package dev.rootiest.icons.render

import android.graphics.Bitmap
import android.os.Build
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Animated WebP and GIF from frames, as render-rootiest-icons --animate writes them with ImageMagick: 25 frames a
// second, a run of the same frame kept as one shown longer; one that plays once holds its last frame, the others loop.

// The frames as (frame, how many in a row are the same).
fun runs(frames: List<Bitmap>): List<Pair<Bitmap, Int>> {
    val out = ArrayList<Pair<Bitmap, Int>>()
    for (f in frames) {
        val last = out.lastOrNull()
        if (last != null && last.first.sameAs(f)) out[out.size - 1] = last.first to last.second + 1 else out += f to 1
    }
    return out
}

// An animated WebP: each frame compressed by Android (lossless from Android 11), its image chunks wrapped in an ANMF
// frame of a VP8X/ANIM container.
fun animatedWebp(frames: List<Pair<Bitmap, Int>>, delayMs: Int, once: Boolean): ByteArray {
    @Suppress("DEPRECATION")
    val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
    val w = frames[0].first.width
    val h = frames[0].first.height
    val body = ByteArrayOutputStream()
    fun le(n: Int, bytes: Int) = ByteArray(bytes) { (n shr (8 * it) and 0xff).toByte() }
    fun chunk(tag: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag.toByteArray(Charsets.US_ASCII))
        out.write(le(data.size, 4))
        out.write(data)
        if (data.size % 2 == 1) out.write(0)
        return out.toByteArray()
    }
    body.write(chunk("VP8X", byteArrayOf(0x12, 0, 0, 0) + le(w - 1, 3) + le(h - 1, 3)))  // animation and alpha
    body.write(chunk("ANIM", le(0, 4) + le(if (once) 1 else 0, 2)))
    for ((f, n) in frames) {
        val still = ByteArrayOutputStream().also { f.compress(format, 100, it) }.toByteArray()
        // the still's image chunks after "RIFF size WEBP": ALPH and VP8, or VP8L; not its VP8X, nor the ICCP (sRGB)
        // Android adds, which a frame cannot hold
        val images = ByteArrayOutputStream()
        var p = 12
        while (p + 8 <= still.size) {
            val tag = String(still, p, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(still, p + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val total = 8 + size + (size % 2)
            if (tag == "ALPH" || tag == "VP8 " || tag == "VP8L") images.write(still, p, minOf(total, still.size - p))
            p += total
        }
        // x/2, y/2, width-1, height-1, duration, flags (no blending: each frame replaces the last)
        body.write(chunk("ANMF", le(0, 3) + le(0, 3) + le(w - 1, 3) + le(h - 1, 3) + le(delayMs * n, 3) + byteArrayOf(0x02) + images.toByteArray()))
    }
    val bytes = body.toByteArray()
    return "RIFF".toByteArray() + le(4 + bytes.size, 4) + "WEBP".toByteArray() + bytes
}

// An animated GIF: each frame in a palette of its own (up to 255 colors, from its most common ones at 5 bits a
// channel), fully transparent pixels in the 256th; a GIF has no partial transparency.
fun animatedGif(frames: List<Pair<Bitmap, Int>>, delayCs: Int, once: Boolean): ByteArray {
    val w = frames[0].first.width
    val h = frames[0].first.height
    val out = ByteArrayOutputStream()
    fun u16(n: Int) { out.write(n and 0xff); out.write(n shr 8 and 0xff) }
    out.write("GIF89a".toByteArray())
    u16(w); u16(h)
    out.write(0); out.write(0); out.write(0)  // no global color table
    if (!once) {  // NETSCAPE2.0: loop forever
        out.write(byteArrayOf(0x21, 0xff.toByte(), 11) + "NETSCAPE2.0".toByteArray() + byteArrayOf(3, 1, 0, 0, 0))
    }
    val pixels = IntArray(w * h)
    // 5 bits a channel, as rrrrrgggggbbbbb (infix shl and or bind left to right, hence the parentheses)
    fun key(c: Int) = ((c shr 19 and 0x1f) shl 10) or ((c shr 11 and 0x1f) shl 5) or (c shr 3 and 0x1f)
    for ((f, n) in frames) {
        f.getPixels(pixels, 0, w, 0, 0, w, h)
        val counts = HashMap<Int, Int>()
        for (c in pixels) if ((c ushr 24) >= 128) {
            val k = key(c)
            counts[k] = (counts[k] ?: 0) + 1
        }
        val palette = counts.entries.sortedByDescending { it.value }.take(255).map { it.key }
        fun rgb(k: Int) = intArrayOf((k shr 10 and 0x1f) * 255 / 31, (k shr 5 and 0x1f) * 255 / 31, (k and 0x1f) * 255 / 31)
        val pal = palette.map(::rgb)
        val nearest = HashMap<Int, Int>()
        val index = ByteArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            if ((c ushr 24) < 128) { index[i] = 255.toByte(); continue }
            val k = key(c)
            index[i] = nearest.getOrPut(k) {
                val (r, g, b) = rgb(k)
                pal.indices.minByOrNull { j -> val q = pal[j]; (q[0] - r) * (q[0] - r) + (q[1] - g) * (q[1] - g) + (q[2] - b) * (q[2] - b) } ?: 0
            }.toByte()
        }
        // graphic control: restore to background after, transparent index 255, the delay
        out.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 0x09)); u16(delayCs * n); out.write(255); out.write(0)
        // image descriptor with a local color table of 256
        out.write(0x2c); u16(0); u16(0); u16(w); u16(h); out.write(0x87)
        for (j in 0 until 256) {
            val q = pal.getOrNull(j) ?: intArrayOf(0, 0, 0)
            out.write(q[0]); out.write(q[1]); out.write(q[2])
        }
        out.write(8)
        val data = lzw(index)
        var p = 0
        while (p < data.size) {
            val n = minOf(255, data.size - p)
            out.write(n); out.write(data, p, n)
            p += n
        }
        out.write(0)
    }
    out.write(0x3b)
    return out.toByteArray()
}

// GIF's LZW for 8-bit indexes: codes from 9 to 12 bits, a clear code when the table is full.
private fun lzw(index: ByteArray): ByteArray {
    val clear = 256
    val eoi = 257
    val out = ByteArrayOutputStream()
    var bits = 0L
    var nbits = 0
    var size = 9
    fun emit(code: Int) {
        bits = bits or (code.toLong() shl nbits)
        nbits += size
        while (nbits >= 8) { out.write((bits and 0xff).toInt()); bits = bits ushr 8; nbits -= 8 }
    }
    val table = HashMap<Long, Int>()
    var next = 258
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
            next = 258
            size = 9
        }
        prefix = k
    }
    if (prefix >= 0) emit(prefix)
    if (next >= (1 shl size) && size < 12) size++
    emit(eoi)
    if (nbits > 0) out.write((bits and 0xff).toInt())
    return out.toByteArray()
}
