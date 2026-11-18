package dev.rootiest.icons.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Animated rasters: an animated SVG's frames, as render-icon.py's frame_svg() makes them for librsvg, made here for
// AndroidSVG. Every animated value is worked out for the moment (keyframes through their timing curves, a morph's
// path between its shapes) and written onto the elements themselves: inline styles for opacity, color and the dashes
// (in each shape's own length; pathLength is not read), transform attributes for the moves about the emblem's
// center and the heartbeat and shine bands. The animation stylesheet goes, its plain rules kept. AndroidSVG draws no
// filters, so shine's copy is made white by hand.

const val FPS = 25

private val NUM = Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
private val ELEMENT = Regex("<(/?)([a-zA-Z][\\w:-]*)((?:\\s[^<>]*?)?)(/?)>")
private val EASE = mapOf(
    "ease" to listOf(0.25, 0.1, 0.25, 1.0), "ease-in" to listOf(0.42, 0.0, 1.0, 1.0),
    "ease-out" to listOf(0.0, 0.0, 0.58, 1.0), "ease-in-out" to listOf(0.42, 0.0, 0.58, 1.0),
)

private fun eased(timing: List<Double>?, f: Double): Double {
    if (timing == null || f <= 0 || f >= 1) return min(max(f, 0.0), 1.0)
    val (x1, y1, x2, y2) = timing
    fun bez(a: Double, b: Double, t: Double) = 3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t
    var lo = 0.0
    var hi = 1.0
    repeat(50) {
        val mid = (lo + hi) / 2
        if (bez(x1, x2, mid) < f) lo = mid else hi = mid
    }
    return bez(y1, y2, (lo + hi) / 2)
}

private fun timingOf(name: String): List<Double>? =
    if (name.startsWith("cubic-bezier")) NUM.findAll(name).map { it.value.toDouble() }.toList() else EASE[name]

private fun mixed(v0: String, v1: String, f: Double): String {
    val hex = Regex("#[0-9a-fA-F]{6}")
    if (hex.matches(v0) && hex.matches(v1)) {
        return "#" + (1..5 step 2).joinToString("") { i ->
            val a = v0.substring(i, i + 2).toInt(16)
            val b = v1.substring(i, i + 2).toInt(16)
            "%02x".format(pyRound(a + (b - a) * f))
        }
    }
    val n0 = NUM.findAll(v0).map { it.value.toDouble() }.toList()
    val n1 = NUM.findAll(v1).map { it.value.toDouble() }.toList()
    if (n0.isNotEmpty() && n0.size == n1.size) {
        val vals = n0.zip(n1).map { (a, b) -> fmtG(a + (b - a) * f) }.iterator()
        return NUM.replace(v0) { vals.next() }
    }
    return if (f < 0.5) v0 else v1
}

private class Rule(val selectors: List<String>, val decls: Map<String, String>)

private fun decls(text: String) = text.split(";").filter { ":" in it }.associate { it.substringBefore(":").trim() to it.substringAfter(":").trim() }

// The stylesheet's plain rules and its keyframes (@media blocks left out).
private fun stylesheet(css: String): Pair<List<Rule>, Map<String, List<Pair<Double, Map<String, String>>>>> {
    val rules = ArrayList<Rule>()
    val frames = HashMap<String, List<Pair<Double, Map<String, String>>>>()
    var i = 0
    while (i < css.length) {
        val j = css.indexOf('{', i)
        if (j < 0) break
        val head = css.substring(i, j).trim()
        var depth = 1
        var k = j + 1
        while (k < css.length && depth > 0) {
            if (css[k] == '{') depth++ else if (css[k] == '}') depth--
            k++
        }
        val inner = css.substring(j + 1, k - 1)
        if (head.startsWith("@keyframes")) {
            frames[head.split(Regex("\\s+"))[1]] = Regex("([^{}]+)\\{([^{}]*)\\}").findAll(inner).flatMap { m ->
                m.groupValues[1].split(",").map { it.trim().removeSuffix("%").toDouble() to decls(m.groupValues[2]) }
            }.sortedBy { it.first }.toList()
        } else if (!head.startsWith("@")) {
            rules += Rule(head.split(",").map { it.trim() }, decls(inner))
        }
        i = k
    }
    return rules to frames
}

private fun splitTop(value: String): List<String> {
    val out = ArrayList<String>()
    var depth = 0
    val cur = StringBuilder()
    for (ch in value) {
        if (ch == '(') depth++ else if (ch == ')') depth--
        if (ch == ',' && depth == 0) {
            out += cur.toString().trim()
            cur.clear()
        } else cur.append(ch)
    }
    if (cur.isNotBlank()) out += cur.toString().trim()
    return out
}

// {selector: {property: value}} of a stylesheet's animations at t seconds, and its plain rules without animations.
private fun animatedValues(css: String, t: Double): Pair<Map<String, Map<String, String>>, String> {
    val (rules, frames) = stylesheet(css)
    val anims = HashMap<String, MutableList<MutableList<String>>>()
    val base = HashMap<String, MutableMap<String, String>>()
    val plain = StringBuilder()
    for (r in rules) {
        val still = r.decls.filterKeys { !it.startsWith("animation") && !it.startsWith("transform") }
        if (still.isNotEmpty()) plain.append(r.selectors.joinToString(",") + "{" + still.entries.joinToString(";") { "${it.key}:${it.value}" } + "}")
        for (s in r.selectors) {
            base.getOrPut(s) { HashMap() }.putAll(still)
            r.decls["animation"]?.let { anims[s] = splitTop(it).map { a -> a.split(Regex("\\s+")).toMutableList() }.toMutableList() }
            r.decls["animation-name"]?.let { names -> anims[s]?.zip(splitTop(names))?.forEach { (a, n) -> a[0] = n } }
        }
    }
    val out = HashMap<String, MutableMap<String, String>>()
    for ((s, items) in anims) {
        for (a in items) {
            val name = a[0]
            val dur = a[1].removeSuffix("s").toDouble()
            val timing = timingOf(a.getOrElse(2) { "linear" })
            val p = if (a.getOrNull(3) == "infinite") (t % dur) / dur else min(t / dur, 1.0)
            val ks = frames[name] ?: continue
            for (prop in ks.flatMap { it.second.keys }.distinct()) {
                val under = base[s]?.get(prop) ?: when (prop) {
                    "opacity", "fill-opacity" -> "1"
                    "stroke-dashoffset" -> "0"
                    "stroke-opacity" -> if ("draw-trace" in s) "0" else "1"
                    else -> "none"
                }
                val pts = ks.filter { prop in it.second }.map { it.first to it.second.getValue(prop) }.toMutableList()
                if (pts.first().first > 0) pts.add(0, 0.0 to under)
                if (pts.last().first < 100) pts.add(100.0 to under)
                val x = p * 100
                var value = pts.last().second
                for (i in 0 until pts.size - 1) {
                    val (p0, v00) = pts[i]
                    val (p1, v10) = pts[i + 1]
                    if (x in p0..p1) {
                        var v0 = v00
                        var v1 = v10
                        if (prop == "transform" && v0 == "none") v0 = NUM.replace(v1) { if (v1.startsWith("scale")) "1" else "0" }
                        if (prop == "transform" && v1 == "none") v1 = NUM.replace(v0) { if (v0.startsWith("scale")) "1" else "0" }
                        value = mixed(v0, v1, eased(timing, if (p1 > p0) (x - p0) / (p1 - p0) else 1.0))
                        break
                    }
                }
                out.getOrPut(s) { HashMap() }[prop] = value
            }
        }
    }
    return out to plain.toString()
}

private fun drawnBox(text: String): List<Double>? {
    val stack = ArrayDeque(listOf(listOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)))
    val xs = ArrayList<Double>()
    val ys = ArrayList<Double>()
    for (m in ELEMENT.findAll(text)) {
        val (closing, name, attrs, empty) = m.destructured
        if (closing.isNotEmpty()) {
            if (stack.size > 1) stack.removeLast()
            continue
        }
        val mat = matMul(stack.last(), transformMatrix(getAttr(attrs, "transform")))
        if (empty.isEmpty()) stack.addLast(mat)
        if (name in setOf("path", "circle", "ellipse", "rect", "polygon")) {
            val d = if (name == "path") getAttr(attrs, "d") else shapeD(name, attrs)
            for (sub in pathSubpaths(d)) for ((px, py) in sub) {
                xs += mat[0] * px + mat[2] * py + mat[4]
                ys += mat[1] * px + mat[3] * py + mat[5]
            }
        }
    }
    return if (xs.isEmpty()) null else listOf(xs.min(), ys.min(), xs.max(), ys.max())
}

private fun pathLength(name: String, attrs: String): Double {
    val d = when (name) {
        "path" -> getAttr(attrs, "d")
        "line" -> "M${getAttr(attrs, "x1") ?: 0} ${getAttr(attrs, "y1") ?: 0}L${getAttr(attrs, "x2") ?: 0} ${getAttr(attrs, "y2") ?: 0}"
        "polyline" -> "M" + (getAttr(attrs, "points") ?: "")
        else -> shapeD(name, attrs)
    }
    var total = 0.0
    for (sub in pathSubpaths(d)) {
        val pts = if (name !in setOf("line", "polyline") && sub.size > 2) sub + listOf(sub[0]) else sub
        for (i in 0 until pts.size - 1) {
            val dx = pts[i + 1].first - pts[i].first
            val dy = pts[i + 1].second - pts[i].second
            total += sqrt(dx * dx + dy * dy)
        }
    }
    return total
}

private fun smilD(attrs: String, t: Double): String {
    val values = (getAttr(attrs, "values") ?: "").split(";")
    val dur = (getAttr(attrs, "dur") ?: "1s").removeSuffix("s").toDouble()
    val keys = getAttr(attrs, "keyTimes")?.split(";")?.map { it.toDouble() } ?: values.indices.map { it.toDouble() / (values.size - 1) }
    val splines = (getAttr(attrs, "keySplines") ?: "").split(";").filter { it.isNotBlank() }
        .map { s -> s.trim().split(Regex("\\s+")).map(String::toDouble) }
    val p = if (getAttr(attrs, "repeatCount") == "indefinite") (t % dur) / dur else min(t / dur, 1.0)
    for (i in 0 until keys.size - 1) {
        if (p in keys[i]..keys[i + 1]) {
            var f = if (keys[i + 1] > keys[i]) (p - keys[i]) / (keys[i + 1] - keys[i]) else 1.0
            if (getAttr(attrs, "calcMode") == "spline" && i < splines.size) f = eased(splines[i], f)
            return mixed(values[i], values[i + 1], f)
        }
    }
    return values.last()
}

// Which simple selectors (".a" or ".a .b") an element matches, from its classes and its ancestors'.
private fun matches(selector: String, own: Set<String>, above: Set<String>): Boolean {
    val parts = selector.trim().split(Regex("\\s+")).map { it.removePrefix(".") }
    return when (parts.size) {
        1 -> parts[0] in own
        2 -> parts[1] in own && parts[0] in above
        else -> false
    }
}

// The SVG still, at t seconds of its animation, for AndroidSVG.
fun frameSvg(svgText: String, t: Double): String {
    val style = Regex("\\s*<style id=\"animation\"[^>]*>([\\s\\S]*?)</style>").find(svgText)
    val (values, plain) = if (style != null) animatedValues(style.groupValues[1], t) else emptyMap<String, Map<String, String>>() to ""
    var text = if (style != null) svgText.replaceRange(style.range, "\n  <style id=\"animation\" type=\"text/css\">$plain</style>") else svgText
    // a morph's path (any path data SMIL animates): its shape at t
    text = Regex("(<path\\b[^>]*?)\\sd=\"[^\"]*\"([^>]*)>\\s*<animate attributeName=\"d\"([^>]*)/>\\s*</path>").replace(text) {
        "${it.groupValues[1]} d=\"${smilD(it.groupValues[3], t)}\"${it.groupValues[2]}/>"
    }
    // shine's copy: white, as its filter would make it
    text = Regex("<g filter=\"url\\(#shine-white\\)\">").replace(text, "<g class=\"shine-copy\">")
    val motion = values[".motion"]?.get("transform")
    val bands = listOf("sweep-band", "shine-band").associateWith { values[".$it"]?.get("transform") }
    // Walk the elements: classes in force, and each element's values.
    val classes = ArrayDeque<Set<String>>()
    val out = StringBuilder()
    var at = 0
    for (m in ELEMENT.findAll(text)) {
        out.append(text, at, m.range.first)
        at = m.range.last + 1
        val (closing, name, attrs0, empty) = m.destructured
        if (closing.isNotEmpty()) {
            if (classes.isNotEmpty()) classes.removeLast()
            out.append(m.value)
            continue
        }
        var attrs = attrs0
        val own = (getAttr(attrs, "class") ?: "").split(Regex("\\s+")).filter(String::isNotEmpty).toSet()
        val above = classes.flatten().toSet()
        val props = LinkedHashMap<String, String>()
        for ((sel, vs) in values) if (matches(sel, own, above)) props.putAll(vs)
        if ("shine-copy" in above || "shine-copy" in own) {
            props["color"] = "#ffffff"
            val fill = getAttr(attrs, "fill")
            if (fill != null && fill != "none" && fill != "currentColor") props["fill"] = "#ffffff"
        }
        val dash = props.remove("stroke-dasharray")
        val offset = props.remove("stroke-dashoffset")
        props.remove("transform")
        if (dash != null && dash != "none" && name in setOf("path", "circle", "ellipse", "rect", "polygon", "polyline", "line")) {
            val len = pathLength(name, attrs)
            val off = offset?.let { NUM.find(it)?.value?.toDouble() } ?: 0.0
            props["stroke-dasharray"] = "${fmtG(len)} ${fmtG(len)}"
            props["stroke-dashoffset"] = fmtG(off * len)
        }
        if (props.isNotEmpty()) {
            val old = getAttr(attrs, "style")
            attrs = setAttr(attrs, "style", (if (old.isNullOrBlank()) "" else "${old.trimEnd(';')};") + props.entries.joinToString(";") { "${it.key}:${it.value}" })
        }
        for ((band, move) in bands) if (move != null && band in own) {
            val v = NUM.find(move)!!.value.toDouble()
            attrs = setAttr(attrs, "transform", "translate(${fmtG(v)} 0)")
        }
        if (motion != null && "motion" in own && empty.isEmpty()) {
            // the group's contents: to its own closing tag
            var depth = 1
            var end = text.length
            for (g in Regex("<(/?)g\\b[^<>]*?(/?)>").findAll(text, m.range.last + 1)) {
                if (g.groupValues[2].isNotEmpty()) continue
                depth += if (g.groupValues[1].isNotEmpty()) -1 else 1
                if (depth == 0) { end = g.range.first; break }
            }
            drawnBox(text.substring(m.range.last + 1, end))?.let { (x0, y0, x1, y1) ->
                val cx = (x0 + x1) / 2
                val cy = (y0 + y1) / 2
                val fn = motion.substringBefore("(")
                val v = NUM.find(motion)!!.value.toDouble()
                attrs = setAttr(attrs, "transform", if (fn == "rotate") "rotate(${fmtG(v)} ${fmtG(cx)} ${fmtG(cy)})"
                else "translate(${fmtG(cx)} ${fmtG(cy)}) scale(${fmtG(v)}) translate(${fmtG(-cx)} ${fmtG(-cy)})")
            }
        }
        out.append("<$name$attrs$empty>")
        if (empty.isEmpty()) classes.addLast(own)
    }
    out.append(text, at, text.length)
    return out.toString()
}

// The moments a run's animated rasters show, FPS a second: one cycle of a repeating animation, or the whole of one
// that plays once, its last frame included; and whether it plays once.
fun frameTimes(animate: String, loop: Boolean, duration: Double?): Pair<List<Double>, Boolean> {
    val dur = duration ?: defaultDuration(animate, loop)
    val count = max(1, pyRound(dur * FPS).toInt())  // half to even, as Python rounds: 2.5 s is 62 frames
    val once = animate == "pop" || (animate in setOf("draw", "morph") && !loop)
    return (List(count) { it.toDouble() / FPS } + if (once) listOf(dur) else emptyList()) to once
}
