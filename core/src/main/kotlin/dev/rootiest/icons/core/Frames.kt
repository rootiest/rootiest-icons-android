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
private val SPACE = Regex("\\s+")
private val HEX = Regex("#[0-9a-fA-F]{6}")
private val RULE = Regex("([^{}]+)\\{([^{}]*)\\}")
private val ANIMATION_STYLE = Regex("\\s*<style id=\"animation\"[^>]*>([\\s\\S]*?)</style>")
private val SMIL_PATH = Regex("(<path\\b[^>]*?)\\sd=\"[^\"]*\"([^>]*)>\\s*<animate attributeName=\"d\"([^>]*)/>\\s*</path>")
private val SHINE_FILTER = Regex("<g filter=\"url\\(#shine-white\\)\">")
private val GLITCH_FILTER = Regex("\\s*filter=\"url\\(#glitch-(?:cyan|pink)\\)\"")
private val TRANSFORM_OP = Regex("(\\w+)\\(([^)]*)\\)")
private val G_TAG = Regex("<(/?)g\\b[^<>]*?(/?)>")

// What stays the same from frame to frame, worked out once: an animation's frames all come from one SVG, so each
// path's length, each group's drawn box and each animated path's numbers are kept here (the most recent ones).
private class Memo<K, V>(private val max: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > max
    }

    @Synchronized
    fun get(key: K, make: () -> V): V = map[key] ?: make().also { map[key] = it }
}

private val lengths = Memo<String, Double>(512)
private val boxes = Memo<String, List<Double>?>(64)

// A value's numbers, and the text around them: n numbers between n + 1 pieces.
private class Numbers(val pieces: List<String>, val values: DoubleArray)

private val numbered = Memo<String, Numbers>(256)

private fun numbersOf(v: String) = numbered.get(v) {
    val pieces = ArrayList<String>()
    val values = ArrayList<Double>()
    var at = 0
    for (m in NUM.findAll(v)) {
        pieces += v.substring(at, m.range.first)
        values += m.value.toDouble()
        at = m.range.last + 1
    }
    pieces += v.substring(at)
    Numbers(pieces, values.toDoubleArray())
}
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

// A number between two keyframes', to 4 decimals: only drawn, never compared with the command line's text, and far
// quicker than fmtG (an animated path has hundreds a frame).
private fun quick(x: Double): String {
    if (x.isNaN() || kotlin.math.abs(x) >= 1e14) return fmtG(x)
    val r = Math.round(x * 10000)
    if (r == 0L) return "0"
    val a = kotlin.math.abs(r)
    val whole = a / 10000
    val frac = a % 10000
    val sign = if (r < 0) "-" else ""
    if (frac == 0L) return "$sign$whole"
    return "$sign$whole." + frac.toString().padStart(4, '0').trimEnd('0')
}

// precise: numbers as the command line writes them (fmtG), for text that is compared with it (a chain's keyframes).
private fun mixed(v0: String, v1: String, f: Double, precise: Boolean = false): String {
    if (v0.length == 7 && v1.length == 7 && HEX.matches(v0) && HEX.matches(v1)) {
        return "#" + (1..5 step 2).joinToString("") { i ->
            val a = v0.substring(i, i + 2).toInt(16)
            val b = v1.substring(i, i + 2).toInt(16)
            "%02x".format(pyRound(a + (b - a) * f))
        }
    }
    val n0 = numbersOf(v0)
    val n1 = numbersOf(v1)
    if (n0.values.isNotEmpty() && n0.values.size == n1.values.size) {
        val out = StringBuilder(v0.length + 16)
        for (i in n0.values.indices) {
            val a = n0.values[i]
            val x = a + (n1.values[i] - a) * f
            out.append(n0.pieces[i]).append(if (precise) fmtG(x) else quick(x))
        }
        return out.append(n0.pieces.last()).toString()
    }
    return if (f < 0.5) v0 else v1
}

internal class Rule(val selectors: List<String>, val decls: Map<String, String>)

private fun decls(text: String) = text.split(";").filter { ":" in it }.associate { it.substringBefore(":").trim() to it.substringAfter(":").trim() }

// The stylesheet's plain rules and its keyframes. @media blocks are left out, but for light the light-mode block's
// rules are read in their place, over the rules before them, as a browser in light mode reads them.
internal fun stylesheet(css: String, light: Boolean = false): Pair<List<Rule>, Map<String, List<Pair<Double, Map<String, String>>>>> {
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
            frames[head.split(SPACE)[1]] = RULE.findAll(inner).flatMap { m ->
                m.groupValues[1].split(",").map { it.trim().removeSuffix("%").toDouble() to decls(m.groupValues[2]) }
            }.sortedBy { it.first }.toList()
        } else if (!head.startsWith("@")) {
            rules += Rule(head.split(",").map { it.trim() }, decls(inner))
        } else if (light && head == "@media (prefers-color-scheme: light)") {
            rules += stylesheet(inner).first
        }
        i = k
    }
    return rules to frames
}

internal fun splitTop(value: String): List<String> {
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
private val sheets = Memo<String, Pair<List<Rule>, Map<String, List<Pair<Double, Map<String, String>>>>>>(8)

internal fun animatedValues(css: String, t: Double, light: Boolean, precise: Boolean = false): Pair<Map<String, Map<String, String>>, String> {
    val (rules, frames) = sheets.get("$light $css") { stylesheet(css, light) }
    val anims = HashMap<String, MutableList<MutableList<String>>>()
    val base = HashMap<String, MutableMap<String, String>>()
    val plain = StringBuilder()
    for (r in rules) {
        val still = r.decls.filterKeys { !it.startsWith("animation") && !it.startsWith("transform") }
        if (still.isNotEmpty()) plain.append(r.selectors.joinToString(",") + "{" + still.entries.joinToString(";") { "${it.key}:${it.value}" } + "}")
        for (s in r.selectors) {
            base.getOrPut(s) { HashMap() }.putAll(still)
            r.decls["animation"]?.let { anims[s] = splitTop(it).map { a -> a.split(SPACE).toMutableList() }.toMutableList() }
            r.decls["animation-name"]?.let { names -> anims[s]?.zip(splitTop(names))?.forEach { (a, n) -> a[0] = n } }
        }
    }
    val out = HashMap<String, MutableMap<String, String>>()
    for ((s, items) in anims) {
        for (a in items) {
            val name = a[0]
            val dur = a[1].removeSuffix("s").toDouble()
            val timing = timingOf(a.getOrElse(2) { "linear" })
            val p = progress(t, dur, iterations(a.getOrNull(3)))
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
                        value = mixed(v0, v1, eased(timing, if (p1 > p0) (x - p0) / (p1 - p0) else 1.0), precise)
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

// How many times an animation plays: its iteration count (CSS) or repeatCount (SMIL); null, forever.
internal fun iterations(count: String?): Double? =
    if (count == "infinite" || count == "indefinite") null else count?.toDoubleOrNull() ?: 1.0

// How far through its current run an animation of dur seconds is at t, from 0 to 1: one that plays a number of times
// starts each run over, then holds its end.
private fun progress(t: Double, dur: Double, count: Double?): Double =
    if (count == null || t < dur * count) (t % dur) / dur else 1.0

// An animated path's <animate>, read: its values, key times, splines and timing.
private class Smil(val values: List<String>, val keys: List<Double>, val splines: List<List<Double>>, val dur: Double, val count: Double?, val spline: Boolean)

private val smils = Memo<String, Smil>(64)

internal fun smilD(attrs: String, t: Double, precise: Boolean = false): String {
    val a = smils.get(attrs) {
        val values = (getAttr(attrs, "values") ?: "").split(";")
        Smil(
            values,
            getAttr(attrs, "keyTimes")?.split(";")?.map { it.toDouble() } ?: values.indices.map { it.toDouble() / (values.size - 1) },
            (getAttr(attrs, "keySplines") ?: "").split(";").filter { it.isNotBlank() }.map { s -> s.trim().split(SPACE).map(String::toDouble) },
            (getAttr(attrs, "dur") ?: "1s").removeSuffix("s").toDouble(),
            iterations(getAttr(attrs, "repeatCount")),
            getAttr(attrs, "calcMode") == "spline",
        )
    }
    val p = progress(t, a.dur, a.count)
    val keys = a.keys
    for (i in 0 until keys.size - 1) {
        if (p in keys[i]..keys[i + 1]) {
            var f = if (keys[i + 1] > keys[i]) (p - keys[i]) / (keys[i + 1] - keys[i]) else 1.0
            if (a.spline && i < a.splines.size) f = eased(a.splines[i], f)
            return mixed(a.values[i], a.values[i + 1], f, precise)
        }
    }
    return a.values.last()
}

// Which simple selectors (".a" or ".a .b") an element matches, from its classes and its ancestors'.
private fun matches(selector: String, own: Set<String>, above: Set<String>): Boolean {
    val parts = selector.trim().split(SPACE).map { it.removePrefix(".") }
    return when (parts.size) {
        1 -> parts[0] in own
        2 -> parts[1] in own && parts[0] in above
        else -> false
    }
}

// render-icon.py svg_transform(): a CSS transform list about a box's own center as an SVG transform attribute.
private fun svgTransform(cssTr: String, box: List<Double>): String {
    val (x0, y0, x1, y1) = box
    val cx = (x0 + x1) / 2
    val cy = (y0 + y1) / 2
    val bw = x1 - x0
    val bh = y1 - y0
    val ops = TRANSFORM_OP.findAll(cssTr).map { it.groupValues[1] to it.groupValues[2] }.toList()
    if (ops.size == 1 && ops[0].first == "rotate") {
        val v = NUM.find(ops[0].second)!!.value.toDouble()
        return "rotate(${fmtG(v)} ${fmtG(cx)} ${fmtG(cy)})"
    }
    val parts = ArrayList<String>(ops.size)
    for ((fn, args) in ops) {
        val nums = NUM.findAll(args).map { it.value.toDouble() }.toList()
        when (fn) {
            "rotate" -> parts += "rotate(${fmtG(nums[0])})"
            "scale" -> parts += if (nums.size == 1) "scale(${fmtG(nums[0])})" else "scale(${fmtG(nums[0])} ${fmtG(nums[1])})"
            "scaleX" -> parts += "scale(${fmtG(nums[0])} 1)"
            "scaleY" -> parts += "scale(1 ${fmtG(nums[0])})"
            "translateX" -> parts += "translate(${fmtG(nums[0] * if ("%" in args) bw / 100 else 1.0)} 0)"
            "translateY" -> parts += "translate(0 ${fmtG(nums[0] * if ("%" in args) bh / 100 else 1.0)})"
            "translate" -> {
                val sp = args.split(",")
                val vx = nums[0] * if ("%" in sp[0]) bw / 100 else 1.0
                val vy = if (nums.size > 1) nums[1] * if (sp.size > 1 && "%" in sp[1]) bh / 100 else 1.0 else 0.0
                parts += "translate(${fmtG(vx)} ${fmtG(vy)})"
            }
        }
    }
    return "translate(${fmtG(cx)} ${fmtG(cy)}) ${parts.joinToString(" ")} translate(${fmtG(-cx)} ${fmtG(-cy)})"
}

// The SVG still, at t seconds of its animation, for AndroidSVG: as a viewer in dark mode sees it (the rasters' colors),
// or with light, in light mode (a preview; its other styles are fixed to light mode by schemeSvg).
fun frameSvg(svgText: String, t: Double, light: Boolean = false): String {
    val style = ANIMATION_STYLE.find(svgText)
    val (values, plain) = if (style != null) animatedValues(style.groupValues[1], t, light) else emptyMap<String, Map<String, String>>() to ""
    var text = if (style != null) svgText.replaceRange(style.range, "\n  <style id=\"animation\" type=\"text/css\">$plain</style>") else svgText
    // a morph's path (any path data SMIL animates): its shape at t
    text = SMIL_PATH.replace(text) {
        "${it.groupValues[1]} d=\"${smilD(it.groupValues[3], t)}\"${it.groupValues[2]}/>"
    }
    // shine's copy and glitch's ghost copies: tinted by hand, as AndroidSVG draws no filters
    text = SHINE_FILTER.replace(text, "<g class=\"shine-copy\">")
    text = GLITCH_FILTER.replace(text, "")
    val centered = listOf("motion", "ping-ring", "glitch-a", "glitch-b", "glitch-main").associateWith { values[".$it"]?.get("transform") }
    val bands = listOf("sweep-band", "shine-band", "fill-band").associateWith { values[".$it"]?.get("transform") }
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
        val own = (getAttr(attrs, "class") ?: "").split(SPACE).filter(String::isNotEmpty).toSet()
        val above = classes.flatten().toSet()
        val props = LinkedHashMap<String, String>()
        for ((sel, vs) in values) if (matches(sel, own, above)) props.putAll(vs)
        val tint = when {
            "shine-copy" in above || "shine-copy" in own -> "#ffffff"
            "glitch-a" in above || "glitch-a" in own -> "#00e6ff"
            "glitch-b" in above || "glitch-b" in own -> "#ff004c"
            else -> null
        }
        if (tint != null) {
            props["color"] = tint
            val fill = getAttr(attrs, "fill")
            if (fill != null && fill != "none" && fill != "currentColor") props["fill"] = tint
            val stroke = getAttr(attrs, "stroke")
            if (stroke != null && stroke != "none" && stroke != "currentColor") props["stroke"] = tint
        }
        val dash = props.remove("stroke-dasharray")
        val offset = props.remove("stroke-dashoffset")
        props.remove("transform")
        if (dash != null && dash != "none" && name in setOf("path", "circle", "ellipse", "rect", "polygon", "polyline", "line")) {
            val len = lengths.get("$name $attrs0") { pathLength(name, attrs0) }
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
            val tr = if (move.startsWith("translateY")) "translate(0 ${fmtG(v)})" else "translate(${fmtG(v)} 0)"
            attrs = setAttr(attrs, "transform", tr)
        }
        if (empty.isEmpty()) {
            val trCss = centered.entries.firstOrNull { (cls, tr) -> tr != null && cls in own }?.value
            if (trCss != null) {
                var depth = 1
                var end = text.length
                for (g in G_TAG.findAll(text, m.range.last + 1)) {
                    if (g.groupValues[2].isNotEmpty()) continue
                    depth += if (g.groupValues[1].isNotEmpty()) -1 else 1
                    if (depth == 0) { end = g.range.first; break }
                }
                val inner = text.substring(m.range.last + 1, end)
                boxes.get(inner) { drawnBox(inner) }?.let { box ->
                    attrs = setAttr(attrs, "transform", svgTransform(trCss, box))
                }
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
    val once = animate == "pop" || (animate in LOOPABLE && !loop)
    return (List(count) { it.toDouble() / FPS } + if (once) listOf(dur) else emptyList()) to once
}

// How long a published animated file (an animated status badge) plays, and whether it loops, read from its own text:
// the durations and iteration counts of the CSS animations in its animation stylesheet and of its SMIL <animate>s. One
// that loops plays until all its loops line up again; one that plays once, until its last animation ends.
data class PlayTime(val seconds: Double, val loops: Boolean)

private val ANIMATE_TAG = Regex("<animate\\b[^>]*>")

fun playTime(svgText: String): PlayTime {
    val runs = ArrayList<Pair<Double, Double?>>()  // seconds, times (null: forever)
    ANIMATION_STYLE.find(svgText)?.let { style ->
        for (r in stylesheet(style.groupValues[1]).first) {
            for (item in splitTop(r.decls["animation"] ?: continue)) {
                val a = item.split(SPACE)
                val dur = a.getOrNull(1)?.removeSuffix("s")?.toDoubleOrNull() ?: continue
                runs += dur to iterations(a.getOrNull(3))
            }
        }
    }
    for (m in ANIMATE_TAG.findAll(svgText)) {
        val dur = getAttr(m.value, "dur")?.removeSuffix("s")?.toDoubleOrNull() ?: continue
        runs += dur to iterations(getAttr(m.value, "repeatCount"))
    }
    val forever = runs.filter { it.second == null && it.first > 0 }.map { pyRound(it.first * 1000) }
    if (forever.isEmpty()) return PlayTime(runs.maxOfOrNull { it.first * (it.second ?: 1.0) } ?: 0.0, false)
    fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    val ms = forever.reduce { a, b -> a / gcd(a, b) * b }
    // loops that never line up within a minute: the longest one stands for them
    return PlayTime(if (ms <= 60_000) ms / 1000.0 else forever.max() / 1000.0, true)
}

// The moments a published animated file's frames show, FPS a second, from its own play time: one run of a loop, or
// the whole of one that plays once, its last frame included; and whether it plays once.
fun playFrames(svgText: String): Pair<List<Double>, Boolean> {
    val (seconds, loops) = playTime(svgText)
    val count = max(1, pyRound(seconds * FPS).toInt())
    return (List(count) { it.toDouble() / FPS } + if (loops) emptyList() else listOf(seconds)) to !loops
}
