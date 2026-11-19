package dev.rootiest.icons.core

import kotlinx.serialization.Serializable
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Colors: a port of render-icon.py's recolor, icon_recolored, emblem_recolored, shades and oklch_of, and the
// generator's oklch(), so a recolored SVG here is what `render-rootiest-icons <icon> --color ...` writes.

// A color option's value: a category or status state (kind "cat", value the category, label as typed), or a hex color
// (kind "hex", value "#rrggbb", label the hex, or "brand" for a Simple Icons brand color).
data class ColorSpec(val kind: String, val value: String, val label: String = value) {
    val isCategory get() = kind == "cat"
}

// The constants a hex color's shades take, from index.json.
@Serializable
data class HexShades(val emblemLift: Double, val emblemC: Double, val tone2Dark: List<Double>, val tone2Light: List<Double>)

// index.json's colors: each category's shades ({Accent, Emblem, Emblem2}: [dark, light]) and the states' categories.
@Serializable
data class Colors(
    val categories: Map<String, Map<String, List<String>>>,
    val states: Map<String, String>,
    val hex: HexShades,
)

// The color options of a render: one color for the whole icon, and each part's over it.
data class ColorOptions(
    val color: ColorSpec? = null,
    val logo: ColorSpec? = null,
    val emblem: ColorSpec? = null,
    val tone2: ColorSpec? = null,
) {
    val any get() = color != null || logo != null || emblem != null || tone2 != null
}

private val HEX_RE = Regex("#[0-9a-fA-F]{3,8}\\b")
private val PROP_RE = Regex("((?:fill|stroke|stop-color|flood-color|color)\\s*(?:=\\s*\"|:\\s*))#[0-9a-fA-F]{3,8}\\b")

// The generator's oklch(): #rrggbb for an OKLCH color, lowering the chroma until it fits sRGB.
fun oklch(L: Double, chroma: Double, H: Double): String {
    fun gamma(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055
    var C = chroma
    while (true) {
        val a = C * cos(Math.toRadians(H))
        val b = C * sin(Math.toRadians(H))
        val l = (L + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (L - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (L - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val rgb = listOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
        if (rgb.all { it in -0.001..1.001 } || C <= 0) {
            return "#" + rgb.joinToString("") { pyRound(gamma(min(max(it, 0.0), 1.0)) * 255).toString(16).padStart(2, '0') }
        }
        C -= 0.002
    }
}

// render-icon.py oklch_of(): (L, C, H) of a #rrggbb color.
fun oklchOf(hex: String): Triple<Double, Double, Double> {
    fun linear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    val (r, g, b) = listOf(1, 3, 5).map { linear(hex.substring(it, it + 2).toInt(16) / 255.0) }
    val l = (0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b).pow(1.0 / 3)
    val m = (0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b).pow(1.0 / 3)
    val s = (0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b).pow(1.0 / 3)
    val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    val A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return Triple(L, hypot(A, B), pyMod(Math.toDegrees(atan2(B, A)), 360.0))
}

// render-icon.py color_spec(): a category, a status state or a hex color (the # optional); null when it is none.
fun colorSpec(colors: Colors, text: String, hexOnly: Boolean = false): ColorSpec? {
    val t = text.trim().lowercase()
    if (!hexOnly && t in colors.categories) return ColorSpec("cat", t, t)
    if (!hexOnly) colors.states[t]?.let { return ColorSpec("cat", it, t) }
    val m = Regex("^#?([0-9a-f]{3}|[0-9a-f]{6})$").find(t) ?: return null
    val h = m.groupValues[1].let { if (it.length == 6) it else it.map { c -> "$c$c" }.joinToString("") }
    return ColorSpec("hex", "#$h")
}

// render-icon.py shades(): {Accent, Emblem, Emblem2}: [dark, light] from one color.
fun shades(colors: Colors, spec: ColorSpec): Map<String, List<String>> {
    if (spec.isCategory) return colors.categories.getValue(spec.value)
    val (L, C, H) = oklchOf(spec.value)
    val x = colors.hex
    return mapOf(
        "Accent" to listOf(spec.value, spec.value),
        "Emblem" to listOf(
            oklch(min(L + x.emblemLift, 0.95), min(x.emblemC, C), H),
            oklch(max(L - x.emblemLift - 0.02, 0.2), min(x.emblemC, C), H),
        ),
        "Emblem2" to listOf(oklch(x.tone2Dark[0], min(x.tone2Dark[1], C), H), oklch(x.tone2Light[0], min(x.tone2Light[1], C), H)),
    )
}

// render-icon.py recolor(): every hex color becomes main, except on tone-2 parts, which take tone2 (null: unchanged).
fun recolor(svg: String, main: String?, tone2: String?): String {
    val styled = Regex("(<style[^>]*>)([\\s\\S]*?)(</style>)").replace(svg) { m ->
        m.groupValues[1] + Regex("([^{}]+)\\{([^{}]*)\\}").replace(m.groupValues[2]) { r ->
            val color = if ("Emblem2" in r.groupValues[1]) tone2 else main
            if (color == null) r.value else r.groupValues[1] + "{" + HEX_RE.replace(r.groupValues[2]) { color } + "}"
        } + m.groupValues[3]
    }
    return Regex("<[a-zA-Z][^>]*>").replace(styled) { m ->
        val color = if ("data-tone=\"2\"" in m.value || "Emblem2" in m.value) tone2 else main
        if (color == null) m.value else PROP_RE.replace(m.value) { it.groupValues[1] + color }
    }
}

// render-icon.py icon_parts(): {class: [dark, light]} from the options.
fun iconParts(colors: Colors, o: ColorOptions): Map<String, List<String>> {
    val parts = LinkedHashMap<String, List<String>>()
    o.color?.let { parts.putAll(shades(colors, it)) }
    o.logo?.let { parts["Accent"] = shades(colors, it).getValue("Accent") }
    o.emblem?.let {
        val s = shades(colors, it)
        parts["Emblem"] = if (it.isCategory) s.getValue("Emblem") else listOf(it.value, it.value)
        parts["Emblem2"] = s.getValue("Emblem2")
    }
    o.tone2?.let { parts["Emblem2"] = listOf(it.value, it.value) }
    return parts
}

// render-icon.py icon_recolored(): the current-color-scheme block rewritten for these colors.
fun iconRecolored(colors: Colors, text: String, o: ColorOptions): String {
    val m = Regex("(<style id=\"current-color-scheme\"[^>]*>)([\\s\\S]*?)(</style>)").find(text) ?: return text
    val css = m.groupValues[2]
    val media = Regex("@media \\(prefers-color-scheme: light\\)\\{([\\s\\S]*)\\}").find(css)
    val baseCss = if (media != null) css.substring(0, media.range.first) else css
    fun rules(s: String) = LinkedHashMap<String, String>().apply {
        Regex("\\.ColorScheme-(\\w+)\\{color:(#[0-9a-fA-F]+)\\}").findAll(s).forEach { put(it.groupValues[1], it.groupValues[2]) }
    }
    val base = rules(baseCss)
    val light = if (media != null) rules(media.groupValues[1]) else LinkedHashMap()
    for ((cls, pair) in iconParts(colors, o)) {
        if (cls !in base) continue
        base[cls] = pair[0]
        if (pair[1] != pair[0]) light[cls] = pair[1] else light.remove(cls)
    }
    val order = listOf("Accent", "Emblem", "Emblem2").filter { it in base }
    var out = order.joinToString("") { ".ColorScheme-$it{color:${base[it]}}" }
    if (light.isNotEmpty()) {
        out += "@media (prefers-color-scheme: light){" +
            order.filter { it in light }.joinToString("") { ".ColorScheme-$it{color:${light[it]}}" } + "}"
    }
    val start = m.groups[2]!!.range.first
    val indent = css.takeWhile { it.isWhitespace() }
    val tail = css.takeLastWhile { it.isWhitespace() }
    return text.substring(0, start) + indent + out + tail + text.substring(start + css.length)
}

// render-icon.py emblem_recolored(): the emblem in one color, its tone-2 parts in another (or as they are).
fun emblemRecolored(colors: Colors, text: String, o: ColorOptions): String {
    val spec = o.emblem ?: o.color
    val main = spec?.let { if (it.isCategory) shades(colors, it).getValue("Accent")[0] else it.value }
    return recolor(text, main, o.tone2?.value)
}

// The emblems' dark and light variants (render-icon.py MONO): white or black, tone 2 the other.
val MONO = mapOf("dark" to ("#ffffff" to "#000000"), "light" to ("#000000" to "#ffffff"))

// render-icon.py source_text() for an emblem alone: dark or light, white or black (MONO); auto, both in one file,
// following the viewer's mode (autoSvg). The color variant, and any other, is the text as it is.
fun emblemVariant(text: String, variant: String): String = when (variant) {
    "auto" -> autoSvg(emblemVariant(text, "dark"), emblemVariant(text, "light"))
    in MONO -> MONO.getValue(variant).let { recolor(text, it.first, it.second) }
    else -> text
}

private const val LIGHT_MEDIA = "@media (prefers-color-scheme: light)"
private const val COLOR_PROPS = "fill|stroke|stop-color|flood-color|lighting-color|color"
private val TAG_COLOR = Regex("\\s($COLOR_PROPS)=\"(#[0-9a-fA-F]{3,8}\\b)\"")
private val STYLE_COLOR = Regex("(?:^|;)\\s*($COLOR_PROPS)\\s*:\\s*(#[0-9a-fA-F]{3,8}\\b)")
private val CSS_RULE = Regex("([^{}]+)\\{([^{}]*)\\}")
private val ANY_TAG = Regex("<[^>]*>")
private val DEFS_OPEN = Regex("<defs\\b[^>]*>")

// render-icon.py tag_colors(): {property: hex color} a tag sets, by attribute or in its style attribute (which wins).
private fun tagColors(tag: String): Map<String, String> {
    val found = LinkedHashMap<String, String>()
    for (m in TAG_COLOR.findAll(tag)) found[m.groupValues[1]] = m.groupValues[2]
    for (m in STYLE_COLOR.findAll(getAttr(tag, "style") ?: "")) found[m.groupValues[1]] = m.groupValues[2]
    return found
}

// render-icon.py with_class(): a start tag with cls added to its classes.
private fun withClass(tag: String, cls: String): String {
    if (getAttr(tag, "class") != null) {
        val m = Regex("(\\sclass=)([\"'])(.*?)\\2").find(tag) ?: return tag
        return tag.replaceRange(m.range, "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]} $cls${m.groupValues[2]}")
    }
    val m = Regex("^<[\\w:-]+").find(tag) ?: return tag
    return tag.replaceRange(m.range, "${m.value} class=\"$cls\"")
}

// render-icon.py without_colors(): a start tag without these color properties, as attributes or in its style
// attribute (an emptied one goes).
private fun withoutColors(tag0: String, props: List<String>): String {
    var tag = tag0
    for (k in props) tag = Regex("\\s$k=\"#[0-9a-fA-F]{3,8}\\b\"").replace(tag, "")
    val style = getAttr(tag, "style") ?: return tag
    val drop = Regex("\\s*(${props.joinToString("|")})\\s*:\\s*#[0-9a-fA-F]{3,8}\\b\\s*")
    val kept = style.split(";").filter { it.isNotBlank() && !drop.matches(it) }.joinToString(";")
    val m = Regex("\\sstyle=([\"']).*?\\1").find(tag) ?: return tag
    return tag.replaceRange(m.range, if (kept.isNotEmpty()) " style=\"$kept\"" else "")
}

// Python's re.split(r"(<[^>]*>)", text): the text between tags and the tags, alternately, text first and last.
private fun splitTags(text: String): MutableList<String> {
    val out = ArrayList<String>()
    var at = 0
    for (m in ANY_TAG.findAll(text)) {
        out += text.substring(at, m.range.first)
        out += m.value
        at = m.range.last + 1
    }
    out += text.substring(at)
    return out
}

// render-icon.py auto_svg(): one SVG that is `dark` in dark mode and `light` in light mode, two versions of one drawing
// that differ only in colors (an emblem's dark and light variants): dark's text, with a prefers-color-scheme: light
// block holding light's colors where they differ, as the published auto/ files have them. A stylesheet rule's color
// goes there under the rule's selector; an element's own (an attribute, or its style attribute, as in an emblem drawn
// in hex colors) moves to the stylesheet: the element gets a class, auto-N, ruled with its dark colors and, in the
// light block, its light ones, so no rule has to win over a style attribute (AndroidSVG lets none).
fun autoSvg(dark: String, light: String): String {
    fun differ(): Nothing = throw UnusableSvg("the dark and light versions differ in more than their colors")
    val ds = splitTags(dark)
    val ls = splitTags(light)
    if (ds.size != ls.size) differ()
    val out = ArrayList(ds)
    val lightRules = LinkedHashMap<Int, MutableList<String>>()
    val classes = LinkedHashMap<Pair<String, String>, String>()
    var scheme: Int? = null
    for (i in ds.indices) {
        val d = ds[i]
        val l = ls[i]
        val inStyle = i > 0 && ds[i - 1].startsWith("<style")
        if (inStyle && "id=\"current-color-scheme\"" in ds[i - 1]) scheme = i
        if (d == l) continue
        if (HEX_RE.replace(d, "#") != HEX_RE.replace(l, "#") || (inStyle && "prefers-color-scheme" in d)) differ()
        if (d.startsWith("<")) {
            val dc = tagColors(d)
            val lc = tagColors(l)
            val diff = lc.filter { (k, v) -> dc[k] != v }.keys.toList()
            if (diff.any { it !in dc }) differ()
            val key = diff.joinToString(";") { "$it:${dc.getValue(it)}" } to diff.joinToString(";") { "$it:${lc.getValue(it)}" }
            out[i] = withClass(withoutColors(d, diff), classes.getOrPut(key) { "auto-${classes.size + 1}" })
        } else if (inStyle) {
            lightRules[i] = CSS_RULE.findAll(d).zip(CSS_RULE.findAll(l))
                .filter { (a, b) -> a.groupValues[2] != b.groupValues[2] }
                .map { (_, b) -> "${b.groupValues[1].trim()}{${b.groupValues[2]}}" }.toMutableList()
        } else {
            differ()
        }
    }
    val base = classes.entries.joinToString("") { (k, cls) -> ".$cls{${k.first}}" }
    val tagged = classes.map { (k, cls) -> ".$cls{${k.second}}" }
    if (tagged.isNotEmpty() && scheme == null) {
        // no stylesheet for the colors yet: one, in the defs (made if there are none)
        val style = "<style id=\"current-color-scheme\" type=\"text/css\">$base$LIGHT_MEDIA{${tagged.joinToString("")}}</style>"
        val at = out.indexOfFirst { DEFS_OPEN.matches(it) && !it.endsWith("/>") }
        if (at < 0) {
            val svg = out.indexOfFirst { it.startsWith("<svg") }
            if (svg < 0) differ()
            out[svg] += "<defs>$style</defs>"
        } else {
            out[at] += style
        }
    } else if (tagged.isNotEmpty()) {
        lightRules.getOrPut(scheme!!) { ArrayList() }.addAll(tagged)
    }
    for ((i, rules) in lightRules) {
        if (rules.isEmpty()) continue
        val core = out[i].trimEnd()
        out[i] = "$core${if (i == scheme) base else ""}$LIGHT_MEDIA{${rules.joinToString("")}}${out[i].substring(core.length)}"
    }
    return out.joinToString("")
}
