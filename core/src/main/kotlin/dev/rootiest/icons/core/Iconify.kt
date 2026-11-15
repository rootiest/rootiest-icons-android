package dev.rootiest.icons.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlin.math.ceil

// Icons from other sets, through the Iconify API's data endpoint (/<set>.json?icons=): render-icon.py's icon_data()
// and icon_svg(), which draw the SVG the API's /<set>/<icon>.svg would send.

private val TRANSFORMS = mapOf("rotate" to JsonPrimitive(0), "vFlip" to JsonPrimitive(false), "hFlip" to JsonPrimitive(false))
private val PROPS = listOf("left", "top", "width", "height", "rotate", "vFlip", "hFlip", "body", "hidden")

private fun flag(o: Map<String, JsonElement>, key: String) = (o[key] as? JsonPrimitive)?.booleanOrNull == true
private fun num(o: Map<String, JsonElement>, key: String) = (o[key] as? JsonPrimitive)?.doubleOrNull

// An alias's data on top of its parent's: flips cancel out, rotations add up (Iconify's mergeIconData).
private fun mergeIconData(parent: Map<String, JsonElement>, child: Map<String, JsonElement>): Map<String, JsonElement> {
    val out = LinkedHashMap<String, JsonElement>()
    if (flag(parent, "hFlip") != flag(child, "hFlip")) out["hFlip"] = JsonPrimitive(true)
    if (flag(parent, "vFlip") != flag(child, "vFlip")) out["vFlip"] = JsonPrimitive(true)
    val rotate = (((num(parent, "rotate") ?: 0.0) + (num(child, "rotate") ?: 0.0)).toInt() % 4 + 4) % 4
    if (rotate != 0) out["rotate"] = JsonPrimitive(rotate)
    for (key in PROPS) {
        if (key in TRANSFORMS) {
            if (key in parent && key !in out) out[key] = TRANSFORMS.getValue(key)
        } else if (key in child) out[key] = child.getValue(key)
        else if (key in parent) out[key] = parent.getValue(key)
    }
    return out
}

// The icon's data from the API's answer, an alias followed to its icon and the set's default size filled in; null
// when the set has no such icon.
fun iconData(data: JsonObject, icon: String): Map<String, JsonElement>? {
    val icons = data["icons"] as? JsonObject ?: JsonObject(emptyMap())
    val aliases = data["aliases"] as? JsonObject ?: JsonObject(emptyMap())
    val chain = arrayListOf(icon)
    var name = icon
    while (name !in icons) {
        name = ((aliases[name] as? JsonObject)?.get("parent") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        if (name in chain || chain.size > 36) return null
        chain.add(name)
    }
    var props: Map<String, JsonElement> = emptyMap()
    for (n in chain) props = mergeIconData((icons[n] ?: aliases.getValue(n)).jsonObject, props)
    props = mergeIconData(data, props)
    return props.takeIf { (it["body"] as? JsonPrimitive)?.isString == true }
}

// The SVG the API's /<set>/<icon>.svg would send for that data (Iconify's iconToSVG).
fun iconSvg(d: Map<String, JsonElement>): String {
    var left = num(d, "left") ?: 0.0
    var top = num(d, "top") ?: 0.0
    var width = num(d, "width") ?: 16.0
    var height = num(d, "height") ?: 16.0
    var body = (d.getValue("body") as JsonPrimitive).content
    var rotation = (num(d, "rotate") ?: 0.0).toInt()
    val transforms = ArrayList<String>()
    if (flag(d, "hFlip")) {
        if (flag(d, "vFlip")) rotation += 2
        else {
            transforms += listOf("translate(${jsNum(width + left)} ${jsNum(0 - top)})", "scale(-1 1)")
            top = 0.0; left = 0.0
        }
    } else if (flag(d, "vFlip")) {
        transforms += listOf("translate(${jsNum(0 - left)} ${jsNum(height + top)})", "scale(1 -1)")
        top = 0.0; left = 0.0
    }
    rotation = (rotation % 4 + 4) % 4
    when (rotation) {
        1 -> (height / 2 + top).let { transforms.add(0, "rotate(90 ${jsNum(it)} ${jsNum(it)})") }
        2 -> transforms.add(0, "rotate(180 ${jsNum(width / 2 + left)} ${jsNum(height / 2 + top)})")
        3 -> (width / 2 + left).let { transforms.add(0, "rotate(-90 ${jsNum(it)} ${jsNum(it)})") }
    }
    if (rotation % 2 == 1) {
        val (l, t, w, h) = listOf(top, left, height, width)
        left = l; top = t; width = w; height = h
    }
    if (transforms.isNotEmpty()) body = "<g transform=\"${transforms.joinToString(" ")}\">$body</g>"
    return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${jsNum(ceil(1 * (width / height) * 100) / 100)}em\" height=\"1em\"" +
        " viewBox=\"${jsNum(left)} ${jsNum(top)} ${jsNum(width)} ${jsNum(height)}\">$body</svg>"
}

private val SET_NAME = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

// A name from another set: whether it goes on the logo (rootiest:), the set and the icon.
data class ExternalName(val whole: Boolean, val prefix: String, val icon: String) {
    val key get() = "$prefix:$icon"
}

// render-icon.py external_name(): a style after the set's name moves to the icon's, as Iconify names it:
// tabler-filled:star is tabler:star-filled. sets is every set's prefix (empty: unknown, names are taken as given).
fun externalName(name: String, sets: Set<String>): ExternalName? {
    var parts = name.trim().split(":")
    val whole = parts.size == 3 && parts[0] == "rootiest"
    if (whole) parts = parts.drop(1)
    if (parts.size != 2 || !parts.all { SET_NAME.matches(it) }) return null
    var (prefix, icon) = parts
    if (sets.isNotEmpty() && prefix !in sets) {
        val at = prefix.indices.reversed().firstOrNull { prefix[it] == '-' && prefix.substring(0, it) in sets } ?: return null
        icon = "$icon-${prefix.substring(at + 1)}"
        prefix = prefix.substring(0, at)
    }
    return ExternalName(whole, prefix, icon)
}
