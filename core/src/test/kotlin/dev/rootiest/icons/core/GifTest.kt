package dev.rootiest.icons.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadata

// GifEncoder: its frames, decoded by the JDK's GIF reader and laid on a canvas as a viewer does (each box at its place,
// transparent pixels showing through, wiped after when its disposal says so), are the frames that went in: transparent
// where those were under half opaque, else their colors to within the 5 bits a channel the palettes keep. And each
// frame after the first is stored as only the box that changed.
class GifTest {
    private class Decoded(val frames: List<IntArray>, val boxes: List<IntArray>, val delays: List<Int>, val loops: Boolean)

    private fun child(m: IIOMetadata, name: String): Element? {
        val root = m.getAsTree("javax_imageio_gif_image_1.0")
        var n = root.firstChild
        while (n != null) {
            if (n.nodeName == name) return n as Element
            n = n.nextSibling
        }
        return null
    }

    private fun decode(bytes: ByteArray, w: Int, h: Int): Decoded {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val count = reader.getNumImages(true)
        val canvas = IntArray(w * h)
        val frames = mutableListOf<IntArray>()
        val boxes = mutableListOf<IntArray>()
        val delays = mutableListOf<Int>()
        for (i in 0 until count) {
            val m = reader.getImageMetadata(i)
            val d = child(m, "ImageDescriptor")!!
            val gc = child(m, "GraphicControlExtension")!!
            val x0 = d.getAttribute("imageLeftPosition").toInt()
            val y0 = d.getAttribute("imageTopPosition").toInt()
            val img = reader.read(i)
            boxes += intArrayOf(x0, y0, img.width, img.height)
            delays += gc.getAttribute("delayTime").toInt()
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val c = img.getRGB(x, y)
                if ((c ushr 24) != 0) canvas[(y0 + y) * w + x0 + x] = c
            }
            frames += canvas.copyOf()
            if (gc.getAttribute("disposalMethod") == "restoreToBackgroundColor") {
                for (y in 0 until img.height) canvas.fill(0, (y0 + y) * w + x0, (y0 + y) * w + x0 + img.width)
            }
        }
        val loops = String(bytes, Charsets.ISO_8859_1).contains("NETSCAPE2.0")
        return Decoded(frames, boxes, delays, loops)
    }

    private fun assertShows(want: IntArray, got: IntArray, what: String) {
        for (i in want.indices) {
            val a = want[i]
            val b = got[i]
            val opaque = (a ushr 24) >= 128
            assertEquals("$what, pixel $i: transparency", opaque, (b ushr 24) != 0)
            if (!opaque) continue
            for (s in intArrayOf(16, 8, 0)) {
                val diff = Math.abs((a shr s and 0xff) - (b shr s and 0xff))
                assertTrue("$what, pixel $i: color ${"%08x".format(a)} shown as ${"%08x".format(b)}", diff <= 8)
            }
        }
    }

    // A w by h frame: a gradient with a square that moves, and a transparent hole that moves once, at frame 3 (so frame
    // 2 is wiped where the hole goes, and frame 3 shows the gradient again where it was).
    private fun frame(w: Int, h: Int, t: Int) = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val inSquare = x in t..t + 3 && y in 4..7
        val hole = x in (if (t >= 3) 8..9 else 10..11) && y in 10..11
        when {
            hole -> 0x40112233  // under half opaque: transparent
            inSquare -> 0xffe04020.toInt()
            else -> (0xff shl 24) or ((x * 16) shl 16) or ((y * 16) shl 8) or 0x80
        }
    }

    @Test
    fun framesShowAsDrawn() {
        val w = 16
        val h = 16
        for (once in listOf(true, false)) {
            val enc = GifEncoder(4, once)
            val drawn = (0..5).map { frame(w, h, it) }
            drawn.forEachIndexed { i, px -> enc.frame(px, w, h, i + 1) }
            val got = decode(enc.bytes(), w, h)
            assertEquals(drawn.size, got.frames.size)
            assertEquals((1..6).map { it * 4 }, got.delays)
            assertEquals(!once, got.loops)
            drawn.forEachIndexed { i, px -> assertShows(px, got.frames[i], "once=$once, frame $i") }
            // after the first, only the box that changed (the moving square, and the hole), never the whole frame
            for (b in got.boxes.drop(1)) assertTrue("once=$once boxes ${got.boxes.map { it.toList() }}", b[2] * b[3] < w * h / 2)
        }
    }

    @Test
    fun aLoopStartsOverOnItsFirstFrame() {
        // The last frame covers a pixel the first leaves transparent: a viewer that keeps its canvas between loops still
        // shows the first frame as drawn, as the last frame is wiped where the first is transparent.
        val w = 4
        val h = 4
        val a = IntArray(w * h) { if (it == 5) 0 else 0xff00ff00.toInt() }
        val b = IntArray(w * h) { 0xff0000ff.toInt() }
        val enc = GifEncoder(4, false)
        enc.frame(a, w, h, 1)
        enc.frame(b, w, h, 1)
        val bytes = enc.bytes()
        val got = decode(bytes, w, h)
        assertShows(b, got.frames[1], "second frame")
        // Lay the first frame again on the canvas the second leaves (decode keeps it): read a GIF of the frames twice.
        val twice = decodeTwice(bytes, w, h)
        assertShows(a, twice, "first frame, second time round")
    }

    // The canvas after the frames twice over, as a viewer that does not clear it between loops shows the first again.
    private fun decodeTwice(bytes: ByteArray, w: Int, h: Int): IntArray {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val count = reader.getNumImages(true)
        val canvas = IntArray(w * h)
        var shown = canvas.copyOf()
        for (pass in 0..1) for (i in 0 until count) {
            val m = reader.getImageMetadata(i)
            val d = child(m, "ImageDescriptor")!!
            val gc = child(m, "GraphicControlExtension")!!
            val x0 = d.getAttribute("imageLeftPosition").toInt()
            val y0 = d.getAttribute("imageTopPosition").toInt()
            val img = reader.read(i)
            for (y in 0 until img.height) for (x in 0 until img.width) {
                val c = img.getRGB(x, y)
                if ((c ushr 24) != 0) canvas[(y0 + y) * w + x0 + x] = c
            }
            if (pass == 1 && i == 0) shown = canvas.copyOf()
            if (gc.getAttribute("disposalMethod") == "restoreToBackgroundColor") {
                for (y in 0 until img.height) canvas.fill(0, (y0 + y) * w + x0, (y0 + y) * w + x0 + img.width)
            }
        }
        return shown
    }

    @Test
    fun aStillFrameIsOneFrame() {
        val px = frame(8, 8, 2)
        val enc = GifEncoder(0, true)
        enc.frame(px, 8, 8, 1)
        val got = decode(enc.bytes(), 8, 8)
        assertEquals(1, got.frames.size)
        assertShows(px, got.frames[0], "still")
    }
}
