package dev.rootiest.icons.core

import kotlin.math.max
import kotlin.math.min

// Icons built here: a port of render-icon.py's built_svg and emblem_svg and the generator's placement, so the SVG
// text is the same as theirs. An emblem is cleaned (Own.kt) and measured (by the app, which can draw) first.

// An emblem ready to place: its cleaned body, the same all in currentColor when it keeps its own colors (what dark
// and light draw), its measured box [x, y, w, h], the manifest's nudges [scale, dx, dy] and its colors.
data class Emblem(
    val body: String,
    val bbox: List<Double>,
    val about: String,
    val colors: VariantColors,
    val mono: String? = null,
    val nudge: List<Double> = listOf(1.0, 0.0, 0.0),
    val keep: Boolean = false,
) {
    val twoTone get() = "ColorScheme-Emblem2" in body
}

// A logo of your own: cleaned, measured and fitted into the Rootiest logo's extent.
data class Logo(val body: String, val transform: String, val about: String, val mono: String? = null, val keep: Boolean = false)

// Where the emblem goes: a preset, the emblem's size (1: as usual) and how far right and down, in logo units.
data class Place(val preset: String = "rootiest", val scale: Double = 1.0, val dx: Double = 0.0, val dy: Double = 0.0)

// One layout's placement: its class, the emblem's transform, its scale and the gap cut around it.
data class Placement(val cls: String, val transform: String, val scale: Double, val gap: Double)

// The generator's placements(): the full layout and each small one.
fun placements(cfg: ComposeConfig, bbox: List<Double>, scale: Double = 1.0, dx: Double = 0.0, dy: Double = 0.0, preset: String = "rootiest"): List<Placement> {
    val (bx, by, bw, bh) = bbox
    val p = cfg.presets.getValue(preset)
    val fit = min(p.w / bw, p.h / bh) * scale
    val (cx0, cy0, side) = cfg.viewBox.split(" ").map(String::toDouble)
    val rows = listOf(listOf(0.0, 1.0, p.cx, p.cy, cfg.knockout)) + p.small
    return rows.mapIndexed { i, (px, k, cx, cy, gap) ->
        val s = if (k == 1.0) fit * k else min(fit * k, max(fit, (side - 2 * cfg.edge) / max(bw, bh)))
        val w = bw * s
        val h = bh * s
        var ex = cx + dx - w / 2
        var ey = cy + dy - h / 2
        if (k != 1.0) {
            ex = min(max(ex, cx0 + cfg.edge), cx0 + side - cfg.edge - w)
            ey = min(max(ey, cy0 + cfg.edge), cy0 + side - cfg.edge - h)
        }
        Placement(if (i == 0) "SizeFull" else "Size${px.toInt()}", "translate(${pyFixed(ex - s * bx, 3)} ${pyFixed(ey - s * by, 3)}) scale(${pyFixed(s, 4)})", s, gap)
    }
}

// The generator's fit_logo(): a logo of your own, by its measured box, fitted into the Rootiest logo's extent.
fun fitLogo(cfg: ComposeConfig, bbox: List<Double>): String {
    val (bx, by, bw, bh) = bbox
    val (ew, eh) = cfg.logoExtent
    val s = min(ew / bw, eh / bh)
    return "translate(${pyFixed((ew - bw * s) / 2 - bx * s, 3)} ${pyFixed((eh - bh * s) / 2 - by * s, 3)}) scale(${pyFixed(s, 4)})"
}

private val STROKE_WIDTH = Regex("stroke-width(?:=\"|\\s*:\\s*)([\\d.]+)")

// The generator's stroke_widths(): the stroke widths a body sets, widest first; [1] for strokes without one.
fun strokeWidths(body: String): List<Double> {
    var widths = STROKE_WIDTH.findAll(body).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
    if (widths.isEmpty() && Regex("stroke(?:=\"|\\s*:\\s*)(?!none)").containsMatchIn(body)) widths = listOf(1.0)
    return widths.sortedDescending()
}

// render-icon.py thicken(): every stroke `scale` times as wide.
fun thicken(body: String, scale: Double): String {
    if (scale == 1.0 || strokeWidths(body).isEmpty()) return body
    val wider = Regex("(stroke-width(?:=\"|\\s*:\\s*))([\\d.]+)").replace(body) {
        it.groupValues[1] + fmtG(it.groupValues[2].toDouble() * scale, 4)
    }
    return "<g stroke-width=\"${fmtG(scale, 4)}\">$wider</g>"
}

// The generator's knockout_mask(): the logo hidden within the emblem's halo.
fun knockoutMask(maskId: String, logoVb: String, emblemBody: String, transform: String, halo: Double): String {
    val body = emblemBody.replace(Regex("\\s(class|style|stroke[a-z-]*|(?:fill-)?opacity)=\"[^\"]*\""), "")
        .replace(Regex("\\sfill=\"(?!none\")[^\"]*\""), "")
    val (x, y, w, h) = logoVb.split(" ")
    return "<mask id=\"$maskId\" maskUnits=\"userSpaceOnUse\" x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\">" +
        "<rect x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\" fill=\"white\"/>" +
        "<g transform=\"$transform\" fill=\"black\" stroke=\"black\" stroke-width=\"${fmtG(halo, 4)}\" " +
        "stroke-linejoin=\"round\" stroke-linecap=\"round\">$body</g></mask>"
}

// The generator's logo_group(): a logo of your own, the mask outside and the fit inside.
private fun logoGroup(body: String, transform: String, mask: String = "") =
    "<g fill=\"currentColor\" class=\"ColorScheme-Accent\"$mask><g transform=\"$transform\">$body</g></g>"

// The generator's custom_logo_svg(): a logo of your own on its own.
fun customLogoSvg(vb: String, body: String, transform: String, color: String) =
    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$vb\">\n  <defs>\n" +
        "    <style id=\"current-color-scheme\" type=\"text/css\">\n      .ColorScheme-Accent{color:$color}\n    </style>\n" +
        "  </defs>\n  ${logoGroup(body, transform)}\n</svg>\n"

// The generator's compose() with masked_layouts(): a whole icon, its five layouts switched by size.
fun compose(cfg: ComposeConfig, logoVb: String, body: String, places: List<Placement>, c: ColorSet, logo: Pair<String, String>? = null): String {
    val widest = strokeWidths(body).firstOrNull() ?: 0.0
    val halos = places.map { 2 * it.gap / it.scale + widest }
    var tone2 = c.emblem2?.let { ".ColorScheme-Emblem2{color:$it}" } ?: ""
    c.onLight?.let { ol ->
        tone2 += "@media (prefers-color-scheme: light){.ColorScheme-Emblem{color:${ol[0]}}" +
            (if (c.emblem2 != null) ".ColorScheme-Emblem2{color:${ol[1]}}" else "") +
            (if (ol.size > 2 && ol[2] != null) ".ColorScheme-Accent{color:${ol[2]}}" else "") + "}"
    }
    val masks = places.mapIndexed { i, p -> "\n    ${knockoutMask("ko-${p.cls}", logoVb, body, p.transform, halos[i])}" }.joinToString("")
    val groups = places.joinToString("") { p ->
        "\n  <g class=\"${p.cls}\">" +
            (if (logo != null) "\n    ${logoGroup(logo.first, logo.second, " mask=\"url(#ko-${p.cls})\"")}"
            else "\n    <path fill=\"currentColor\" d=\"${cfg.logoD}\" class=\"ColorScheme-Accent\" mask=\"url(#ko-${p.cls})\"/>") +
            "\n    <g fill=\"currentColor\" class=\"ColorScheme-Emblem\" transform=\"${p.transform}\">$body</g>" +
            "\n  </g>"
    }
    return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$logoVb\">\n  <defs>\n" +
        "    <style id=\"current-color-scheme\" type=\"text/css\">\n" +
        "      .ColorScheme-Accent{color:${c.logo}}.ColorScheme-Emblem{color:${c.emblem}}$tone2\n    </style>\n" +
        "    <style id=\"sizes\" type=\"text/css\">${cfg.sizeCss}</style>$masks\n  </defs>$groups\n</svg>\n"
}

// render-icon.py built_svg(): emblem e (null: your logo alone) on the Rootiest logo (logo null) or yours, in a
// variant, padded or not, at a place, which adds to the emblem's own nudges.
fun builtSvg(cfg: ComposeConfig, e: Emblem?, logo: Logo?, variant: String, padded: Boolean, place: Place = Place()): String {
    val canvas = if (padded) cfg.viewBoxPadded else cfg.viewBox
    val c = (e?.colors ?: cfg.rootiestColors).getValue(variant)[if (e?.twoTone == true) 1 else 0]
    val mine = logo?.let { (if (variant != "color" && it.mono != null) it.mono else it.body) to it.transform }
    if (e == null) {
        requireNotNull(mine) { "an icon needs an emblem or a logo of your own" }
        return customLogoSvg(canvas, mine.first, mine.second, c.logo)
            .replaceFirst("\n  <defs>", "\n  <!-- ${logo.about.replace("--", "-")}. -->\n  <defs>")
    }
    val body = if (variant != "color" && e.mono != null) e.mono else e.body
    val (s, ndx, ndy) = e.nudge
    val svg = compose(cfg, canvas, body, placements(cfg, e.bbox, s * place.scale, ndx + place.dx, ndy + place.dy, place.preset), c, mine)
    val about = (if (logo != null) "${logo.about} with ${e.about}" else "The Rootiest logo with ${e.about}").replace("--", "-")
    return svg.replaceFirst("\n  <defs>", "\n  <!-- $about. -->\n  <defs>")
}

// render-icon.py emblem_svg(): an emblem on its own, in the logo's red.
fun emblemSvg(cfg: ComposeConfig, body: String, bbox: List<Double>, keep: Boolean, about: String): String {
    val (bx, by, bw, bh) = bbox
    val side = max(bw, bh)
    val view = listOf(bx - (side - bw) / 2, by - (side - bh) / 2, side, side).joinToString(" ") { fmtG(it, 4) }
    return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$view\"${if (keep) " data-keep-fill=\"\"" else ""}>\n" +
        "  <!-- ${about.replace("--", "-")}. -->\n  <defs>\n    <style id=\"current-color-scheme\" type=\"text/css\">\n" +
        "      .ColorScheme-Emblem{color:${cfg.logoRed}}\n    </style>\n  </defs>\n" +
        "  <g fill=\"currentColor\" class=\"ColorScheme-Emblem\">$body</g>\n</svg>\n"
}
