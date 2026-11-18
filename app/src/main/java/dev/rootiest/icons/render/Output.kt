package dev.rootiest.icons.render

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.rootiest.icons.core.NamedBytes
import dev.rootiest.icons.core.animateSvg
import dev.rootiest.icons.core.animationTag
import dev.rootiest.icons.core.FPS
import dev.rootiest.icons.core.frameSvg
import dev.rootiest.icons.core.frameTimes
import dev.rootiest.icons.core.layoutsOf
import dev.rootiest.icons.core.icoBytes
import dev.rootiest.icons.core.rasterSvg
import dev.rootiest.icons.core.singleLayoutSvg
import dev.rootiest.icons.core.zipBytes
import java.io.ByteArrayOutputStream
import java.io.File

// The largest side a phone renders: a 4096 px bitmap takes 64 MB while it is drawn (the command line goes to 16384).
const val MAX_PX = 4096
const val ICO_MAX = 256
const val FOLDER = "Rootiest Icons"

// A rendered file, with a small picture of it to show.
class Rendered(val file: NamedBytes, val preview: Bitmap?)

private fun Bitmap.bytes(format: Bitmap.CompressFormat) = ByteArrayOutputStream().also { compress(format, 100, it) }.toByteArray()

@Suppress("DEPRECATION")
private val WEBP = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP

// An SVG's animation (render-icon.py --animate), looping (draw and morph), seconds (null: default), and for a morph
// the icon it turns into (its name, and its SVG built as the icon is).
data class Animation(
    val animate: String = "none", val loop: Boolean = false, val duration: Double? = null,
    val morphTo: String? = null, val target: String? = null,
) {
    val tag get() = animationTag(animate, loop, duration, morphTo)
    val moves get() = animate != "none" && (animate != "morph" || target != null)
}

// Every file of a render, as render-rootiest-icons writes them: per layout an SVG (animated, if asked), a PNG, a WebP
// and a GIF per size (the WebP and GIF animated, with an animation), and one .ico holding every size.
fun renderFiles(
    text: String, stem: String, small: List<Int>, layouts: List<String>, sizes: List<Int>, formats: List<String>, background: Int?,
    animation: Animation = Animation(),
): List<Rendered> {
    val out = ArrayList<Rendered>()
    for (lay in layouts) {
        val name = stem + if (lay == "auto") "" else "-layout-$lay"
        val one = if (lay == "auto" || small.isEmpty()) text else singleLayoutSvg(text, lay)
        val target = animation.target?.let { if (lay != "auto" && layoutsOf(it).isNotEmpty()) singleLayoutSvg(it, lay) else it }
        val moving = animateSvg(one, animation.animate, animation.duration, animation.loop, target)
        if ("svg" in formats) {
            out += Rendered(NamedBytes("$name${animation.tag}.svg", moving.toByteArray()), Svg.rasterize(rasterSvg(text, lay, 192, small), 192))
        }
        if (animation.moves && ("webp" in formats || "gif" in formats)) {
            val (times, once) = frameTimes(animation.animate, animation.loop, animation.duration)
            for (size in sizes) {
                // each frame: the animation's values at its moment, then one layout and the dark-background shades
                val frames = times.map { t -> Svg.rasterize(rasterSvg(frameSvg(moving, t), lay, size, small), size, background) }
                val shown = runs(frames)
                val preview = frames.last().let { if (size <= 256) it else Bitmap.createScaledBitmap(it, 256, 256, true) }
                if ("webp" in formats) out += Rendered(NamedBytes("$name${animation.tag}-${size}px.webp", animatedWebp(shown, 1000 / FPS, once)), preview)
                if ("gif" in formats) out += Rendered(NamedBytes("$name${animation.tag}-${size}px.gif", animatedGif(shown, 100 / FPS, once)), preview)
            }
        }
        if (formats.none { it == "png" || it == "ico" || (!animation.moves && (it == "webp" || it == "gif")) }) continue
        val pngs = ArrayList<Pair<Int, ByteArray>>()
        var last: Bitmap? = null
        for (size in sizes) {
            val bitmap = Svg.rasterize(rasterSvg(text, lay, size, small), size, background)
            val png = bitmap.bytes(Bitmap.CompressFormat.PNG)
            pngs += size to png
            val preview = if (size <= 256) bitmap else Bitmap.createScaledBitmap(bitmap, 256, 256, true)
            if ("png" in formats) out += Rendered(NamedBytes("$name-${size}px.png", png), preview)
            if ("webp" in formats && !animation.moves) out += Rendered(NamedBytes("$name-${size}px.webp", bitmap.bytes(WEBP)), preview)
            if ("gif" in formats && !animation.moves) out += Rendered(NamedBytes("$name-${size}px.gif", animatedGif(listOf(bitmap to 1), 0, true)), preview)
            last = preview
        }
        if ("ico" in formats) out += Rendered(NamedBytes("$name.ico", icoBytes(pngs)), last)
    }
    return out
}

private fun mime(name: String) = when (name.substringAfterLast('.')) {
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "ico" -> "image/x-icon"
    else -> "application/zip"
}

// Save files to Downloads/Rootiest Icons. Returns how many were written.
fun saveToDownloads(context: Context, files: List<NamedBytes>): Int {
    val resolver = context.contentResolver
    for (f in files) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime(f.name))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("cannot create ${f.name} in Downloads")
        resolver.openOutputStream(uri)!!.use { it.write(f.bytes) }
    }
    return files.size
}

// Hand files to another app: one as it is, several as a zip named after the render.
fun share(context: Context, files: List<NamedBytes>, stem: String) {
    val dir = File(context.cacheDir, "renders").apply { deleteRecursively(); mkdirs() }
    val one = if (files.size == 1) files[0] else NamedBytes("$stem.zip", zipBytes(files))
    val file = File(dir, one.name).apply { writeBytes(one.bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime(one.name)).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share ${one.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
