package dev.rootiest.icons.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import dev.rootiest.icons.core.animateSvg
import dev.rootiest.icons.core.layoutsOf
import dev.rootiest.icons.render.Animation
import dev.rootiest.icons.render.animatedPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

// Animations, played: the app draws SVG with AndroidSVG, which shows a still, so an animated SVG is played as an animated
// WebP of its frames (the frames its WebP and GIF files are made of), and those files as themselves.

// An animated WebP or GIF, playing at size (scaled as it is decoded). With replay, a tap plays it again from the start;
// without (a file's tile, where a tap shares it), one that plays once plays again after holding its end a moment.
// With again, it is played from outside: it starts over each time again changes, and one that plays once holds its end.
@Composable
internal fun AnimatedImage(bytes: ByteArray, size: Dp, description: String?, modifier: Modifier = Modifier, replay: Boolean = false, again: Int? = null) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val drawable by produceState<Drawable?>(null, bytes, px) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    val side = maxOf(info.size.width, info.size.height)
                    if (side > px) decoder.setTargetSize(info.size.width * px / side, info.size.height * px / side)
                }
            }.getOrNull()
        }
    }
    val d = drawable
    // each new frame the drawable asks for draws the canvas again
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(d) {
        val main = Handler(Looper.getMainLooper())
        // a drawable holds its callback weakly: this effect keeps it
        val callback = object : Drawable.Callback {
            override fun invalidateDrawable(who: Drawable) { tick++ }
            override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) { main.postAtTime(what, who, `when`) }
            override fun unscheduleDrawable(who: Drawable, what: Runnable) { main.removeCallbacks(what, who) }
        }
        val restart = Runnable { (d as? AnimatedImageDrawable)?.start() }
        val ended = object : Animatable2.AnimationCallback() {
            override fun onAnimationEnd(drawable: Drawable?) { main.postDelayed(restart, 2000) }
        }
        d?.callback = callback
        if (d is AnimatedImageDrawable) {
            if (!replay && again == null && d.repeatCount != AnimatedImageDrawable.REPEAT_INFINITE) d.registerAnimationCallback(ended)
            d.start()
        }
        onDispose {
            main.removeCallbacks(restart)
            if (d is AnimatedImageDrawable) {
                d.unregisterAnimationCallback(ended)
                d.stop()
            }
            d?.callback = null
        }
    }
    LaunchedEffect(again) {
        if (again != null && d is AnimatedImageDrawable) { d.stop(); d.start() }
    }
    val tap = if (replay && again == null && d is AnimatedImageDrawable) Modifier.clickable(interactionSource = null, indication = null) { d.stop(); d.start() } else Modifier
    Canvas(modifier.size(size).semantics { description?.let { contentDescription = it } }.then(tap)) {
        tick
        if (d != null) {
            val w = d.intrinsicWidth.takeIf { it > 0 } ?: this.size.width.toInt()
            val h = d.intrinsicHeight.takeIf { it > 0 } ?: this.size.height.toInt()
            val k = minOf(this.size.width / w, this.size.height / h)
            val left = ((this.size.width - w * k) / 2).toInt()
            val top = ((this.size.height - h * k) / 2).toInt()
            d.setBounds(left, top, left + (w * k).toInt(), top + (h * k).toInt())
            drawIntoCanvas { d.draw(it.nativeCanvas) }
        }
    }
}

// An icon's SVG with its animation playing, at size, in one layout and in light or dark mode as SvgImage draws it. Its
// frames take a moment to draw; until then, and if they cannot be drawn, it shows the still. A tap plays it again.
@Composable
internal fun AnimatedSvgImage(svg: String, animation: Animation, size: Dp, description: String?, light: Boolean?, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bytes by produceState<ByteArray?>(null, svg, animation, px, light) {
        value = null
        // a choice changed while drawing: this is cancelled, and the frames still to draw with it
        value = try {
            withContext(Dispatchers.Default) {
                val moving = animation.svg(svg, "auto", animation.target)
                animatedPreview(moving, animation, "auto", size.value.toInt(), layoutsOf(svg), light, px)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    val b = bytes
    if (b != null) AnimatedImage(b, size, description, modifier, replay = true)
    else SvgImage(svg, size, description, modifier, light = light)
}
