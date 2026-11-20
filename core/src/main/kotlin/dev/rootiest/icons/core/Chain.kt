package dev.rootiest.icons.core

import kotlin.math.abs

// Chains (render-icon.py chain_svg and its helpers): animations one after another in one SVG, an animated badge, then
// a morph, then a shine. Each step is an animated SVG of its own, baked onto the chain's clock: its CSS animations
// become keyframes sampled FPS times a second (in dark and light mode), its SMIL path data keeps its keys and timing
// curves, moved onto that clock (sampled only where its slot cuts between two keys). Each step keeps its own document
// inside the chain, its selectors scoped by .chain-<n> and its ids suffixed -c<n>; undoing those gives the step back,
// whose frames frameSvg draws. A step shows only in its slot. Every animated SVG names the still it ends on in a
// <metadata id="rootiest-end">: render-rootiest-icons' arguments for it. The text is the command line's, byte for byte.

private val END_RECIPE = Regex("\\s*<metadata id=\"rootiest-end\">([\\s\\S]*?)</metadata>")
private val CHAIN_STEP = Regex(
    "<svg class=\"chain-(\\d+)\" data-chain-start=\"([^\"]*)\" data-chain-length=\"([^\"]*)\" viewBox=\"([^\"]*)\"[^>]*>([\\s\\S]*?)</svg><!--/chain-\\1-->",
)
private val SVG_TAG = Regex("<svg\\b[^>]*>")
private val VIEWBOX = Regex("<svg\\b[^>]*\\sviewBox=\"([^\"]*)\"")
private val ANIMATION_CSS = Regex("(<style id=\"animation\"[^>]*>)([\\s\\S]*?)(</style>)")
private val ANY_STYLE = Regex("(<style\\b[^>]*>)([\\s\\S]*?)(</style>)")
private val SMIL_D = Regex("<animate attributeName=\"d\"([^>]*)/>")
private val ID_ATTR = Regex("\\sid=\"([^\"]+)\"")
private val SHELL_SAFE = Regex("^[\\w@%+=:,./-]+$")

// The arguments for the still an animated SVG ends on, or null.
fun endRecipe(svgText: String): String? =
    END_RECIPE.find(svgText)?.groupValues?.get(1)?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&amp;", "&")

// The SVG naming the still it ends on, right after its <svg> tag (replacing one it named).
fun withEndRecipe(svgText: String, recipe: String?): String {
    val text = END_RECIPE.replace(svgText, "")
    val head = SVG_TAG.find(text)
    if (recipe.isNullOrEmpty() || head == null) return text
    val esc = recipe.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return text.substring(0, head.range.last + 1) + "\n  <metadata id=\"rootiest-end\">$esc</metadata>" + text.substring(head.range.last + 1)
}

fun isChain(svgText: String) = "<svg class=\"chain-1\" data-chain-start=" in svgText

private fun svgBody(svgText: String): String {
    val head = SVG_TAG.find(svgText) ?: return ""
    val end = svgText.lastIndexOf("</svg>")
    return if (end > head.range.last + 1) END_RECIPE.replace(svgText.substring(head.range.last + 1, end), "") else ""
}

// Every rule under scope, in @media blocks too; @keyframes kept as they are.
fun scopedCss(css: String, scope: String): String {
    val out = StringBuilder()
    var i = 0
    while (i < css.length) {
        val j = css.indexOf('{', i)
        if (j < 0) {
            out.append(css.substring(i))
            break
        }
        val head = css.substring(i, j)
        var depth = 1
        var k = j + 1
        while (k < css.length && depth > 0) {
            if (css[k] == '{') depth++ else if (css[k] == '}') depth--
            k++
        }
        val inner = css.substring(j + 1, k - 1)
        val lead = head.substring(0, head.length - head.trimStart().length)
        val h = head.trim()
        when {
            h.startsWith("@media") -> out.append("$lead$h{${scopedCss(inner, scope)}}")
            h.startsWith("@") -> out.append("$lead$h{$inner}")
            else -> out.append(lead + h.split(",").joinToString(",") { "$scope ${it.trim()}" } + "{$inner}")
        }
        i = k
    }
    return out.toString()
}

// A step's ids, and every reference to them, with a suffix; undo takes it off.
private fun chainIds(body: String, suffix: String, undo: Boolean = false): String {
    if (undo) return Regex("(\\sid=\"|url\\(#|href=\"#)([^\"\\)]*?)" + Regex.escape(suffix) + "([\"\\)])").replace(body, "$1$2$3")
    var text = body
    for (x in ID_ATTR.findAll(body).map { it.groupValues[1] }.distinct().sortedByDescending { it.length }) {
        text = Regex("(\\sid=\"|url\\(#|href=\"#)" + Regex.escape(x) + "([\"\\)])").replace(text) { m -> m.groupValues[1] + x + suffix + m.groupValues[2] }
    }
    return text
}

// A step: its animated SVG, the moment of its own time it starts from, and how long it plays.
data class ChainStep(val doc: String, val offset: Double, val length: Double)

// A chain's steps on its clock; any other animated SVG is one step from 0 for its own play time.
fun chainSteps(svgText: String): List<ChainStep> {
    if (!isChain(svgText)) return listOf(ChainStep(svgText, 0.0, playTime(svgText).seconds))
    return CHAIN_STEP.findAll(svgText).map { m ->
        val (n, start, length, vb, body) = m.destructured
        ChainStep("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$vb\">${chainIds(body.replace(".chain-$n ", ""), "-c$n", undo = true)}</svg>\n",
            start.toDouble(), length.toDouble())
    }.toList()
}

// A step of a chain at t seconds: the document to draw a frame of (frameSvg) at that moment.
fun chainStepAt(svgText: String, t: Double): String {
    if (!isChain(svgText)) return svgText
    val steps = chainSteps(svgText)
    return (steps.firstOrNull { t < it.offset + it.length } ?: steps.last()).doc
}

// A frame of any animated SVG, of a chain its step's at that moment.
fun chainFrame(svgText: String, t: Double, light: Boolean = false): String = frameSvg(chainStepAt(svgText, t), t, light)

private fun samples(length: Double) = List(pyRound(length * FPS).toInt()) { it.toDouble() / FPS } + length

// A step's animation stylesheet on the chain's clock: its plain rules scoped, and each animated selector's values
// sampled over its slot (from start, for length seconds, its own time from offset), held before and after, in dark
// mode and, where they differ, light.
private fun bakedCss(css: String, offset: Double, length: Double, start: Double, total: Double, loop: Boolean, scope: String, name: String): String {
    val rules = stylesheet(css).first
    val us = samples(length)
    val dark = us.map { animatedValues(css, offset + it, light = false, precise = true).first }
    val light = us.map { animatedValues(css, offset + it, light = true, precise = true).first }
    val animated = rules.filter { "animation" in it.decls }.flatMap { it.selectors }.distinct()
    val out = StringBuilder()
    for (r in rules) {
        val plain = r.decls.filterKeys { !it.startsWith("animation") }
        if (plain.isNotEmpty()) out.append(r.selectors.joinToString(",") { "$scope $it" } + "{" + plain.entries.joinToString(";") { "${it.key}:${it.value}" } + "}")
    }
    fun pct(u: Double) = fmtG(100 * (start + u) / total)
    val count = if (loop) "infinite" else "1"
    fun frames(kname: String, values: List<Map<String, Map<String, String>>>, sel: String): String {
        val props = values.flatMap { it[sel]?.keys ?: emptySet() }.toSortedSet()
        fun at(v: Map<String, Map<String, String>>) = "{" + props.filter { v[sel]?.containsKey(it) == true }.joinToString(";") { "$it:${v[sel]!![it]}" } + "}"
        val keys = (if (start > 0) listOf("0%" + at(values[0])) else emptyList()) + us.indices.map { "${pct(us[it])}%" + at(values[it]) } +
            (if (start + length < total) listOf("100%" + at(values.last())) else emptyList())
        return "@keyframes $kname{" + keys.joinToString("") + "}"
    }
    val media = ArrayList<String>()
    animated.forEachIndexed { i, sel ->
        val kname = "$name-$i"
        out.append("$scope $sel{animation:$kname ${fmtG(total)}s linear $count both}" + frames(kname, dark, sel))
        if (dark.indices.any { dark[it][sel] != light[it][sel] }) {
            out.append(frames("$kname-light", light, sel))
            media += "$scope $sel{animation-name:$kname-light}"
        }
    }
    if (media.isNotEmpty()) out.append("@media (prefers-color-scheme: light){" + media.joinToString("") + "}")
    if (animated.isNotEmpty()) out.append("@media (prefers-reduced-motion: reduce){" + animated.joinToString(",") { "$scope $it" } + "{animation:none}}")
    return out.toString()
}

// One SMIL <animate>'s keys moved onto the chain's clock as they are: [share of the chain, value, curve to the next],
// or null when the slot starts or ends between two keys (then it is sampled).
private fun retimedSmil(attrs: String, offset: Double, length: Double, start: Double, total: Double): List<Triple<Double, String, String>>? {
    val values = (getAttr(attrs, "values") ?: "").split(";")
    val dur = (getAttr(attrs, "dur") ?: "1s").removeSuffix("s").toDouble()
    val keys = getAttr(attrs, "keyTimes")?.split(";")?.map { it.toDouble() } ?: values.indices.map { it.toDouble() / (values.size - 1) }
    val spline = getAttr(attrs, "calcMode") == "spline"
    val splines = (getAttr(attrs, "keySplines") ?: "").split(";").map { it.trim() }.filter { it.isNotEmpty() }
    val count = iterations(getAttr(attrs, "repeatCount"))
    val end = if (count != null) dur * count else Double.POSITIVE_INFINITY
    // key times are written to 6 digits (a chain's, continued), so a key that close to the slot's edge is on it
    val tol = 1e-6 * dur + 1e-9
    val pts = ArrayList<Triple<Double, String, String>>()
    var i = 0
    while (i * dur <= offset + length + tol && i * dur < end) {
        keys.forEachIndexed { j, k ->
            var t = i * dur + k * dur
            if (offset - tol <= t && t <= offset + length + tol && t <= end + tol) {
                t = if (abs(t - offset) <= tol) offset else if (abs(t - offset - length) <= tol) offset + length else t
                pts += Triple(t, values[j], if (spline && j < splines.size) splines[j] else "0 0 1 1")
            }
        }
        i++
    }
    if (pts.isEmpty() || pts[0].first != offset || (pts.last().first != offset + length && offset + length < end - tol)) return null
    if (pts.last().first < offset + length) pts += Triple(offset + length, pts.last().second, "0 0 1 1")
    val out = pts.map { Triple((start + it.first - offset) / total, it.second, it.third) }.toMutableList()
    if (start > 0) out.add(0, Triple(0.0, out[0].second, "0 0 1 1"))
    if (start + length < total) {
        out[out.size - 1] = Triple(out.last().first, out.last().second, "0 0 1 1")
        out += Triple(1.0, out.last().second, "0 0 1 1")
    }
    return out
}

// A step's SMIL path data on the chain's clock.
private fun bakedSmil(body: String, offset: Double, length: Double, start: Double, total: Double, loop: Boolean): String {
    val us = samples(length)
    val rep = if (loop) "indefinite" else "1"
    return SMIL_D.replace(body) { m ->
        val attrs = m.groupValues[1]
        val pts = retimedSmil(attrs, offset, length, start, total)
        if (pts != null) {
            "<animate attributeName=\"d\" dur=\"${fmtG(total)}s\" repeatCount=\"$rep\" fill=\"freeze\" calcMode=\"spline\" " +
                "keyTimes=\"${pts.joinToString(";") { fmtG(it.first) }}\" keySplines=\"${pts.dropLast(1).joinToString(";") { it.third }}\" " +
                "values=\"${pts.joinToString(";") { it.second }}\"/>"
        } else {
            val keys = (if (start > 0) listOf(0.0) else emptyList()) + us.map { (start + it) / total } + (if (start + length < total) listOf(1.0) else emptyList())
            var vals = us.map { smilD(attrs, offset + it, precise = true) }
            vals = (if (start > 0) listOf(vals[0]) else emptyList()) + vals + (if (start + length < total) listOf(vals.last()) else emptyList())
            "<animate attributeName=\"d\" dur=\"${fmtG(total)}s\" repeatCount=\"$rep\" fill=\"freeze\" calcMode=\"linear\" " +
                "keyTimes=\"${keys.joinToString(";") { fmtG(it) }}\" values=\"${vals.joinToString(";")}\"/>"
        }
    }
}

// Steps one after another in one SVG, played once (holding the last step's end) or looping whole.
fun chainSvg(steps: List<ChainStep>, loop: Boolean, recipe: String? = null): String {
    val total = steps.sumOf { it.length }
    val vb = VIEWBOX.find(steps[0].doc)?.groupValues?.get(1) ?: "0 0 24 24"
    val (x, y, w, h) = (vb.replace(",", " ").split(Regex("\\s+")).filter { it.isNotEmpty() } + List(4) { "0" }).take(4)
    val count = if (loop) "infinite" else "1"
    val show = StringBuilder()
    val parts = StringBuilder()
    var start = 0.0
    steps.forEachIndexed { idx, (doc, offset, length) ->
        val n = idx + 1
        val scope = ".chain-$n"
        val end = start + length
        val last = n == steps.size
        val on = (if (start > 0) listOf("0%{opacity:0}", "${fmtG(100 * start / total)}%{opacity:1}") else listOf("0%{opacity:1}")) +
            (if (last) listOf("100%{opacity:1}") else listOf("${fmtG(100 * end / total)}%{opacity:0}", "100%{opacity:0}"))
        show.append("$scope{animation:chain-show-$n ${fmtG(total)}s step-end $count both}@keyframes chain-show-$n{${on.joinToString("")}}")
        var body = svgBody(doc)
        ANIMATION_CSS.find(body)?.let { m ->
            body = body.substring(0, m.range.first) + m.groupValues[1] + bakedCss(m.groupValues[2], offset, length, start, total, loop, scope, "chain-$n") +
                m.groupValues[3] + body.substring(m.range.last + 1)
        }
        body = ANY_STYLE.replace(body) { m ->
            m.groupValues[1] + (if ("id=\"animation\"" in m.groupValues[1]) m.groupValues[2] else scopedCss(m.groupValues[2], scope)) + m.groupValues[3]
        }
        body = chainIds(bakedSmil(body, offset, length, start, total, loop), "-c$n")
        val sub = VIEWBOX.find(doc)?.groupValues?.get(1) ?: vb
        parts.append("\n  <svg class=\"chain-$n\" data-chain-start=\"${fmtG(start)}\" data-chain-length=\"${fmtG(length)}\" " +
            "viewBox=\"$sub\" x=\"$x\" y=\"$y\" width=\"$w\" height=\"$h\">$body</svg><!--/chain-$n-->")
        start = end
    }
    val hidden = (2..steps.size).joinToString(",") { ".chain-$it" }
    val css = (if (hidden.isNotEmpty()) "$hidden{opacity:0}" else "") + show + "@media (prefers-reduced-motion: reduce){" +
        (1..steps.size).joinToString(",") { ".chain-$it" } + "{animation:none}}"
    return withEndRecipe("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$vb\">\n  <style id=\"animation\" type=\"text/css\">$css</style>$parts\n</svg>\n", recipe)
}

// An animated SVG fixed at t seconds as a plain still (an animated badge's end, to continue from): its emblem colors
// (dark and light) and SMIL path data as they are then, folded into its color scheme and paths, its animations gone.
fun frozen(svgText: String, t: Double): String {
    val m = Regex("\\s*<style id=\"animation\"[^>]*>([\\s\\S]*?)</style>").find(svgText)
    val css = m?.groupValues?.get(1) ?: ""
    val dark = animatedValues(css, t, light = false, precise = true).first
    val light = animatedValues(css, t, light = true, precise = true).first
    var text = if (m != null) svgText.substring(0, m.range.first) + svgText.substring(m.range.last + 1) else svgText
    text = Regex("(<path\\b[^>]*?)\\sd=\"[^\"]*\"([^>]*)>\\s*<animate attributeName=\"d\"([^>]*)/>\\s*</path>").replace(text) { mm ->
        "${mm.groupValues[1]} d=\"${smilD(mm.groupValues[3], t, precise = true)}\"${mm.groupValues[2]}/>"
    }
    return Regex("(<style id=\"current-color-scheme\"[^>]*>\\s*)([\\s\\S]*?)(\\s*</style>)").replace(text) { mm ->
        val body = mm.groupValues[2]
        val media = body.indexOf("@media (prefers-color-scheme: light)")
        var base = if (media >= 0) body.substring(0, media) else body
        var lite = if (media >= 0) body.substring(media) else ""
        for (cls in listOf("Accent", "Emblem", "Emblem2")) {
            val d = dark[".ColorScheme-$cls"]?.get("color")
            val l = light[".ColorScheme-$cls"]?.get("color")
            val rule = Regex("(\\.ColorScheme-$cls\\{color:)[^}]*")
            if (d != null) base = rule.replace(base) { it.groupValues[1] + d }
            if (l != null && l != d) {
                lite = if (Regex("\\.ColorScheme-$cls\\{").containsMatchIn(lite)) rule.replace(lite) { it.groupValues[1] + l }
                else (if (lite.isNotEmpty()) lite.dropLast(1) else "@media (prefers-color-scheme: light){") + ".ColorScheme-$cls{color:$l}}"
            } else if (l != null && lite.isNotEmpty()) lite = Regex("\\.ColorScheme-$cls\\{[^}]*\\}").replace(lite, "")
        }
        mm.groupValues[1] + base + lite + mm.groupValues[3]
    }
}

// One layout kept (auto: as it is); of a chain, each of its steps'.
fun oneLayout(text: String, lay: String): String {
    if (lay == "auto" || layoutsOf(text).isEmpty()) return text
    if (isChain(text)) return chainSvg(chainSteps(text).map { it.copy(doc = oneLayout(it.doc, lay)) }, playTime(text).loops, endRecipe(text))
    return singleLayoutSvg(text, lay)
}

// An animated SVG continued by an animation from the still it ends on, in one layout (render-icon.py chained).
fun chainedSvg(prev: String, still: String, animate: String, duration: Double?, target: String?, lay: String, loop: Boolean, recipe: String?): String {
    val steps = chainSteps(prev).map { it.copy(doc = oneLayout(it.doc, lay)) }
    val step = animateSvg(oneLayout(still, lay), animate, duration, false, target?.let { oneLayout(it, lay) })
    return chainSvg(steps + ChainStep(step, 0.0, playTime(step).seconds), loop, recipe)
}

// render-icon.py recipe_args(): the arguments for a still icon, the end recipe an animated SVG carries. extra: what
// else builds it (a placement, a logo of your own), as the command line has it.
fun recipeArgs(name: String, emblem: Boolean, variant: String, padded: Boolean, o: ColorOptions?, extra: List<String> = emptyList()): String {
    val parts = arrayListOf(name)
    if (emblem) parts += "--emblem"
    parts += listOf("--variant", variant)
    if (padded) parts += "--padded"
    for ((flag, spec) in listOf("--color" to o?.color, "--logo-color" to o?.logo, "--emblem-color" to o?.emblem, "--tone2-color" to o?.tone2)) {
        if (spec != null) parts += listOf(flag, spec.label.removePrefix("#"))
    }
    return (parts + extra).joinToString(" ") { if (SHELL_SAFE.matches(it)) it else "'" + it.replace("'", "'\"'\"'") + "'" }
}

// render-icon.py's rule for where a published animated badge ends: a heartbeat or a shine on the status icon itself
// (icon-<state>), a change or a cycle frozen at its end (null: freeze it). Its name parts: from, to, shape.
private val BADGE_NAME = Regex("icon-(?:(\\w+)-to-(\\w+)|cycle|(\\w+))-(\\w+)")

fun badgeEndIcon(name: String): String? {
    val m = BADGE_NAME.matchEntire(name) ?: return null
    val to = m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { "ok" }
    return if (m.groupValues[4] == "heartbeat" || m.groupValues[4] == "shine") "icon-$to" else null
}
