package dev.rootiest.icons.core

import java.io.ByteArrayOutputStream

// An animated GIF, frame by frame, as the homepage's GifWriter writes one (site/site.js), and as ImageMagick's
// -layers Optimize does for render-rootiest-icons: losslessly, every frame showing exactly as it was drawn, but each
// stored as only the box of pixels that changed, those that did not left transparent to show the last frame through.
// A frame some of whose pixels the next one clears is wiped after it shows (disposal 2), so its box takes in those
// pixels too; otherwise it stays (disposal 1). A looping one's last frame is followed by its first. Each frame's
// colors are a palette of its own (up to 255, from its most common ones at 5 bits a channel), in a table just large
// enough, fully transparent in its last entry; a GIF has no partial transparency.
//
// Frames are ARGB pixels (as Android's Bitmap.getPixels gives them), w by h, each shown for n frames of delayCs
// hundredths of a second. Only the frame before is held.
class GifEncoder(private val delayCs: Int, private val once: Boolean) {
    private val out = ByteArrayOutputStream()
    private var screen: IntArray? = null  // what a decoder's canvas holds before the next frame written
    private var first: IntArray? = null
    private var pending: Pending? = null

    private class Pending(val px: IntArray, val w: Int, val h: Int, val n: Int)

    fun frame(argb: IntArray, w: Int, h: Int, n: Int) {
        // opaque as 0xffRRGGBB, transparent as 0
        val px = IntArray(w * h) { i -> val c = argb[i]; if ((c ushr 24) < 128) 0 else c or (0xff shl 24) }
        pending?.let { write(it, px) }
        if (first == null) first = px
        pending = Pending(px, w, h, n)
    }

    fun bytes(): ByteArray {
        // A looping one's last frame leads to its first again, drawn as on an empty canvas: it wipes what that leaves out.
        pending?.let { write(it, if (once || first === it.px) null else first) }
        pending = null
        return out.toByteArray() + byteArrayOf(0x3b)
    }

    private fun u16(n: Int) {
        out.write(n and 0xff)
        out.write(n shr 8 and 0xff)
    }

    // 5 bits a channel, as rrrrrgggggbbbbb
    private fun key(c: Int) = ((c shr 19 and 0x1f) shl 10) or ((c shr 11 and 0x1f) shl 5) or (c shr 3 and 0x1f)

    private fun rgb(k: Int) = intArrayOf((k shr 10 and 0x1f) * 255 / 31, (k shr 5 and 0x1f) * 255 / 31, (k and 0x1f) * 255 / 31)

    // One frame, knowing the next one's pixels (null after the last).
    private fun write(f: Pending, next: IntArray?) {
        val (px, w, h) = Triple(f.px, f.w, f.h)
        val canvas = screen ?: IntArray(w * h).also {
            screen = it
            out.write("GIF89a".toByteArray())
            u16(w); u16(h)
            out.write(0); out.write(0); out.write(0)  // no global color table
            if (!once) {  // NETSCAPE2.0: loop forever
                out.write(byteArrayOf(0x21, 0xff.toByte(), 11) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0))
            }
        }
        var x0 = w
        var y0 = h
        var x1 = -1
        var y1 = -1
        var clears = false
        val changed = BooleanArray(w * h)
        for (i in px.indices) {
            val c = px[i] != canvas[i]
            val wipe = next != null && px[i] != 0 && next[i] == 0
            if (c) changed[i] = true
            if (wipe) clears = true
            if (c || wipe) {
                val x = i % w
                val y = i / w
                if (x < x0) x0 = x
                if (x > x1) x1 = x
                if (y < y0) y0 = y
                if (y > y1) y1 = y
            }
        }
        if (x1 < 0) { x0 = 0; y0 = 0; x1 = 0; y1 = 0 }  // nothing changed: one transparent pixel
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        val counts = LinkedHashMap<Int, Int>()  // in the order first seen, as the page's Map, for the same ties
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * w + x
            if (changed[i] && px[i] != 0) { val k = key(px[i]); counts[k] = (counts[k] ?: 0) + 1 }
        }
        val pal = counts.entries.sortedByDescending { it.value }.take(255).map { rgb(it.key) }
        // the table: the colors and the transparent one after them, in the fewest bits (2 to 8) that hold them all
        var bits = 2
        while ((1 shl bits) < pal.size + 1) bits++
        val clear = (1 shl bits) - 1
        val nearest = HashMap<Int, Int>()
        val index = ByteArray(bw * bh)
        var at = 0
        for (y in y0..y1) for (x in x0..x1) {
            val i = y * w + x
            // transparent, or the same as the canvas shows: left for the canvas to show through
            index[at++] = if (!changed[i] || px[i] == 0) clear.toByte() else nearest.getOrPut(key(px[i])) {
                val (r, g, b) = rgb(key(px[i]))
                pal.indices.minByOrNull { j -> val q = pal[j]; (q[0] - r) * (q[0] - r) + (q[1] - g) * (q[1] - g) + (q[2] - b) * (q[2] - b) } ?: 0
            }.toByte()
        }
        // graphic control: wiped after (2) or kept (1), the transparent index, the delay
        out.write(byteArrayOf(0x21, 0xf9.toByte(), 4, if (clears) 0x09 else 0x05)); u16(delayCs * f.n); out.write(clear); out.write(0)
        // image descriptor of the box, with a local color table of 2^bits
        out.write(0x2c); u16(x0); u16(y0); u16(bw); u16(bh); out.write(0x80 or (bits - 1))
        for (j in 0..clear) {
            val q = pal.getOrNull(j) ?: intArrayOf(0, 0, 0)
            out.write(q[0]); out.write(q[1]); out.write(q[2])
        }
        out.write(bits)
        val data = lzw(index, bits)
        var p = 0
        while (p < data.size) {
            val k = minOf(255, data.size - p)
            out.write(k); out.write(data, p, k)
            p += k
        }
        out.write(0)
        // The canvas now shows this frame, less its box when it is wiped.
        px.copyInto(canvas)
        if (clears) for (y in y0..y1) canvas.fill(0, y * w + x0, y * w + x1 + 1)
    }
}

// GIF's LZW for indexes of `bits` bits (2 to 8): codes from bits + 1 to 12 bits, a clear code when the table is full.
private fun lzw(index: ByteArray, bits: Int): ByteArray {
    val clear = 1 shl bits
    val eoi = clear + 1
    val first = clear + 2
    val out = ByteArrayOutputStream()
    var acc = 0L
    var nbits = 0
    var size = bits + 1
    fun emit(code: Int) {
        acc = acc or (code.toLong() shl nbits)
        nbits += size
        while (nbits >= 8) { out.write((acc and 0xff).toInt()); acc = acc ushr 8; nbits -= 8 }
    }
    val table = HashMap<Long, Int>()
    var next = first
    emit(clear)
    var prefix = -1
    for (b in index) {
        val k = b.toInt() and 0xff
        if (prefix < 0) { prefix = k; continue }
        val key = (prefix.toLong() shl 8) or k.toLong()
        val found = table[key]
        if (found != null) { prefix = found; continue }
        emit(prefix)
        // widen after a code once the next entry needs it, before adding it, as decoders read
        if (next >= (1 shl size) && size < 12) size++
        if (next < 4096) table[key] = next++
        else {
            emit(clear)
            table.clear()
            next = first
            size = bits + 1
        }
        prefix = k
    }
    if (prefix >= 0) emit(prefix)
    if (next >= (1 shl size) && size < 12) size++
    emit(eoi)
    if (nbits > 0) out.write((acc and 0xff).toInt())
    return out.toByteArray()
}
