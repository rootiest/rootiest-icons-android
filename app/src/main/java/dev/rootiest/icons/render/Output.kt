package dev.rootiest.icons.render

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.rootiest.icons.core.ColorSpec
import dev.rootiest.icons.core.NamedBytes
import dev.rootiest.icons.core.animateSvg
import dev.rootiest.icons.core.animationTag
import dev.rootiest.icons.core.FPS
import dev.rootiest.icons.core.frameSvg
import dev.rootiest.icons.core.frameTimes
import dev.rootiest.icons.core.playFrames
import dev.rootiest.icons.core.layoutsOf
import dev.rootiest.icons.core.icoBytes
import dev.rootiest.icons.core.rasterSvg
import dev.rootiest.icons.core.resolveLayout
import dev.rootiest.icons.core.schemeSvg
import dev.rootiest.icons.core.singleLayoutSvg
import dev.rootiest.icons.core.zipBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// The largest side a phone renders: a 4096 px bitmap takes 64 MB while it is drawn (the command line goes to 16384).
const val MAX_PX = 4096
const val ICO_MAX = 256
const val FOLDER = "Rootiest Icons"

// A rendered file, with a small picture of it to show and, for an animated one, an animated WebP of it that plays.
class Rendered(val file: NamedBytes, val preview: Bitmap?, val animated: ByteArray? = null)

private fun Bitmap.bytes(format: Bitmap.CompressFormat) = ByteArrayOutputStream().also { compress(format, 100, it) }.toByteArray()

@Suppress("DEPRECATION")
private val WEBP = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP

// An SVG's animation (render-icon.py --animate), looping (draw and morph), seconds (null: default), and for a morph
// the icon it turns into (its name, and its SVG built as the icon is).
data class Animation(
    val animate: String = "none", val loop: Boolean = false, val duration: Double? = null,
    val morphTo: String? = null, val target: String? = null,
    val morphColor: ColorSpec? = null, val morphTone2: ColorSpec? = null, // the target's colors, as they apply
) {
    val tag get() = animationTag(animate, loop, duration, morphTo, morphColor, morphTone2)
    val moves get() = animate != "none" && (animate != "morph" || target != null)
}

// Every file of a render, as render-rootiest-icons writes them: per layout an SVG (animated, if asked), a PNG, a WebP
// and a GIF per size (the WebP and GIF animated, with an animation), and one .ico holding every size. previewLight: an
// SVG's picture in light or dark mode (an auto one switches); null, the rasters' dark-background shades.
suspend fun renderFiles(
    text: String, stem: String, small: List<Int>, layouts: List<String>, sizes: List<Int>, formats: List<String>, background: Int?,
    animation: Animation = Animation(), previewLight: Boolean? = null,
): List<Rendered> {
    val out = ArrayList<Rendered>()
    for (lay in layouts) {
        val name = stem + if (lay == "auto") "" else "-layout-$lay"
        val one = if (lay == "auto" || small.isEmpty()) text else singleLayoutSvg(text, lay)
        val target = animation.target?.let { if (lay != "auto" && layoutsOf(it).isNotEmpty()) singleLayoutSvg(it, lay) else it }
        val moving = animateSvg(one, animation.animate, animation.duration, animation.loop, target)
        if ("svg" in formats) {
            out += Rendered(
                NamedBytes("$name${animation.tag}.svg", moving.toByteArray()),
                Svg.rasterize(rasterSvg(previewLight?.let { schemeSvg(text, it) } ?: text, lay, 192, small), 192),
                if (animation.moves) animatedPreview(moving, animation, lay, 192, small, previewLight) else null,
            )
        }
        if (animation.moves && ("webp" in formats || "gif" in formats)) {
            val (times, once) = frameTimes(animation.animate, animation.loop, animation.duration)
            for (size in sizes) {
                // each frame: the animation's values at its moment, then one layout and the dark-background shades
                val webp = WebpSink(1000 / FPS, once).takeIf { "webp" in formats }
                val gif = GifSink(100 / FPS, once).takeIf { "gif" in formats }
                val single = oneLayout(moving, lay, size, small)
                encodeFrames(times.size, listOfNotNull(webp, gif)) { i ->
                    Svg.rasterize(rasterSvg(frameSvg(single, times[i]), lay, size, emptyList()), size, background)
                }
                val still = Svg.rasterize(rasterSvg(text, lay, size, small), minOf(size, 256), background)
                webp?.let { out += Rendered(NamedBytes("$name${animation.tag}-${size}px.webp", it.bytes()), still, it.bytes()) }
                gif?.let { out += Rendered(NamedBytes("$name${animation.tag}-${size}px.gif", it.bytes()), still, it.bytes()) }
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
            if ("gif" in formats && !animation.moves) out += Rendered(NamedBytes("$name-${size}px.gif", GifSink(0, true).also { it.frame(bitmap, 1) }.bytes()), preview)
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

// The layout of an animated SVG a size shows, alone, before its frames are worked out: only what is drawn is worked on.
private fun oneLayout(text: String, layout: String, size: Int, small: List<Int>) =
    if (small.isEmpty()) text else singleLayoutSvg(text, resolveLayout(layout, size, small))

// An animated SVG playing, as an animated WebP of px a side (lossy: it is only shown): one layout of it, the one a
// size shows (as rasterSvg picks it), in light or dark mode (null: the rasters' dark-background shades). Its frames are
// drawn and compressed on several cores at once, then a run of the same frame is kept as one.
suspend fun animatedPreview(moving: String, animation: Animation, layout: String, size: Int, small: List<Int>, light: Boolean?, px: Int = size): ByteArray {
    val (times, once) = frameTimes(animation.animate, animation.loop, animation.duration)
    return previewFrames(oneLayout(moving, layout, size, small), times, once, layout, size, light, px)
}

// A published animated file (an animated status badge) playing, as animatedPreview plays an animation: its frames from
// its own play time (a loop loops, a change plays once and holds its end), the layout size shows.
suspend fun publishedPreview(text: String, size: Int, light: Boolean?, px: Int = size): ByteArray {
    val (times, once) = playFrames(text)
    return previewFrames(oneLayout(text, "auto", size, layoutsOf(text)), times, once, "auto", size, light, px)
}

private suspend fun previewFrames(single: String, times: List<Double>, once: Boolean, layout: String, size: Int, light: Boolean?, px: Int): ByteArray {
    val sink = WebpSink(1000 / FPS, once, lossy = true)
    val cores = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 4)
    val frames = withContext(Dispatchers.Default.limitedParallelism(cores)) {
        times.map { t ->
            async {
                val frame = frameSvg(single, t, light == true)
                val bitmap = Svg.rasterize(rasterSvg(if (light != null) schemeSvg(frame, light) else frame, layout, size, emptyList()), px)
                val pixels = IntArray(px * px).also { bitmap.getPixels(it, 0, px, 0, 0, px, px) }
                val images = sink.images(bitmap)
                bitmap.recycle()
                pixels.contentHashCode() to images
            }
        }.awaitAll()
    }
    var i = 0
    while (i < frames.size) {
        var n = 1
        while (i + n < frames.size && frames[i + n].first == frames[i].first) n++
        sink.add(frames[i].second, px, px, n)
        i += n
    }
    return sink.bytes()
}
