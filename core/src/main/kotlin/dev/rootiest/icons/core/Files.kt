package dev.rootiest.icons.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

// Rendering files: render-icon.py's names and layouts, and the ICO and zip containers.

// render-icon.py color_tag(): the colors part of a file name (dev, logo-dev-emblem-ff8800, ...).
fun colorTag(o: ColorOptions): String =
    listOf("" to o.color, "logo-" to o.logo, "emblem-" to o.emblem, "tone2-" to o.tone2)
        .mapNotNull { (label, spec) -> spec?.let { label + it.label.removePrefix("#") } }.joinToString("-")

// render-icon.py stem_of(): <icon>[-emblem]-<variant>[-<colors>][-padded].
fun stemOf(base: String, emblem: Boolean, variant: String, padded: Boolean, o: ColorOptions): String {
    val tag = colorTag(o)
    return "$base-${if (emblem) "emblem-" else ""}$variant${if (tag.isNotEmpty()) "-$tag" else ""}${if (padded) "-padded" else ""}"
}

// render-icon.py planned(): every file a render writes, in order. A layout is "auto", "full" or a small size.
fun plannedFiles(stem: String, layouts: List<String>, sizes: List<Int>, formats: List<String>): List<String> {
    val out = ArrayList<String>()
    for (lay in layouts) {
        val name = stem + if (lay == "auto") "" else "-layout-$lay"
        if ("svg" in formats) out.add("$name.svg")
        for (size in sizes) for (f in listOf("png", "webp")) if (f in formats) out.add("$name-${size}px.$f")
        if ("ico" in formats) out.add("$name.ico")
    }
    return out
}

// render-icon.py layouts_of(): the small layouts an icon SVG holds, largest first.
fun layoutsOf(text: String): List<Int> =
    Regex("class=\"Size(\\d+)\"").findAll(text).map { it.groupValues[1].toInt() }.toSet().sortedDescending()

// render-icon.py resolve(): the layout shown for a choice at a size: "full" or a small size.
fun resolveLayout(layout: String, size: Int, small: List<Int>): String {
    if (layout != "auto") return layout
    return small.filter { size <= it }.lastOrNull()?.toString() ?: "full"
}

// render-icon.py single_layout_svg(): one layout kept.
fun singleLayoutSvg(text: String, shown: String): String {
    val keep = if (shown == "full") "SizeFull" else "Size$shown"
    return text.replaceFirst(Regex("\\s*<style id=\"sizes\"[^>]*>[\\s\\S]*?</style>"), "")
        .replace(Regex("\\n  <g class=\"(Size\\w+)\">[\\s\\S]*?\\n  </g>")) { if (it.groupValues[1] == keep) it.value else "" }
}

// The SVG a raster is drawn from: one layout, and the dark-background shades (as the CLI's rasters have: librsvg
// ignores media queries).
fun rasterSvg(text: String, layout: String, size: Int, small: List<Int>): String {
    val one = if (small.isNotEmpty()) singleLayoutSvg(text, resolveLayout(layout, size, small)) else text
    return one.replaceFirst(Regex("@media \\(prefers-color-scheme: light\\)\\{(?:\\.[\\w-]+\\{[^}]*\\})*\\}"), "")
}

// A named file's bytes.
class NamedBytes(val name: String, val bytes: ByteArray)

// An .ico holding PNGs (size to bytes, each at most 256 px), as ImageMagick writes from PNGs.
fun icoBytes(images: List<Pair<Int, ByteArray>>): ByteArray {
    val head = 6 + 16 * images.size
    val out = ByteBuffer.allocate(head + images.sumOf { it.second.size }).order(ByteOrder.LITTLE_ENDIAN)
    out.putShort(0).putShort(1).putShort(images.size.toShort())
    var offset = head
    for ((size, bytes) in images) {
        val dim = (if (size >= 256) 0 else size).toByte()
        out.put(dim).put(dim).put(0).put(0).putShort(1).putShort(32).putInt(bytes.size).putInt(offset)
        offset += bytes.size
    }
    for ((_, bytes) in images) out.put(bytes)
    return out.array()
}

// A zip of files, stored (PNG, WebP and ICO are compressed already), dated 1980-01-01 so it is the same every time.
fun zipBytes(files: List<NamedBytes>): ByteArray {
    val out = ByteArrayOutputStream()
    val central = ByteArrayOutputStream()
    fun buf(n: Int) = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
    for (f in files) {
        val name = f.name.toByteArray(Charsets.UTF_8)
        val crc = CRC32().apply { update(f.bytes) }.value.toInt()
        val offset = out.size()
        out.write(buf(30).putInt(0x04034b50).putShort(20).putShort(0x0800).putShort(0).putShort(0).putShort(0x21)
            .putInt(crc).putInt(f.bytes.size).putInt(f.bytes.size).putShort(name.size.toShort()).putShort(0).array())
        out.write(name)
        out.write(f.bytes)
        central.write(buf(46).putInt(0x02014b50).putShort(20).putShort(20).putShort(0x0800).putShort(0).putShort(0).putShort(0x21)
            .putInt(crc).putInt(f.bytes.size).putInt(f.bytes.size).putShort(name.size.toShort()).putShort(0).putShort(0)
            .putShort(0).putShort(0).putInt(0).putInt(offset).array())
        central.write(name)
    }
    val start = out.size()
    out.write(central.toByteArray())
    out.write(buf(22).putInt(0x06054b50).putShort(0).putShort(0).putShort(files.size.toShort()).putShort(files.size.toShort())
        .putInt(central.size()).putInt(start).putShort(0).array())
    return out.toByteArray()
}
