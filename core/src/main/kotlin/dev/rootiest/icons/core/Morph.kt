package dev.rootiest.icons.core

import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// render-icon.py's morphing (--animate morph --morph-to): the emblems' filled shapes in the emblem color, flattened to
// polygons, resampled, paired and aligned, drawn by one path while the icon turns into the target, and those in its
// second tone by another under it; the rest cross-fades, the incoming side in before the outgoing one goes. Its arithmetic is render-icon.py's to the last bit: trigonometry from + - * / sqrt and floor, plain sums.

const val MORPH_ONCE = 1.2
const val MORPH_LOOP = 4.0
private const val MORPH_POINTS = 96
private const val MORPH_EASE = ".6 0 .3 1"
private val NUMBER = Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
private val IDENTITY = listOf(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
private const val HALF_PI = Math.PI / 2
private val DRAWN_SHAPES = setOf("path", "circle", "ellipse", "rect", "polygon", "polyline", "line")
private val UNDRAWN_PARTS = setOf("defs", "mask", "clipPath", "symbol", "pattern", "marker", "linearGradient", "radialGradient", "filter")
private val TAG = Regex("<(/?)([a-zA-Z][\\w:-]*)((?:\\s[^<>]*?)?)(/?)>")

typealias Pt = Pair<Double, Double>

// render-icon.py p_sincos(), p_atan(), p_atan2().
internal fun pSinCos(x: Double): Pair<Double, Double> {
    val q = floor(x / HALF_PI + 0.5)
    val r = x - q * HALF_PI
    val r2 = r * r
    var s = 1.0
    var c = 1.0
    for (k in 16 downTo 2 step 2) {
        s = 1 - r2 / (k * (k + 1)) * s
        c = 1 - r2 / ((k - 1) * k) * c
    }
    s *= r
    return when (((q % 4) + 4) % 4) {
        0.0 -> s to c
        1.0 -> c to -s
        2.0 -> -s to -c
        else -> -c to s
    }
}

private fun pAtan(z0: Double): Double {
    var z = z0
    repeat(2) { z = z / (1 + sqrt(1 + z * z)) }
    val z2 = z * z
    var t = 0.0
    for (k in 31 downTo 1 step 2) t = 1.0 / k - z2 * t
    return 4 * z * t
}

internal fun pAtan2(y: Double, x: Double): Double {
    if (x == 0.0 && y == 0.0) return 0.0
    if (abs(x) >= abs(y)) {
        val a = pAtan(y / x)
        return if (x < 0) a + (if (y >= 0) Math.PI else -Math.PI) else a
    }
    return (if (y > 0) HALF_PI else -HALF_PI) - pAtan(x / y)
}

internal fun matMul(m: List<Double>, n: List<Double>): List<Double> {
    val (a, b, c, d, e) = m
    val f = m[5]
    val (p, q, r, s, t) = n
    val u = n[5]
    return listOf(a * p + c * q, b * p + d * q, a * r + c * s, b * r + d * s, a * t + c * u + e, b * t + d * u + f)
}

internal fun transformMatrix(transform: String?): List<Double> {
    var m = IDENTITY
    for (mm in Regex("([a-zA-Z]+)\\s*\\(([^)]*)\\)").findAll(transform ?: "")) {
        val name = mm.groupValues[1]
        val v = NUMBER.findAll(mm.groupValues[2]).map { it.value.toDouble() }.toList()
        val n: List<Double>? = when {
            name == "matrix" && v.size == 6 -> v
            name == "translate" && v.isNotEmpty() -> listOf(1.0, 0.0, 0.0, 1.0, v[0], if (v.size > 1) v[1] else 0.0)
            name == "scale" && v.isNotEmpty() -> listOf(v[0], 0.0, 0.0, if (v.size > 1) v[1] else v[0], 0.0, 0.0)
            name == "rotate" && v.isNotEmpty() -> {
                val (sn, cs) = pSinCos(v[0] * (Math.PI / 180))
                val rot = listOf(cs, sn, -sn, cs, 0.0, 0.0)
                if (v.size == 3) matMul(matMul(listOf(1.0, 0.0, 0.0, 1.0, v[1], v[2]), rot), listOf(1.0, 0.0, 0.0, 1.0, -v[1], -v[2])) else rot
            }
            (name == "skewX" || name == "skewY") && v.isNotEmpty() -> {
                val (sn, cs) = pSinCos(v[0] * (Math.PI / 180))
                if (name == "skewX") listOf(1.0, 0.0, sn / cs, 1.0, 0.0, 0.0) else listOf(1.0, sn / cs, 0.0, 1.0, 0.0, 0.0)
            }
            else -> null
        }
        if (n != null) m = matMul(m, n)
    }
    return m
}

private fun arcPoints(x1: Double, y1: Double, rx0: Double, ry0: Double, phi: Double, large: Boolean, sweep: Boolean, x2: Double, y2: Double): List<Pt> {
    if (rx0 == 0.0 || ry0 == 0.0 || (x1 == x2 && y1 == y2)) return listOf(x2 to y2)
    var rx = abs(rx0)
    var ry = abs(ry0)
    val (sp, cp) = pSinCos(phi * (Math.PI / 180))
    val dx = (x1 - x2) / 2
    val dy = (y1 - y2) / 2
    val xp = cp * dx + sp * dy
    val yp = -sp * dx + cp * dy
    val lam = xp * xp / (rx * rx) + yp * yp / (ry * ry)
    if (lam > 1) {
        rx *= sqrt(lam)
        ry *= sqrt(lam)
    }
    val den = rx * rx * yp * yp + ry * ry * xp * xp
    var co = if (den != 0.0) sqrt(max(0.0, (rx * rx * ry * ry - den) / den)) else 0.0
    if (large == sweep) co = -co
    val cxp = co * rx * yp / ry
    val cyp = -co * ry * xp / rx
    val cx = cp * cxp - sp * cyp + (x1 + x2) / 2
    val cy = sp * cxp + cp * cyp + (y1 + y2) / 2
    val t1 = pAtan2((yp - cyp) / ry, (xp - cxp) / rx)
    var dt = pAtan2((-yp - cyp) / ry, (-xp - cxp) / rx) - t1
    if (sweep && dt < 0) dt += 2 * Math.PI else if (!sweep && dt > 0) dt -= 2 * Math.PI
    val steps = max(2, ceil(abs(dt) / (Math.PI / 18)).toInt())
    val out = ArrayList<Pt>()
    for (i in 1..steps) {
        val (st, ct) = pSinCos(t1 + dt * i / steps)
        out += (cx + rx * ct * cp - ry * st * sp) to (cy + rx * ct * sp + ry * st * cp)
    }
    out[out.size - 1] = x2 to y2
    return out
}

private val PATH_CMD = Pattern.compile("[\\s,]*([MmLlHhVvCcSsQqTtAaZz])")
private val PATH_NUM = Pattern.compile("[\\s,]*([-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?)")
private val PATH_FLAG = Pattern.compile("[\\s,]*([01])")
private val PATH_ARGS = mapOf('M' to 2, 'L' to 2, 'H' to 1, 'V' to 1, 'C' to 6, 'S' to 4, 'Q' to 4, 'T' to 2, 'A' to 7)

// render-icon.py path_subpaths(): a path's subpaths, flattened (curves in 16 steps, arcs every 10 degrees).
fun pathSubpaths(d0: String?): List<List<Pt>> {
    val d = d0 ?: ""
    var pos = 0
    var cmd = ' '
    var cur: ArrayList<Pt>? = null
    var x = 0.0
    var y = 0.0
    var sx = 0.0
    var sy = 0.0
    var cx2: Double? = null
    var cy2 = 0.0
    var qx: Double? = null
    var qy = 0.0
    val subs = ArrayList<List<Pt>>()
    fun at(p: Pattern): java.util.regex.Matcher? {
        val m = p.matcher(d).region(pos, d.length)
        return if (m.lookingAt()) m else null
    }
    fun bezier(p: List<Pt>) {
        for (j in 1..16) {
            val t = j / 16.0
            val u = 1 - t
            val w0 = u * u * u
            val w1 = 3 * u * u * t
            val w2 = 3 * u * t * t
            val w3 = t * t * t
            cur!! += (w0 * p[0].first + w1 * p[1].first + w2 * p[2].first + w3 * p[3].first) to
                (w0 * p[0].second + w1 * p[1].second + w2 * p[2].second + w3 * p[3].second)
        }
    }
    while (true) {
        val cm = at(PATH_CMD)
        if (cm != null) {
            cmd = cm.group(1)[0]
            pos = cm.end()
        } else if (cmd == ' ' || at(PATH_NUM) == null) break
        val rel = cmd.isLowerCase()
        val c = cmd.uppercaseChar()
        if (c == 'Z') {
            cur?.let { subs += it }
            cur = null
            x = sx
            y = sy
            cmd = ' '
            cx2 = null
            qx = null
            continue
        }
        val need = PATH_ARGS.getValue(c)
        val v = ArrayList<Double>()
        for (k in 0 until need) {
            val m = at(if (c == 'A' && (k == 3 || k == 4)) PATH_FLAG else PATH_NUM) ?: break
            v += m.group(1).toDouble()
            pos = m.end()
        }
        if (v.size < need) break
        val ox = if (rel) x else 0.0
        val oy = if (rel) y else 0.0
        if (c == 'M') {
            cur?.let { subs += it }
            x = v[0] + ox
            y = v[1] + oy
            sx = x
            sy = y
            cur = arrayListOf(x to y)
            cmd = if (rel) 'l' else 'L'
            cx2 = null
            qx = null
            continue
        }
        if (cur == null) {
            cur = arrayListOf(x to y)
            sx = x
            sy = y
        }
        when (c) {
            'L', 'H', 'V' -> {
                if (c != 'V') x = v[0] + ox
                if (c != 'H') y = v[v.size - 1] + oy
                cur!! += x to y
                cx2 = null
                qx = null
            }
            'C', 'S' -> {
                val p1: Pt
                val p2: Pt
                val p3: Pt
                if (c == 'C') {
                    p1 = (v[0] + ox) to (v[1] + oy); p2 = (v[2] + ox) to (v[3] + oy); p3 = (v[4] + ox) to (v[5] + oy)
                } else {
                    p1 = cx2?.let { (2 * x - it) to (2 * y - cy2) } ?: (x to y)
                    p2 = (v[0] + ox) to (v[1] + oy); p3 = (v[2] + ox) to (v[3] + oy)
                }
                bezier(listOf(x to y, p1, p2, p3))
                cx2 = p2.first; cy2 = p2.second; x = p3.first; y = p3.second
                qx = null
            }
            'Q', 'T' -> {
                val q1: Pt
                val p3: Pt
                if (c == 'Q') {
                    q1 = (v[0] + ox) to (v[1] + oy); p3 = (v[2] + ox) to (v[3] + oy)
                } else {
                    q1 = qx?.let { (2 * x - it) to (2 * y - qy) } ?: (x to y)
                    p3 = (v[0] + ox) to (v[1] + oy)
                }
                bezier(listOf(x to y, (x + 2.0 / 3 * (q1.first - x)) to (y + 2.0 / 3 * (q1.second - y)),
                    (p3.first + 2.0 / 3 * (q1.first - p3.first)) to (p3.second + 2.0 / 3 * (q1.second - p3.second)), p3))
                qx = q1.first; qy = q1.second; x = p3.first; y = p3.second
                cx2 = null
            }
            else -> {
                val nx = v[5] + ox
                val ny = v[6] + oy
                cur!! += arcPoints(x, y, v[0], v[1], v[2], v[3] != 0.0, v[4] != 0.0, nx, ny)
                x = nx; y = ny
                cx2 = null; qx = null
            }
        }
    }
    cur?.let { subs += it }
    return subs
}

private fun pyNum(v: Double): String = v.toString()  // read back by pathSubpaths only: any spelling of the value does

// render-icon.py shape_d(): a basic shape's outline as path data.
internal fun shapeD(name0: String, attrs: String): String? {
    var name = name0
    fun g(k: String) = getAttr(attrs, k)?.takeIf { it.isNotEmpty() }?.toDouble() ?: 0.0
    var rx = 0.0
    var ry = 0.0
    if (name == "circle") { name = "ellipse"; rx = g("r"); ry = g("r") } else if (name == "ellipse") { rx = g("rx"); ry = g("ry") }
    if (name == "ellipse") {
        val cx = g("cx")
        val cy = g("cy")
        return "M${pyNum(cx - rx)} ${pyNum(cy)}A${pyNum(rx)} ${pyNum(ry)} 0 1 0 ${pyNum(cx + rx)} ${pyNum(cy)}" +
            "A${pyNum(rx)} ${pyNum(ry)} 0 1 0 ${pyNum(cx - rx)} ${pyNum(cy)}Z"
    }
    if (name == "rect") {
        val x = g("x"); val y = g("y"); val w = g("width"); val h = g("height")
        val ax = getAttr(attrs, "rx"); val ay = getAttr(attrs, "ry")
        rx = (ax ?: ay?.takeIf { it.isNotEmpty() } ?: "0").ifEmpty { "0" }.toDouble()
        ry = (ay ?: ax?.takeIf { it.isNotEmpty() } ?: "0").ifEmpty { "0" }.toDouble()
        rx = min(rx, w / 2); ry = min(ry, h / 2)
        if (rx == 0.0 || ry == 0.0) return "M${pyNum(x)} ${pyNum(y)}H${pyNum(x + w)}V${pyNum(y + h)}H${pyNum(x)}Z"
        return "M${pyNum(x + rx)} ${pyNum(y)}H${pyNum(x + w - rx)}A${pyNum(rx)} ${pyNum(ry)} 0 0 1 ${pyNum(x + w)} ${pyNum(y + ry)}" +
            "V${pyNum(y + h - ry)}A${pyNum(rx)} ${pyNum(ry)} 0 0 1 ${pyNum(x + w - rx)} ${pyNum(y + h)}" +
            "H${pyNum(x + rx)}A${pyNum(rx)} ${pyNum(ry)} 0 0 1 ${pyNum(x)} ${pyNum(y + h - ry)}V${pyNum(y + ry)}" +
            "A${pyNum(rx)} ${pyNum(ry)} 0 0 1 ${pyNum(x + rx)} ${pyNum(y)}Z"
    }
    if (name == "polygon") {
        val v = NUMBER.findAll(getAttr(attrs, "points") ?: "").map { it.value }.toList()
        return if (v.size >= 4) "M" + v.joinToString(" ") + "Z" else null
    }
    return null
}

private fun presentation(attrs: String, name: String): String? {
    val m = Regex("(?:^|;)\\s*$name\\s*:\\s*([^;]+)").find(getAttr(attrs, "style") ?: "")
    return m?.groupValues?.get(1)?.trim() ?: getAttr(attrs, name)
}

// render-icon.py emblem_shapes(): the filled shapes in the emblem color as polygons, those in its second tone apart, and
// the text with all of them marked.
private fun emblemShapes(text: String): Triple<String, List<List<Pt>>, List<List<Pt>>> {
    class F(val fill: String, val stroke: String, val mat: List<Double>, val hidden: Boolean, val emblem: Boolean, val other: Boolean)
    val stack = ArrayDeque(listOf(F("black", "none", IDENTITY, false, false, false)))
    val polys = ArrayList<List<Pt>>()
    val polys2 = ArrayList<List<Pt>>()
    val marked = TAG.replace(text) { m ->
        val (closing, name, attrs, empty) = m.destructured
        if (closing.isNotEmpty()) {
            if (stack.size > 1) stack.removeLast()
            return@replace m.value
        }
        val top = stack.last()
        val fill = presentation(attrs, "fill") ?: top.fill
        val stroke = presentation(attrs, "stroke") ?: top.stroke
        val mat = matMul(top.mat, transformMatrix(getAttr(attrs, "transform")))
        val hidden = top.hidden || name in UNDRAWN_PARTS
        val cls = (getAttr(attrs, "class") ?: "").trim().split(Regex("\\s+"))
        val emblem = top.emblem || "ColorScheme-Emblem" in cls
        val other = top.other || "ColorScheme-Emblem2" in cls || "data-tone=\"2\"" in attrs
        if (empty.isEmpty()) stack.addLast(F(fill, stroke, mat, hidden, emblem, other))
        if (hidden || !emblem || name !in DRAWN_SHAPES || fill != "currentColor" || stroke !in setOf("none", "transparent")) {
            return@replace m.value
        }
        val d = if (name == "path") getAttr(attrs, "d") else shapeD(name, attrs)
        val (a, b, c, dd, e) = mat
        val f = mat[5]
        for (sub in pathSubpaths(d)) (if (other) polys2 else polys) += sub.map { (px, py) -> (a * px + c * py + e) to (b * px + dd * py + f) }
        val trimmed = attrs.trimEnd()
        val tail = attrs.substring(trimmed.length)
        val old = getAttr(trimmed, "class")
        "<$name${setAttr(trimmed, "class", (if (old != null) "$old " else "") + "morph-main")}$tail$empty>"
    }
    return Triple(marked, polys, polys2)
}

private fun polyArea(p: List<Pt>): Double {
    var s = 0.0
    for (i in p.indices) {
        val q = p[(i + 1) % p.size]
        s += p[i].first * q.second - q.first * p[i].second
    }
    return s / 2
}

private fun resampled(p: List<Pt>, n: Int = MORPH_POINTS): List<Pt> {
    val pts = p + listOf(p[0])
    val lens = ArrayList<Double>()
    for (i in 0 until pts.size - 1) {
        val dx = pts[i + 1].first - pts[i].first
        val dy = pts[i + 1].second - pts[i].second
        lens += sqrt(dx * dx + dy * dy)
    }
    var total = 0.0
    for (l in lens) total += l
    if (total == 0.0) return List(n) { p[0] }
    val out = ArrayList<Pt>()
    var k = 0
    var walked = 0.0
    for (j in 0 until n) {
        val s = total * j / n
        while (k < lens.size - 1 && walked + lens[k] < s) {
            walked += lens[k]
            k++
        }
        val t = if (lens[k] != 0.0) (s - walked) / lens[k] else 0.0
        out += (pts[k].first + (pts[k + 1].first - pts[k].first) * t) to (pts[k].second + (pts[k + 1].second - pts[k].second) * t)
    }
    return out
}

private fun centroid(p: List<Pt>): Pt {
    var x = 0.0
    var y = 0.0
    for (q in p) { x += q.first; y += q.second }
    return (x / p.size) to (y / p.size)
}

private fun aligned(a: List<Pt>, b: List<Pt>): List<Pt> {
    val n = a.size
    var best: Double? = null
    var shift = 0
    for (k in 0 until n) {
        var cost = 0.0
        for (i in 0 until n) {
            val dx = a[i].first - b[(i + k) % n].first
            val dy = a[i].second - b[(i + k) % n].second
            cost += dx * dx + dy * dy
        }
        if (best == null || cost < best - 1e-9 * (best + 1)) {
            best = cost
            shift = k
        }
    }
    return b.subList(shift, n) + b.subList(0, shift)
}

// alone: a second tone, which may be on one side only; its shapes then grow or shrink.
private fun morphPairs(pa0: List<List<Pt>>, pb0: List<List<Pt>>, alone: Boolean = false): List<Pair<List<Pt>, List<Pt>>> {
    val pa = pa0.filter { it.size > 2 && abs(polyArea(it)) > 1e-9 }
    var pb = pb0.filter { it.size > 2 && abs(polyArea(it)) > 1e-9 }
    if ((pa.isEmpty() && pb.isEmpty()) || (!alone && (pa.isEmpty() || pb.isEmpty()))) return emptyList()
    fun bySize(ps: List<List<Pt>>): List<List<Pt>> {
        if (ps.isEmpty()) return ps
        var top = 0.0
        for (p in ps) top = max(top, abs(polyArea(p)))
        return ps.withIndex().sortedWith(compareBy({ -floor(abs(polyArea(it.value)) / top * 1e6 + 0.5) }, { it.index })).map { it.value }
    }
    if (pa.isNotEmpty() && pb.isNotEmpty() && (polyArea(bySize(pa)[0]) > 0) != (polyArea(bySize(pb)[0]) > 0)) pb = pb.map { it.reversed() }
    val pairs = ArrayList<Pair<List<Pt>, List<Pt>>>()
    for (sign in listOf(true, false)) {
        val sa = bySize(pa.filter { (polyArea(it) > 0) == sign })
        val sb = bySize(pb.filter { (polyArea(it) > 0) == sign })
        for (i in 0 until max(sa.size, sb.size)) {
            val a = if (i < sa.size) resampled(sa[i]) else List(MORPH_POINTS) { centroid(sb[i]) }
            val b = if (i < sb.size) resampled(sb[i]) else List(MORPH_POINTS) { centroid(sa[i]) }
            pairs += a to aligned(a, b)
        }
    }
    return pairs
}

private fun morphD(polys: List<List<Pt>>, places: Int) =
    polys.joinToString("") { p -> "M" + p.joinToString("L") { "${pyFixed(it.first, places)} ${pyFixed(it.second, places)}" } + "Z" }

private fun emblemColors(text: String, part: String = "Emblem"): Pair<String, String>? {
    val css = Regex("<style id=\"current-color-scheme\"[^>]*>([\\s\\S]*?)</style>").find(text)?.groupValues?.get(1) ?: ""
    val media = Regex("@media \\(prefers-color-scheme: light\\)\\{([\\s\\S]*)\\}").find(css)
    val base = if (media != null) css.substring(0, media.range.first) else css
    val rule = Regex("\\.ColorScheme-$part\\{color:(#[0-9a-fA-F]+)\\}")
    val dark = rule.find(base)?.groupValues?.get(1) ?: return null
    val light = media?.let { rule.find(it.groupValues[1])?.groupValues?.get(1) }
    return dark to (light ?: dark)
}

private fun layoutParts(text: String): List<Pair<String?, String>> {
    val found = Regex("\\n  <g class=\"(Size\\w+)\">([\\s\\S]*?)\\n  </g>").findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
    if (found.isNotEmpty()) return found
    val head = Regex("<svg\\b[^>]*>").find(text)
    val body = if (head != null) text.substring(head.range.last + 1) else text
    val defs = body.indexOf("</defs>")
    val lo = if (defs >= 0) defs + "</defs>".length else 0
    val hi = body.lastIndexOf("</svg>")
    return listOf(null to body.substring(lo, if (hi >= lo) hi else body.length))
}

private fun renamedTarget(text0: String, ids: List<String>, suffix: String): String {
    var text = text0
    for (i in ids) text = text.replace("id=\"$i\"", "id=\"$i$suffix\"").replace("url(#$i)", "url(#$i$suffix)")
    return Regex("\\bColorScheme-(\\w+)").replace(text) { "ColorScheme$suffix-${it.groupValues[1]}" }
}

private fun opacityFrames(name: String, points: List<Pair<Double, Int>>) =
    "@keyframes $name{" + points.joinToString("") { "${pyFixed(100 * it.first, 2)}%{opacity:${it.second}}" } + "}"

private fun viewBoxOf(text: String) =
    Regex("<svg\\b[^>]*\\sviewBox=\"([^\"]*)\"").find(text)?.groupValues?.get(1)?.replace(",", " ")?.trim()
        ?.split(Regex("\\s+"))?.filter(String::isNotEmpty)?.map(String::toDouble) ?: emptyList()

// render-icon.py morph_svg(): the icon turning into the target, once (ending on it) or there and back, forever.
fun morphSvg(svgText: String, targetText: String, duration: Double, loop: Boolean): String {
    val aText = visibleLayouts(svgText)
    val bText = visibleLayouts(targetText)
    val head = Regex("<svg\\b[^>]*>").find(aText) ?: return svgText
    val end = head.range.last + 1
    val nums = viewBoxOf(aText)
    val nb = viewBoxOf(bText)
    val side = if (nums.size == 4) max(nums[2], nums[3]) else 24.0
    val places = max(0, ceil(-log10(side / 10000)).toInt())
    val la = layoutParts(aText)
    val lb = layoutParts(bText)
    var fit = IDENTITY
    if (nums.size == 4 && nb.size == 4 && nums != nb && nb[2] != 0.0 && nb[3] != 0.0) {
        val k = min(nums[2] / nb[2], nums[3] / nb[3])
        fit = listOf(k, 0.0, 0.0, k, nums[0] + (nums[2] - k * nb[2]) / 2 - k * nb[0], nums[1] + (nums[3] - k * nb[3]) / 2 - k * nb[1])
    }
    val hold = if (loop) 0.3 else 0.25
    val keys = if (loop) listOf(0.0, hold / 2, 0.5, 0.5 + hold / 2, 1.0) else listOf(0.0, hold, 1.0)
    val sides = if (loop) "aabba" else "aab"
    val eps = 0.0001
    // The icons' cross-fade: through each change the incoming side comes in by its middle while the outgoing one stays,
    // and the outgoing one goes after it, so where both draw (the logo) nothing dims.
    fun faded(side: Char) = keys.indices.flatMap { i ->
        listOf(keys[i] to if (sides[i] == side) 1 else 0) +
            if (i + 1 < keys.size && sides[i + 1] != sides[i]) listOf((keys[i] + keys[i + 1]) / 2 to 1) else emptyList()
    }
    val aFrames = faded('a')
    val bFrames = faded('b')
    // Opacity points from a value per stretch between keys and one for the last key: at each change the incoming
    // shape is fully shown by the key and the outgoing one fades just after it; at the last key, where the timeline
    // ends, they cross just before it.
    fun steps(vals: List<Int>, end: Int): List<Pair<Double, Int>> {
        val pts = arrayListOf(keys[0] to vals[0])
        for (i in 1 until keys.size) {
            val u = vals[i - 1]
            val v = if (i < vals.size) vals[i] else end
            val k = keys[i]
            when {
                u == v -> pts += k to v
                i == vals.size || u == 0 -> { pts += (k - eps) to u; pts += k to v }
                else -> { pts += k to u; pts += (k + eps) to v }
            }
        }
        return pts
    }
    // A side's emblem shows while that side holds; the morph path, while it moves (and draws the emblems).
    val stretch = 0 until keys.size - 1
    val (aMain, bMain) = listOf('a', 'b').map { s -> steps(stretch.map { i -> if (sides[i] == s && sides[i + 1] == s) 1 else 0 }, if (sides.last() == s) 1 else 0) }
    val moving = steps(stretch.map { i -> if (sides[i] != sides[i + 1]) 1 else 0 }, 0)
    val bIds = Regex("\\sid=\"([^\"]+)\"").findAll(bText).map { it.groupValues[1] }.toList()
    fun lookup(cls: String?) = (lb.firstOrNull { it.first == cls && it.second.isNotEmpty() }
        ?: lb.firstOrNull { it.first == "SizeFull" && it.second.isNotEmpty() } ?: lb.firstOrNull() ?: (null to "")).second
    val out = StringBuilder()
    var toned = false
    // The path that draws the emblems' shapes in one tone while they move.
    fun morphPath(cls: String, pairs: List<Pair<List<Pt>, List<Pt>>>): String {
        if (pairs.isEmpty()) return ""
        val da = morphD(pairs.map { it.first }, places)
        val db = morphD(pairs.map { it.second }, places)
        val values = sides.map { if (it == 'a') da else db }
        val splines = (0 until keys.size - 1).joinToString(";") { if (it % 2 == 0) "0 0 1 1" else MORPH_EASE }
        return "<path class=\"$cls\" fill=\"currentColor\" d=\"$da\"><animate attributeName=\"d\" " +
            "dur=\"${fmtG(duration)}s\" repeatCount=\"${if (loop) "indefinite" else "1"}\" fill=\"freeze\" calcMode=\"spline\" " +
            "keyTimes=\"${keys.joinToString(";") { fmtG(it) }}\" keySplines=\"$splines\" values=\"${values.joinToString(";")}\"/></path>"
    }
    for ((cls, aInner) in la) {
        var bInner = lookup(cls)
        val (aMarked0, polysA, polys2A) = emblemShapes(aInner)
        val (bMarked0, polysB0, polys2B0) = emblemShapes(bInner)
        val (fa, fb, fc, fd, fe) = fit
        val ff = fit[5]
        val fitted = { ps: List<List<Pt>> -> ps.map { p -> p.map { (x, y) -> (fa * x + fc * y + fe) to (fb * x + fd * y + ff) } } }
        val polysB = fitted(polysB0)
        val polys2B = fitted(polys2B0)
        var aMarked = aMarked0
        var bMarked = renamedTarget(bMarked0, bIds, "-to")
        bInner = renamedTarget(bInner, bIds, "-to")
        val pairs = morphPairs(polysA, polysB)
        // The second tone morphs only beside the first: without that, the emblems cross-fade whole.
        val pairs2 = if (pairs.isNotEmpty()) morphPairs(polys2A, polys2B, alone = true) else emptyList()
        toned = toned || pairs2.isNotEmpty()
        var path = ""
        if (pairs.isNotEmpty()) {
            // the second tone under the first, as the emblems draw it (gitea's teabag shows through the cup)
            path = morphPath("morph-path2", pairs2) + morphPath("morph-path", pairs)
        } else {
            aMarked = aInner
            bMarked = bInner
        }
        val to = if (fit == IDENTITY) "" else " transform=\"matrix(${fit.joinToString(" ") { fmtG(it, 6) }})\""
        val body = "<g class=\"morph-a\">$aMarked</g><g class=\"morph-b\"$to>$bMarked</g>$path"
        out.append(if (cls != null) "\n  <g class=\"$cls\">$body\n  </g>" else "\n  $body\n")
    }
    val bDefsM = Regex("<defs>([\\s\\S]*?)</defs>").find(bText)
    val bDefs = bDefsM?.let { renamedTarget(it.groupValues[1].replace(Regex("\\s*<style\\b[^>]*>[\\s\\S]*?</style>"), ""), bIds, "-to").trim() } ?: ""
    val bCss = Regex("<style id=\"current-color-scheme\"[^>]*>([\\s\\S]*?)</style>").find(bText)?.let { renamedTarget(it.groupValues[1].trim(), bIds, "-to") } ?: ""
    val ca0 = emblemColors(aText)
    val cb0 = emblemColors(bText)
    val ca = ca0 ?: cb0 ?: ("#000000" to "#000000")
    val cb = cb0 ?: ca0 ?: ("#000000" to "#000000")
    // The second tone's: a side without one takes the other's, as its shapes come from or go to a point.
    val c2a0 = emblemColors(aText, "Emblem2")
    val c2b0 = emblemColors(bText, "Emblem2")
    val c2a = c2a0 ?: c2b0 ?: ca
    val c2b = c2b0 ?: c2a0 ?: cb
    val count = if (loop) "infinite" else "1"
    val dur = fmtG(duration)
    fun color(w: String, n: String = "", a: Pair<String, String> = ca, b: Pair<String, String> = cb) =
        "@keyframes morph-color$n-$w{" + keys.mapIndexed { i, k ->
            val c = if (sides[i] == 'a') a else b
            "${pyFixed(100 * k, 2)}%{color:${if (w == "light") c.second else c.first}}"
        }.joinToString("") + "}"
    fun two(t: String) = if (toned) t else ""  // what only a second tone adds
    val css = "$bCss.morph-b,.morph-path${two(",.morph-path2")}{opacity:0}" +
        ".morph-a{animation:morph-a ${dur}s linear $count both}" +
        ".morph-b{animation:morph-b ${dur}s linear $count both}" +
        ".morph-a .morph-main{animation:morph-a-main ${dur}s linear $count both}" +
        ".morph-b .morph-main{animation:morph-b-main ${dur}s linear $count both}" +
        ".morph-path{color:${ca.first};animation:morph-path ${dur}s linear $count both," +
        "morph-color-dark ${dur}s linear $count both}" +
        opacityFrames("morph-a", aFrames) + opacityFrames("morph-b", bFrames) + opacityFrames("morph-a-main", aMain) +
        opacityFrames("morph-b-main", bMain) + opacityFrames("morph-path", moving) + color("dark") + color("light") +
        two(".morph-path2{color:${c2a.first};animation:morph-path ${dur}s linear $count both," +
            "morph-color2-dark ${dur}s linear $count both}" + color("dark", "2", c2a, c2b) + color("light", "2", c2a, c2b)) +
        "@media (prefers-color-scheme: light){.morph-path{animation-name:morph-path,morph-color-light}" +
        two(".morph-path2{animation-name:morph-path,morph-color2-light}") + "}" +
        "@media (prefers-reduced-motion: reduce){.morph-a,.morph-b,.morph-main,.morph-path" + two(",.morph-path2") + "{animation:none}}"
    val text = aText.substring(0, end) + "\n  <style id=\"animation\" type=\"text/css\">$css</style>"
    var rest = aText.substring(end)
    var defsEnd = rest.indexOf("</defs>")
    if (bDefs.isNotEmpty()) {
        rest = if (defsEnd >= 0) rest.substring(0, defsEnd) + "  $bDefs\n  " + rest.substring(defsEnd) else "\n  <defs>$bDefs</defs>$rest"
        defsEnd = rest.indexOf("</defs>")
    }
    val lo = if (defsEnd >= 0) defsEnd + "</defs>".length else 0
    return text + rest.substring(0, lo) + out + "\n</svg>\n"
}
