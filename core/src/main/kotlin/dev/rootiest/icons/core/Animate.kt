package dev.rootiest.icons.core

import kotlin.math.abs
import kotlin.math.sqrt

// Animated SVG: render-icon.py's --animate. draw: the icon draws itself, each shape along its length (a stroked shape
// its stroke, a filled one traced by an outline in its own color that gives way to the fill). heartbeat: a bright band
// sweeps across the emblem, left to right, over a dimmed copy of it, as a heart monitor draws. Either way the icon as
// it is shows wherever the animation does not play.

val ANIMATIONS = listOf("none", "draw", "heartbeat")

// Default seconds: the whole drawing, one cycle of a looping drawing, one beat.
const val DRAW_ONCE = 2.5
const val DRAW_LOOP = 4.0
const val HEARTBEAT = 1.6

fun defaultDuration(animate: String, loop: Boolean): Double =
    if (animate == "heartbeat") HEARTBEAT else if (loop) DRAW_LOOP else DRAW_ONCE

private val DRAWN = setOf("path", "circle", "ellipse", "rect", "polygon", "polyline", "line")
private val UNDRAWN = setOf("defs", "mask", "clipPath", "symbol", "pattern", "marker", "linearGradient", "radialGradient", "filter")
private val ANIM_TAG = Regex("<(/?)([a-zA-Z][\\w:-]*)((?:\\s[^<>]*?)?)(/?)>")
private const val TRACE_WIDTH = 0.01
private const val DRAW_EASE = "cubic-bezier(.5,0,.3,1)"
private val NONE = setOf("none", "transparent")

// render-icon.py draw_css().
private fun drawCss(duration: Double, loop: Boolean): String {
    val dash = "stroke-dasharray:1 1;"
    val line0 = "${dash}stroke-dashoffset:1;fill-opacity:0"
    val line1 = "${dash}stroke-dashoffset:0;fill-opacity:0"
    val trace0 = "${dash}stroke-dashoffset:1;stroke-opacity:1;fill-opacity:0"
    val trace1 = "${dash}stroke-dashoffset:0;stroke-opacity:1;fill-opacity:0"
    val (line, trace) = if (loop) {
        "0%,100%{$line0}40%{$line1}60%,85%{${dash}stroke-dashoffset:0;fill-opacity:1}" to
            "0%,100%{$trace0}40%{$trace1}60%,85%{${dash}stroke-dashoffset:0;stroke-opacity:0;fill-opacity:1}"
    } else {
        "0%{$line0}60%{$line1}" to "0%{$trace0}60%{$trace1}"
    }
    return ".draw-line,.draw-trace{animation:draw-line ${fmtG(duration)}s $DRAW_EASE ${if (loop) "infinite" else "1"} both}" +
        ".draw-trace{animation-name:draw-trace}" +
        "@keyframes draw-line{$line}@keyframes draw-trace{$trace}" +
        "@media (prefers-reduced-motion: reduce){.draw-line,.draw-trace{animation:none}}"
}

// render-icon.py transform_scale(): how much a transform attribute scales lengths.
internal fun transformScale(transform: String?): Double {
    var det = 1.0
    for (m in Regex("([a-zA-Z]+)\\s*\\(([^)]*)\\)").findAll(transform ?: "")) {
        val v = Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?").findAll(m.groupValues[2]).map { it.value.toDouble() }.toList()
        val name = m.groupValues[1]
        if (name == "scale" && v.isNotEmpty()) det *= v[0] * (if (v.size > 1) v[1] else v[0])
        else if (name == "matrix" && v.size == 6) det *= v[0] * v[3] - v[1] * v[2]
    }
    val s = sqrt(abs(det))
    return if (s == 0.0) 1.0 else s
}

// render-icon.py style_value(): a presentation property from the style attribute, else the attribute.
private fun styleValue(attrs: String, name: String): String? {
    val m = Regex("(?:^|;)\\s*$name\\s*:\\s*([^;]+)").find(getAttr(attrs, "style") ?: "")
    return m?.groupValues?.get(1)?.trim() ?: getAttr(attrs, name)
}

// render-icon.py visible_layouts(): layouts switched by visibility, so Firefox plays one file shown at several sizes.
fun visibleLayouts(text: String): String {
    val m = Regex("(<style id=\"sizes\"[^>]*>)([\\s\\S]*?)(</style>)").find(text) ?: return text
    val css = m.groupValues[2].replace("display:none", "visibility:hidden").replace("display:inline", "visibility:visible")
    return text.replaceRange(m.range, m.groupValues[1] + css + m.groupValues[3])
}

// render-icon.py animated_svg(): --animate draw.
fun animatedSvg(svgText: String, duration: Double, loop: Boolean): String {
    val text = visibleLayouts(svgText)
    val vb = Regex("<svg\\b[^>]*\\sviewBox=\"([^\"]*)\"").find(text)
    val nums = vb?.groupValues?.get(1)?.replace(",", " ")?.trim()?.split(Regex("\\s+"))?.filter(String::isNotEmpty)?.map(String::toDouble) ?: emptyList()
    val width = TRACE_WIDTH * (if (nums.size == 4) maxOf(nums[2], nums[3]) else 24.0)
    class Frame(val fill: String, val stroke: String, val scale: Double, val hidden: Boolean)
    val stack = ArrayDeque(listOf(Frame("black", "none", 1.0, false)))
    val head = Regex("<svg\\b[^>]*>").find(text) ?: return text
    val body = ANIM_TAG.replace(text.substring(head.range.last + 1)) { m ->
        val (closing, name, rawAttrs, empty) = m.destructured
        if (closing.isNotEmpty()) {
            if (stack.size > 1) stack.removeLast()
            return@replace m.value
        }
        val top = stack.last()
        val fill = styleValue(rawAttrs, "fill") ?: top.fill
        val stroke = styleValue(rawAttrs, "stroke") ?: top.stroke
        val scale = top.scale * transformScale(getAttr(rawAttrs, "transform"))
        val hidden = top.hidden || name in UNDRAWN
        if (empty.isEmpty()) stack.addLast(Frame(fill, stroke, scale, hidden))
        if (hidden || name !in DRAWN) return@replace m.value
        val stroked = stroke !in NONE
        if (!stroked && fill in NONE) return@replace m.value
        val trimmed = rawAttrs.trimEnd()
        val tail = rawAttrs.substring(trimmed.length)
        var attrs = setAttr(trimmed, "pathLength", "1")
        val cls = getAttr(attrs, "class")
        attrs = setAttr(attrs, "class", (if (cls != null) "$cls " else "") + if (stroked) "draw-line" else "draw-trace")
        if (!stroked) {
            for ((k, v) in listOf("stroke" to fill, "stroke-width" to fmtG(width / scale, 4), "stroke-opacity" to "0",
                "stroke-linejoin" to "round", "stroke-linecap" to "round")) attrs = setAttr(attrs, k, v)
        }
        "<$name$attrs$tail$empty>"
    }
    return text.substring(0, head.range.last + 1) + "\n  <style id=\"animation\" type=\"text/css\">${drawCss(duration, loop)}</style>" + body
}

// render-icon.py heartbeat_css().
private fun heartbeatCss(duration: Double): String =
    ".sweep-base{animation:sweep-dim ${fmtG(duration)}s linear infinite}@keyframes sweep-dim{0%,100%{opacity:.3}}" +
        ".sweep-band{animation:sweep-band ${fmtG(duration)}s cubic-bezier(.4,0,.6,1) infinite}" +
        "@keyframes sweep-band{0%{transform:translateX(0)}80%,100%{transform:translateX(1.7px)}}" +
        "@media (prefers-reduced-motion: reduce){.sweep-base,.sweep-band{animation:none}}"

private const val SWEEP_DEFS = "<defs><linearGradient id=\"sweep-fade\"><stop offset=\"0\" stop-color=\"#fff\" stop-opacity=\"0\"/>" +
    "<stop offset=\"1\" stop-color=\"#fff\"/></linearGradient><mask id=\"sweep\" x=\"-0.5\" y=\"-0.5\" width=\"2\" " +
    "height=\"2\" maskContentUnits=\"objectBoundingBox\"><rect class=\"sweep-band\" x=\"-0.7\" y=\"-0.5\" width=\"0.6\" " +
    "height=\"2\" fill=\"url(#sweep-fade)\"/></mask></defs>"

// render-icon.py emblem_groups(): each <g> whose classes include ColorScheme-Emblem, through its own closing tag.
private fun emblemGroups(body: String): List<IntRange> {
    val spans = ArrayList<IntRange>()
    var depth = 0
    var start = -1
    for (m in Regex("<(/?)g\\b([^<>]*?)(/?)>").findAll(body)) {
        val (closing, attrs, empty) = m.destructured
        if (empty.isNotEmpty()) continue
        if (closing.isNotEmpty()) {
            depth -= 1
            if (start >= 0 && depth == 0) {
                spans += start until m.range.last + 1
                start = -1
            }
        } else if (start >= 0) depth += 1
        else if ("ColorScheme-Emblem" in (getAttr(attrs, "class") ?: "").split(Regex("\\s+"))) {
            start = m.range.first
            depth = 1
        }
    }
    return spans
}

// render-icon.py heartbeat_svg(): --animate heartbeat.
fun heartbeatSvg(svgText: String, duration: Double): String {
    val text = visibleLayouts(svgText)
    val head = Regex("<svg\\b[^>]*>").find(text) ?: return text
    val end = head.range.last + 1
    var body = text.substring(end)
    fun beat(group: String): String {
        val open = group.substring(0, group.indexOf('>'))
        val cls = getAttr(open, "class")
        return setAttr(open, "class", (if (cls != null) "$cls " else "") + "sweep-base") + group.substring(open.length) +
            "<g class=\"sweep-head\" mask=\"url(#sweep)\">$group</g>"
    }
    val spans = emblemGroups(body)
    body = if (spans.isNotEmpty()) {
        val out = StringBuilder()
        var at = 0
        for (span in spans) {
            out.append(body, at, span.first).append(beat(body.substring(span)))
            at = span.last + 1
        }
        out.append(body, at, body.length).toString()
    } else {
        val defs = body.indexOf("</defs>")
        val lo = if (defs >= 0) defs + "</defs>".length else 0
        val last = body.lastIndexOf("</svg>")
        val hi = if (last >= lo) last else body.length
        val inner = body.substring(lo, hi)
        body.substring(0, lo) + "\n  <g class=\"sweep-base\">$inner</g><g class=\"sweep-head\" mask=\"url(#sweep)\">$inner</g>\n" + body.substring(hi)
    }
    return text.substring(0, end) + "\n  <style id=\"animation\" type=\"text/css\">${heartbeatCss(duration)}</style>\n  $SWEEP_DEFS" + body
}

// render-icon.py animate_svg(): the SVG in an animation, duration null for its default; none leaves it as it is.
fun animateSvg(text: String, animate: String, duration: Double? = null, loop: Boolean = false): String = when (animate) {
    "draw" -> animatedSvg(text, duration ?: defaultDuration("draw", loop), loop)
    "heartbeat" -> heartbeatSvg(text, duration ?: HEARTBEAT)
    else -> text
}

// render-icon.py animation_tag(): -draw, -draw-loop, -draw-loop-6s, -heartbeat, -heartbeat-1.2s; "" when still.
fun animationTag(animate: String, loop: Boolean, duration: Double?): String {
    if (animate == "none") return ""
    return "-$animate" + (if (animate == "draw" && loop) "-loop" else "") + (duration?.let { "-${fmtG(it)}s" } ?: "")
}
