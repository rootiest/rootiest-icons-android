package dev.rootiest.icons

import dev.rootiest.icons.core.ColorOptions
import dev.rootiest.icons.core.ColorSpec
import dev.rootiest.icons.core.Emblem
import dev.rootiest.icons.core.EmblemEntry
import dev.rootiest.icons.core.ExternalName
import dev.rootiest.icons.core.IconEntry
import dev.rootiest.icons.core.Index
import dev.rootiest.icons.core.Logo
import dev.rootiest.icons.core.Parts
import dev.rootiest.icons.core.Place
import dev.rootiest.icons.core.UnusableSvg
import dev.rootiest.icons.core.builtSvg
import dev.rootiest.icons.core.cleanSvg
import dev.rootiest.icons.core.emblemRecolored
import dev.rootiest.icons.core.emblemSvg
import dev.rootiest.icons.core.emblemVariant
import dev.rootiest.icons.core.fitLogo
import dev.rootiest.icons.core.fmtG
import dev.rootiest.icons.core.iconData
import dev.rootiest.icons.core.iconRecolored
import dev.rootiest.icons.core.iconSvg
import dev.rootiest.icons.core.layoutsOf
import dev.rootiest.icons.core.measureBox
import dev.rootiest.icons.core.oklchOf
import dev.rootiest.icons.core.ownSvg
import dev.rootiest.icons.core.placementTag
import dev.rootiest.icons.core.slug
import dev.rootiest.icons.core.thicken
import dev.rootiest.icons.data.OwnFile
import dev.rootiest.icons.data.Repo
import dev.rootiest.icons.data.SetInfo
import dev.rootiest.icons.render.Svg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// What a detail screen is about.
sealed interface Subject

data class IconSubject(val entry: IconEntry) : Subject
data class EmblemSubject(val entry: EmblemEntry) : Subject
data class IconifySubject(val name: ExternalName) : Subject
data object OwnEmblemSubject : Subject
data object OwnLogoSubject : Subject

// A subject, loaded: its emblem cleaned and measured (none for a logo alone), and what to call it.
class Source(
    val subject: Subject,
    val title: String,
    val emblem: Emblem?,
    val stem: String, // the emblem in an output name: tabler-star, my-mark
    val row: String?, // the published icon whose manifest row builds it, for a published emblem
    val cliName: String, // the emblem as render-rootiest-icons names it
    val details: List<Pair<String, String>>,
    // auto: dark and light in one SVG, following the viewer's mode (render-icon.py --variant auto)
    val variants: List<String> = listOf("color", "auto", "dark", "light"),
    val brand: String? = null,
    val standsAlone: Boolean = false, // has an emblem-alone view as well as a whole icon
    val problem: String? = null, // why it cannot go on another logo or be placed elsewhere
)

// Your logo, ready to draw.
class OwnLogo(val logo: Logo, val stem: String, val name: String)

// The choices of a detail screen: what is drawn, and what a render writes.
data class Choices(
    val variant: String = "color",
    val padded: Boolean = false,
    val whole: Boolean = true,
    val mine: Boolean = false, // on your logo
    val preset: String = "rootiest",
    val scale: Double? = null,
    val x: Double = 0.0, // percent of the icon's width, right
    val y: Double = 0.0, // down
    val colors: ColorOptions = ColorOptions(),
    val layouts: List<String> = listOf("auto"),
    val sizes: List<Int> = listOf(256),
    val formats: List<String> = listOf("png"),
    val background: String? = null,
    val animate: String = "none", // render-icon.py --animate: none, draw or heartbeat, for the SVG
    val loop: Boolean = false, // --loop, for draw and morph
    val duration: Double? = null, // --duration in seconds; null: the animation's default
    val morphTo: String? = null, // the morph's target: a published icon's or emblem's name, set:icon, or your file's
    val morphKind: String = "published", // what morphTo names: published, iconify or own
    val morphColor: ColorSpec? = null, // --morph-color: the target's emblem color (OWN_COLOR: its own); null: the icon's
    val morphTone2: ColorSpec? = null, // --morph-tone2-color: its second tone's (hex)
) {
    val placedElsewhere get() = preset != "rootiest" || scale != null || x != 0.0 || y != 0.0
}

// What the choices give: the SVG, and how the files and the command name it.
class Drawn(
    val text: String,
    val isEmblem: Boolean,
    val built: Boolean, // put together here, so it can be placed
    val parts: Parts, // what takes a color
    val stemBase: String,
    val args: List<String>,
    val shown: ColorOptions?, // the colors applied: the chosen ones, or a brand's default
    val brandDefault: Boolean,
) {
    val small get() = layoutsOf(text)
}

// Builds what the screens show, as render-rootiest-icons builds it (rootiest-icons scripts/render-icon.py).
class Studio(private val repo: Repo, val index: Index) {
    private val cfg get() = index.compose
    private val side = cfg.viewBox.split(" ")[2].toDouble()

    private suspend fun measured(viewBox: String, body: String) =
        withContext(Dispatchers.Default) { Svg.measure(measureBox(viewBox), body) }

    // A published icon's emblem with its manifest row: the icon's own, or for an emblem the first service icon made
    // from it (otherwise the Rootiest red), as render-icon.py's published_row() picks.
    private suspend fun publishedEmblem(name: String, isEmblem: Boolean): Pair<Emblem, String>? {
        val row = if (isEmblem) index.icons.firstOrNull { it.build?.emblem == name && !it.build!!.colorOnly } else index.icons.firstOrNull { it.name == name }
        val build = row?.build
        if (!isEmblem && build == null) return null
        val file = build?.emblem ?: name
        val raw = repo.svg("emblems/$file.svg")
        val c = cleanSvg(raw, file)
        val keep = "data-keep-fill" in raw
        return Emblem(
            body = c.body, bbox = measured(c.viewBox, c.body), about = "the $file emblem",
            colors = build?.colors ?: cfg.rootiestColors, nudge = build?.nudge ?: listOf(1.0, 0.0, 0.0), keep = keep,
            mono = if (keep) c.body.replace(Regex("\\sfill=\"(?!none\")[^\"]*\""), "") else null,
        ) to (row?.name ?: "rootiest-$name")
    }

    suspend fun load(subject: Subject, ownEmblem: OwnFile?): Source = when (subject) {
        is IconSubject -> {
            val e = subject.entry
            var problem: String? = null
            val built = try {
                publishedEmblem(e.name, false)
            } catch (err: Exception) {
                problem = err.message
                null
            }
            Source(
                subject, e.title, built?.first, built?.second ?: e.name, built?.second, e.name,
                listOfNotNull("Name" to e.name, e.category?.let { "Category" to it + (e.state?.let { s -> " · $s" } ?: "") },
                    e.emblem?.let { "Emblem" to it }, if (e.layouts.isNotEmpty()) "Layouts" to "full, ${e.layouts.joinToString(", ")} px" else null),
                // auto/ after color: the dark and light versions in one file
                variants = if (e.auto) e.variants.toMutableList().apply { add(indexOf("color") + 1, "auto") } else e.variants,
                problem = problem,
            )
        }
        is EmblemSubject -> {
            val e = subject.entry
            var problem: String? = null
            val built = try {
                publishedEmblem(e.name, true)
            } catch (err: Exception) {
                problem = err.message
                null
            }
            Source(subject, e.title, built?.first, built?.second ?: e.name, built?.second, e.name, listOf("Name" to e.name),
                standsAlone = true, problem = problem)
        }
        is IconifySubject -> {
            val (_, prefix, icon) = subject.name
            val key = subject.name.key
            val info = repo.sets()[prefix] ?: SetInfo()
            val data = repo.iconsData(prefix, listOf(icon))?.let { iconData(it, icon) } ?: throw UnusableSvg("$prefix has no icon $icon")
            val raw = iconSvg(data)
            val c = cleanSvg(raw, key, info.palette)
            val body = thicken(c.body, cfg.strokeScale)
            val credit = info.credit(prefix)
            Source(
                subject, key,
                Emblem(body, measured(c.viewBox, body), "$key from $credit, via Iconify", cfg.rootiestColors, keep = info.palette,
                    mono = if (info.palette) thicken(cleanSvg(raw, key).body, cfg.strokeScale) else null),
                "$prefix-$icon", null, key, listOf("Name" to key, "From" to credit),
                brand = if (prefix == "simple-icons") repo.brands()[icon] else null, standsAlone = true,
            )
        }
        OwnEmblemSubject -> {
            val f = ownEmblem ?: throw UnusableSvg("No emblem of your own is loaded")
            val own = withContext(Dispatchers.Default) { ownSvg(f.raw, "The emblem file ${f.name}", f.keep) }
            Source(
                subject, f.name, Emblem(own.body, measured(own.viewBox, own.body), f.name, cfg.rootiestColors, own.mono, keep = f.keep),
                slug(f.name), null, f.name, listOf("Name" to f.name, "From" to "your file"), standsAlone = true,
            )
        }
        OwnLogoSubject -> Source(subject, "Your logo", null, "", null, "logo", emptyList())
    }

    // Your logo: cleaned, measured and fitted into the Rootiest logo's extent.
    suspend fun logo(f: OwnFile): OwnLogo {
        val own = withContext(Dispatchers.Default) { ownSvg(f.raw, "The logo file ${f.name}", f.keep) }
        return OwnLogo(Logo(own.body, fitLogo(cfg, measured(own.viewBox, own.body)), f.name, own.mono, f.keep), slug(f.name), f.name)
    }

    private fun brandSpec(s: Source) = s.brand?.let { ColorSpec("hex", it, "brand") }

    // Whether a brand's color reads on both backgrounds, so it is the default (render-icon.py's BRAND_L).
    fun brandDefault(s: Source) = s.brand?.let { oklchOf(it).first in cfg.brandL[0]..cfg.brandL[1] } ?: false

    suspend fun draw(s: Source, c: Choices, ownLogo: OwnLogo?, ownEmblem: OwnFile?): Drawn {
        val subject = s.subject
        val whole = subject is IconSubject || subject is OwnLogoSubject || c.whole
        val logo = if (whole && (c.mine || subject is OwnLogoSubject)) ownLogo else null
        val e = s.emblem
        val text: String
        val parts: Parts
        val stemBase: String
        val args = ArrayList<String>()
        var built = false
        var raw: String? = null // an emblem alone: its own file, which the auto variant recolors in two versions
        if (!whole) {
            val keep = if (subject is EmblemSubject) subject.entry.keepFill else e!!.keep
            val twoTone = if (subject is EmblemSubject) subject.entry.twoTone else e!!.twoTone
            val base = if (subject is EmblemSubject) repo.svg("emblems/${subject.entry.name}.svg") else emblemSvg(cfg, e!!.body, e.bbox, e.keep, e.about)
            raw = base
            text = if (subject is EmblemSubject && keep) base else emblemVariant(base, c.variant)
            parts = Parts(emblem = !keep, tone2 = twoTone && !keep)
            stemBase = if (subject is EmblemSubject) subject.entry.name else s.stem
            args += s.cliName
            if (subject is EmblemSubject) args += "--emblem"
        } else if (subject is IconSubject && (e == null || (logo == null && !c.placedElsewhere))) {
            text = repo.svg("${c.variant}/${if (c.padded) "padded/" else ""}${subject.entry.name}.svg")
            parts = subject.entry.parts
            stemBase = subject.entry.name
            args += subject.entry.name
        } else {
            built = true
            if (e == null && logo == null) throw UnusableSvg("Load a logo of your own first")
            val place = Place(c.preset, c.scale ?: 1.0, c.x / 100 * side, c.y / 100 * side)
            text = builtSvg(cfg, e, logo?.logo, c.variant, c.padded, place)
            parts = Parts(logo = logo?.logo?.keep != true, emblem = e != null && !e.keep, tone2 = e != null && e.twoTone && !e.keep)
            val tag = if (e != null) placementTag(c.preset, c.scale, c.x, c.y) else ""
            stemBase = when {
                e == null -> logo!!.stem
                s.row != null -> (if (logo != null) "${logo.stem}-${s.row.removePrefix("rootiest-")}" else s.row) + tag
                else -> "${logo?.stem ?: "rootiest"}-${s.stem}$tag"
            }
            args += when (subject) {
                is IconSubject -> subject.entry.name
                is EmblemSubject -> if (logo != null) subject.entry.name else s.row ?: subject.entry.name
                OwnLogoSubject -> "logo"
                else -> (if (logo == null) "rootiest:" else "") + s.cliName
            }
            if (logo != null) args += listOf("--logo", logo.name)
            if (e != null) {
                if (c.preset != "rootiest") args += listOf("--placement", c.preset)
                c.scale?.let { args += listOf("--emblem-scale", fmtG(it)) }
                if (c.x != 0.0) args += listOf("--emblem-x", fmtG(c.x))
                if (c.y != 0.0) args += listOf("--emblem-y", fmtG(c.y))
            }
            if (logo?.logo?.keep == true) args += "--keep-logo-colors"
            if (subject is OwnEmblemSubject && ownEmblem?.keep == true) args += "--keep-emblem-colors"
        }
        // the custom colors recolor the color and auto variants (render-icon.py check_colors), a pair only auto; a
        // brand's color is the default in the color variant alone
        val brandDefault = c.variant == "color" && brandDefault(s)
        val chosen = if (c.variant == "auto") c.colors else c.colors.withoutPairs()
        val shown = when {
            c.variant != "color" && c.variant != "auto" -> null
            chosen.any -> chosen
            brandDefault -> ColorOptions(color = brandSpec(s))
            else -> null
        }
        val colored = when {
            shown == null -> text
            whole -> iconRecolored(index.colors, text, shown, c.variant)
            else -> emblemRecolored(index.colors, raw!!, shown, c.variant)
        }
        return Drawn(colored, !whole, built, parts, stemBase, args, shown, brandDefault)
    }
}

// The whole command for a render: the name and placement (Drawn.args), then the choices.
// morphName: the morph's target as --morph-to names it (Moving's), when there is one.
// morphColor, morphTone2: its colors as they apply (Animation's).
fun commandLine(d: Drawn, c: Choices, morphName: String? = null, morphColor: ColorSpec? = null, morphTone2: ColorSpec? = null): String {
    val args = ArrayList(d.args)
    if (c.variant != "color") args += listOf("--variant", c.variant)
    if (c.padded && !d.isEmblem) args += "--padded"
    // the color options, for the color and auto variants (a pair, dark/light, for auto alone)
    val given = when (c.variant) {
        "auto" -> c.colors
        "color" -> c.colors.withoutPairs()
        else -> ColorOptions()
    }
    for ((flag, spec) in listOf("--color" to given.color, "--logo-color" to given.logo, "--emblem-color" to given.emblem, "--tone2-color" to given.tone2)) {
        if (spec != null) args += listOf(flag, spec.label.removePrefix("#"))
    }
    if (c.layouts != listOf("auto")) args += listOf("--layout", c.layouts.joinToString(","))
    // auto makes an SVG only, which --variant auto implies: no sizes, formats or background
    val svgOnly = c.variant == "auto"
    if (!svgOnly) args += listOf("--size", c.sizes.joinToString(","))
    if (!svgOnly && c.formats != listOf("png")) args += listOf("--format", c.formats.joinToString(","))
    if (c.animate != "none") {
        args += listOf("--animate", c.animate)
        if (c.animate == "morph") morphName?.let { args += listOf("--morph-to", it) }
        if (c.animate == "morph" && morphName != null) {
            morphColor?.let { args += listOf("--morph-color", it.label.removePrefix("#")) }
            morphTone2?.let { args += listOf("--morph-tone2-color", it.label.removePrefix("#")) }
        }
        if (c.loop && (c.animate == "draw" || c.animate == "morph")) args += "--loop"
        c.duration?.let { args += listOf("--duration", fmtG(it)) }
    }
    if (!svgOnly) c.background?.let { args += listOf("--background", it) }
    return (listOf("render-rootiest-icons") + args).joinToString(" ") { if (Regex("^[\\w@%+=:,./-]+$").matches(it)) it else "'" + it.replace("'", "'\"'\"'") + "'" }
}
