package dev.rootiest.icons.render

import android.graphics.Bitmap
import android.graphics.Canvas
import com.caverock.androidsvg.SVG
import kotlin.math.max
import kotlin.math.roundToInt

// Drawing SVG text: AndroidSVG draws the icons' paths, CSS classes and knockout masks onto a bitmap.
object Svg {
    // A square bitmap of the SVG, on a background color or transparent.
    fun rasterize(text: String, size: Int, background: Int? = null): Bitmap {
        val svg = SVG.getFromString(text)
        svg.setDocumentWidth(size.toFloat())
        svg.setDocumentHeight(size.toFloat())
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        background?.let(canvas::drawColor)
        svg.renderToCanvas(canvas)
        return bitmap
    }

    // The generator's measure(): the box a body draws in, in its own units [x, y, w, h], from the alpha of a render
    // (over 1% counts). About 1000 px on the longer side, so the box is right to a thousandth of it.
    fun measure(viewBox: String, body: String): List<Double> {
        val (x0, y0, w, h) = viewBox.split(" ").map(String::toDouble)
        val px = 1000.0 / max(w, h)
        val width = (w * px).roundToInt().coerceAtLeast(1)
        val height = (h * px).roundToInt().coerceAtLeast(1)
        val svg = SVG.getFromString(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"$viewBox\" width=\"$width\" height=\"$height\">" +
                "<g color=\"#000\" fill=\"currentColor\">$body</g></svg>",
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap))
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((pixels[row + x] ushr 24) > 2) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        require(maxX >= 0) { "it draws nothing" }
        return listOf(x0 + minX / px, y0 + minY / px, (maxX - minX + 1) / px, (maxY - minY + 1) / px)
    }
}
