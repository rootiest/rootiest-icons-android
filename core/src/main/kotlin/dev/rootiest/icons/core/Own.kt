package dev.rootiest.icons.core

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

// SVG files made ready to be an emblem or a logo: render-icon.py's own_text() and the generator's clean() and
// load_emblem(), so a file of your own is treated here exactly as `render-rootiest-icons star.svg` treats it.

// A file that cannot be used, with the reason to show.
class UnusableSvg(message: String) : Exception(message)

private val KEEPABLE = setOf("path", "g", "circle", "ellipse", "rect", "polygon", "polyline", "line")
private val TAG = Regex("<([a-zA-Z][\\w:-]*)(\\s[^<>]*?)?(/?)>")

private fun stripSemi(t: String) = t.trim(';')

internal fun getAttr(attrs: String, name: String): String? {
    val m = Regex("\\s$name=(\"([^\"]*)\"|'([^']*)')").find(attrs) ?: return null
    return m.groups[2]?.value ?: m.groups[3]?.value
}

internal fun setAttr(attrs: String, name: String, value: String): String {
    val m = Regex("(\\s$name=)(\"[^\"]*\"|'[^']*')").find(attrs) ?: return "$attrs $name=\"$value\""
    return attrs.replaceRange(m.range, "${m.groupValues[1]}\"$value\"")
}

// {class: declarations} from the SVG's <style> blocks: plain .class rules only, as design tools write them.
private fun classRules(raw: String): Map<String, String> {
    val rules = LinkedHashMap<String, String>()
    for (block in Regex("<style[^>]*>([\\s\\S]*?)</style>").findAll(raw)) {
        val css = block.groupValues[1].replace(Regex("/\\*[\\s\\S]*?\\*/|<!\\[CDATA\\[|\\]\\]>"), "")
        for (rule in Regex("([^{}]+)\\{([^{}]*)\\}").findAll(css)) {
            for (one in rule.groupValues[1].split(",")) {
                val m = Regex("^\\s*\\.([\\w-]+)\\s*$").find(one) ?: continue
                rules[m.groupValues[1]] = stripSemi((rules[m.groupValues[1]] ?: "") + ";" + rule.groupValues[2].trim())
            }
        }
    }
    return rules
}

// render-icon.py own_text(): class rules inlined; then, recolored, strokes as currentColor, or kept, style fills and
// strokes as attributes and drawable elements marked data-keep-fill.
fun ownText(raw: String, keep: Boolean): String {
    val rules = classRules(raw)
    return TAG.replace(raw.replace(Regex("<!--[\\s\\S]*?-->"), "")) { m ->
        val name = m.groupValues[1]
        var attrs = m.groupValues[2]
        if (name.substringAfterLast(':') in setOf("svg", "style")) return@replace m.value
        val decls = (getAttr(attrs, "class") ?: "").split(Regex("\\s+")).filter { it in rules }.joinToString(";") { rules.getValue(it) }
        if (decls.isNotEmpty()) attrs = setAttr(attrs, "style", stripSemi(decls + ";" + (getAttr(attrs, "style") ?: "")))
        var style = getAttr(attrs, "style")
        if (keep) {
            if (!style.isNullOrEmpty()) {
                for (prop in listOf("fill", "stroke")) {
                    val pm = Regex("(?:^|;)\\s*$prop\\s*:\\s*([^;]+)").find(style!!) ?: continue
                    attrs = setAttr(attrs, prop, pm.groupValues[1].trim())
                    style = stripSemi(style.replace(Regex("(?:^|;)\\s*$prop\\s*:\\s*[^;]+"), ""))
                }
                attrs = if (style!!.isNotBlank()) setAttr(attrs, "style", style) else attrs.replaceFirst(Regex("\\sstyle=(\"[^\"]*\"|'[^']*')"), "")
            }
            if (name in KEEPABLE) attrs += " data-keep-fill=\"\""
        } else {
            val stroke = getAttr(attrs, "stroke")
            if (stroke != null && stroke.trim() !in setOf("none", "currentColor")) attrs = setAttr(attrs, "stroke", "currentColor")
            if (!style.isNullOrEmpty()) {
                attrs = setAttr(attrs, "style", Regex("(^|;)(\\s*stroke\\s*:\\s*)(?!none\\b)[^;]+").replace(style) {
                    it.groupValues[1] + it.groupValues[2] + "currentColor"
                })
            }
        }
        "<$name$attrs${m.groupValues[3]}>"
    }
}

// An SVG's viewBox and its drawable content, cleaned.
data class Cleaned(val viewBox: String, val body: String)

private fun escAttr(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    .replace("\r", "&#13;").replace("\n", "&#10;").replace("\t", "&#09;")

// The generator's load_emblem() and clean(): drawable elements only, no ids, classes or foreign attributes; fills
// become currentColor, except on an element marked data-keep-fill (keepAll: on every element).
fun cleanSvg(raw: String, name: String, keepAll: Boolean = false): Cleaned {
    if (Regex("url\\(#|xlink:href|\\shref=").containsMatchIn(raw)) {
        throw UnusableSvg("$name uses gradients, masks or other references (url(#…), href); save it with flat colors")
    }
    var viewBox: String? = null
    var isSvg = false
    val out = StringBuilder()
    // one entry per open element: whether it was written, and whether it has had a child written
    class Open(val drawn: Boolean, var kids: Boolean = false)
    val stack = ArrayList<Open>()
    val handler = object : DefaultHandler() {
        override fun resolveEntity(publicId: String?, systemId: String?) = InputSource(StringReader("")) // no DTD fetched

        override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
            val tag = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')
            if (stack.isEmpty()) {
                isSvg = tag == "svg"
                viewBox = a.getValue("viewBox") ?: "0 0 ${a.getValue("width") ?: ""} ${a.getValue("height") ?: ""}"
                stack.add(Open(true))
                return
            }
            val parent = stack.last()
            if (!parent.drawn || tag !in KEEPABLE) {
                stack.add(Open(false))
                return
            }
            if (stack.size > 1 && !parent.kids) out.append('>')
            parent.kids = true
            val keep = keepAll || a.getValue("data-keep-fill") != null
            out.append('<').append(tag)
            if (a.getValue("data-tone") == "2") out.append(" class=\"ColorScheme-Emblem2\"")
            for (i in 0 until a.length) {
                val q = a.getQName(i)
                if (':' in q || q in setOf("id", "class", "data-tone", "data-keep-fill")) continue
                var v = a.getValue(i)
                if (q == "fill" && v != "none" && v != "currentColor" && !keep) v = "currentColor"
                if (q == "d") v = v.replace(Regex("(\\d)\\.(?![0-9])"), "$1")
                if (q == "style") {
                    v = v.replace(Regex("fill\\s*:\\s*#[0-9a-fA-F]{3,8}"), "fill:currentColor")
                        .replace(Regex("(^|;)\\s*color\\s*:[^;]*"), "$1")
                }
                out.append(' ').append(q).append("=\"").append(escAttr(v)).append('"')
            }
            stack.add(Open(true))
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val el = stack.removeAt(stack.size - 1)
            if (!el.drawn || stack.isEmpty()) return
            val tag = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')
            if (el.kids) out.append("</").append(tag).append('>') else out.append(" />")
        }
    }
    try {
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        factory.newSAXParser().parse(InputSource(StringReader(raw.trimStart('\uFEFF'))), handler)
    } catch (e: UnusableSvg) {
        throw e
    } catch (e: Exception) {
        throw UnusableSvg("$name is not valid SVG")
    }
    if (!isSvg) throw UnusableSvg("$name is not an SVG")
    val nums = viewBox.orEmpty().trim().split(Regex("[\\s,]+"))
    if (nums.size != 4 || !nums.all { Regex("^-?[\\d.]+$").matches(it) }) throw UnusableSvg("$name has no usable viewBox or width and height")
    if (out.isEmpty()) throw UnusableSvg("$name has nothing drawable in it")
    return Cleaned(nums.joinToString(" "), out.toString())
}

// The viewBox a body is measured in: its own with a margin, so strokes at the edge are not clipped.
fun measureBox(viewBox: String): String {
    val (x, y, w, h) = viewBox.split(" ").map(String::toDouble)
    return listOf(x - w / 10, y - h / 10, w * 1.2, h * 1.2).joinToString(" ") { fmtG(it) }
}

// A file of your own, cleaned: its viewBox, its body, and the body all in currentColor when it keeps its colors.
data class OwnSvg(val viewBox: String, val body: String, val mono: String?)

// render-icon.py own_svg().
fun ownSvg(raw: String, name: String, keep: Boolean): OwnSvg {
    if (!Regex("<svg[\\s>]").containsMatchIn(raw)) throw UnusableSvg("$name is not an SVG")
    val first = cleanSvg(ownText(raw, keep), name)
    return OwnSvg(first.viewBox, first.body, if (keep) cleanSvg(ownText(raw, false), name).body else null)
}

// Whether an SVG draws in more than one color (then it is worth asking whether to keep them).
fun manyColors(raw: String): Boolean =
    Regex("(?:fill|stroke)\\s*(?:=\\s*[\"']|:\\s*)(#[0-9a-f]{3,8}|[a-z]+)\\b").findAll(raw.lowercase())
        .map { it.groupValues[1] }.filter { it !in setOf("none", "currentcolor", "transparent", "inherit") }.toSet().size > 1

// render-icon.py slug(): a file's name as a part of an output name: My Logo.svg is my-logo.
fun slug(name: String): String =
    name.substringAfterLast('/').replace(Regex("\\.[^.]*$"), "").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "custom" }

// render-icon.py placement_tag(): -badge, -scale-1.2, -x5, -y-3 (x and y in percent of the icon's width).
fun placementTag(preset: String, scale: Double?, x: Double, y: Double): String {
    val parts = ArrayList<String>()
    if (preset != "rootiest") parts.add(preset)
    if (scale != null) parts.add("scale-${fmtG(scale)}")
    if (x != 0.0) parts.add("x${fmtG(x)}")
    if (y != 0.0) parts.add("y${fmtG(y)}")
    return parts.joinToString("") { "-$it" }
}
