package dev.rootiest.icons.render

import android.graphics.Bitmap
import android.os.Build
import dev.rootiest.icons.core.GifEncoder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Animated WebP and GIF from frames, as render-rootiest-icons --animate writes them with ImageMagick: 25 frames a
// second, a run of the same frame kept as one shown longer; one that plays once holds its last frame, the others loop.
// Frames are taken one at a time and only the one before is held, so a 4096 px animation fits in a phone's memory.

// Where frames go: each with how many frames in a row it stands for.
interface FrameSink {
    fun frame(f: Bitmap, n: Int)
    fun bytes(): ByteArray
}

// Feeds count frames to the sinks, a run of the same frame as one; frameAt(i) draws frame i, a bitmap of its own.
fun encodeFrames(count: Int, sinks: List<FrameSink>, frameAt: (Int) -> Bitmap) {
    var held: Bitmap? = null
    var n = 0
    for (i in 0 until count) {
        val f = frameAt(i)
        val last = held
        if (last != null && last.sameAs(f)) {
            n++
            f.recycle()
            continue
        }
        if (last != null) {
            sinks.forEach { it.frame(last, n) }
            last.recycle()
        }
        held = f
        n = 1
    }
    held?.let { last ->
        sinks.forEach { it.frame(last, n) }
        last.recycle()
    }
}

private fun le(n: Int, bytes: Int) = ByteArray(bytes) { (n shr (8 * it) and 0xff).toByte() }

private fun chunk(tag: String, data: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(tag.toByteArray(Charsets.US_ASCII))
    out.write(le(data.size, 4))
    out.write(data)
    if (data.size % 2 == 1) out.write(0)
    return out.toByteArray()
}

// An animated WebP: each frame compressed by Android (lossless from Android 11, or lossy for a preview), its image
// chunks wrapped in an ANMF frame of a VP8X/ANIM container.
class WebpSink(private val delayMs: Int, private val once: Boolean, private val lossy: Boolean = false) : FrameSink {
    private val body = ByteArrayOutputStream()
    private var w = 0
    private var h = 0

    @Suppress("DEPRECATION")
    private val format = when {
        Build.VERSION.SDK_INT < 30 -> Bitmap.CompressFormat.WEBP
        lossy -> Bitmap.CompressFormat.WEBP_LOSSY
        else -> Bitmap.CompressFormat.WEBP_LOSSLESS
    }

    override fun frame(f: Bitmap, n: Int) = add(images(f), f.width, f.height, n)

    // A frame's image chunks after "RIFF size WEBP" of Android's still: ALPH and VP8, or VP8L; not its VP8X, nor the
    // ICCP (sRGB) Android adds, which a frame cannot hold. Frames can be compressed apart, at once, then added in order.
    fun images(f: Bitmap): ByteArray {
        val still = ByteArrayOutputStream().also { f.compress(format, if (lossy) 90 else 100, it) }.toByteArray()
        val images = ByteArrayOutputStream()
        var p = 12
        while (p + 8 <= still.size) {
            val tag = String(still, p, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(still, p + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val total = 8 + size + (size % 2)
            if (tag == "ALPH" || tag == "VP8 " || tag == "VP8L") images.write(still, p, minOf(total, still.size - p))
            p += total
        }
        return images.toByteArray()
    }

    // A frame of w by h, from images(), shown for n frames' time.
    fun add(images: ByteArray, w: Int, h: Int, n: Int) {
        this.w = w
        this.h = h
        // x/2, y/2, width-1, height-1, duration, flags (no blending: each frame replaces the last)
        body.write(chunk("ANMF", le(0, 3) + le(0, 3) + le(w - 1, 3) + le(h - 1, 3) + le(delayMs * n, 3) + byteArrayOf(0x02) + images))
    }

    override fun bytes(): ByteArray {
        val all = chunk("VP8X", byteArrayOf(0x12, 0, 0, 0) + le(w - 1, 3) + le(h - 1, 3)) +  // animation and alpha
            chunk("ANIM", le(0, 4) + le(if (once) 1 else 0, 2)) + body.toByteArray()
        return "RIFF".toByteArray() + le(4 + all.size, 4) + "WEBP".toByteArray() + all
    }
}

// An animated GIF, by :core's GifEncoder: each frame only the box that changed, losslessly.
class GifSink(delayCs: Int, once: Boolean) : FrameSink {
    private val gif = GifEncoder(delayCs, once)

    override fun frame(f: Bitmap, n: Int) {
        val pixels = IntArray(f.width * f.height)
        f.getPixels(pixels, 0, f.width, 0, 0, f.width, f.height)
        gif.frame(pixels, f.width, f.height, n)
    }

    override fun bytes(): ByteArray = gif.bytes()
}
