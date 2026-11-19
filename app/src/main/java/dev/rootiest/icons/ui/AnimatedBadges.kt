package dev.rootiest.icons.ui

import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.R
import dev.rootiest.icons.core.AnimatedEntry
import dev.rootiest.icons.core.NamedBytes
import dev.rootiest.icons.core.frameSvg
import dev.rootiest.icons.core.playTime
import dev.rootiest.icons.render.FOLDER
import dev.rootiest.icons.render.publishedPreview
import dev.rootiest.icons.render.saveToDownloads
import dev.rootiest.icons.render.share
import dev.rootiest.icons.ui.theme.palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The animated status badges, as the homepage shows them: a segmented choice of shape, the shape's cycle (every state
// in turn) playing, and a matrix of its changes, from (rows) to (columns); the heartbeat has its loops on the diagonal.
// Drawing frames is costly on a phone, so only the cycle and the loops play by themselves: a change shows its end (the
// new state) until it is tapped, and is then drawn once and kept. A tap plays a badge again and chooses it, to save or
// share its SVG.

private val SHAPES = listOf("circle", "square", "triangle", "heartbeat")
private val STATES = listOf("ok", "warn", "bad", "unknown", "info")

private fun <T> List<T>.rank(x: T) = indexOf(x).let { if (it < 0) size else it }

private fun path(name: String) = "color/animated/$name.svg"

// What the section's badges are doing: the badge chosen, and how many times each was played (a tap plays it again).
internal class AnimatedPlays {
    var chosen by mutableStateOf<String?>(null)
    val plays = mutableStateMapOf<String, Int>()

    fun play(name: String) {
        plays[name] = (plays[name] ?: 0) + 1
        chosen = name
    }
}

// Played badges (animated WebPs) and their stills, by file, mode and size, so a badge scrolled back into view or played
// again is not drawn again.
private val played = object : LruCache<String, ByteArray>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray) = value.size
}
private val stills = LruCache<String, String>(96)

internal fun LazyGridScope.animatedBadges(vm: IconsViewModel, list: List<AnimatedEntry>, state: AnimatedPlays) {
    val shapes = list.map { it.shape }.filter { it.isNotEmpty() }.distinct().sortedBy { SHAPES.rank(it) }
    if (shapes.isEmpty()) return
    val shape = vm.animatedShape?.takeIf { it in shapes } ?: shapes[0]
    val ofShape = list.filter { it.shape == shape }
    val states = ofShape.mapNotNull { it.from }.distinct().sortedBy { STATES.rank(it) }
    val cycle = ofShape.firstOrNull { it.from == null }
    val chosen = state.chosen?.takeIf { c -> ofShape.any { it.name == c } } ?: cycle?.name ?: ofShape.firstOrNull()?.name
    item(key = "animated-head", span = { GridItemSpan(maxLineSpan) }) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle("Animated status badges", "${list.size}")
            Text(
                "Badges whose mark morphs from one state to another, its color with it. A change plays once and ends on the " +
                    "new state; the cycle shows every state in turn. Heartbeat is the status icon itself: each state beating " +
                    "(down the diagonal), and a beat in the old color then one in the new. Tap a badge to play it, then save " +
                    "or share its SVG.",
                style = MaterialTheme.typography.bodySmall, color = palette.Subtext0,
            )
            Segmented(shapes.map { it to it.replaceFirstChar(Char::uppercase) }, shape, Modifier.fillMaxWidth().padding(top = 6.dp)) { vm.animatedShape = it }
        }
    }
    if (cycle != null) item(key = "animated-cycle", span = { GridItemSpan(maxLineSpan) }) {
        val dark = previewDark(vm)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Badge(vm, cycle.name, 132.dp, 120.dp, dark, autoplay = true, state, chosen == cycle.name, "Every state in turn ($shape)")
            Text("Every state in turn", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
        }
    }
    if (states.isNotEmpty()) item(key = "animated-matrix", span = { GridItemSpan(maxLineSpan) }) {
        Matrix(vm, ofShape, states, state, chosen)
    }
    if (chosen != null) item(key = "animated-file", span = { GridItemSpan(maxLineSpan) }) { FileRow(vm, chosen) }
}

// The changes, from (rows) to (columns), each cell as large as the width allows (up to a point), centered.
@Composable
private fun Matrix(vm: IconsViewModel, ofShape: List<AnimatedEntry>, states: List<String>, state: AnimatedPlays, chosen: String?) {
    val dark = previewDark(vm)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
        val label = 64.dp
        val gap = 4.dp
        val cell = ((maxWidth - label - gap * states.size) / states.size).coerceIn(44.dp, 88.dp)
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.width(label))
                states.forEach { to ->
                    Text("to\n$to", Modifier.width(cell), style = MaterialTheme.typography.labelSmall, color = palette.Subtext0, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
            states.forEach { from ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    Text(from, Modifier.width(label).padding(end = 6.dp), style = MaterialTheme.typography.labelMedium, color = palette.Subtext0, textAlign = TextAlign.End, maxLines = 1)
                    states.forEach { to ->
                        val a = ofShape.firstOrNull { it.from == from && it.to == to }
                        if (a == null) Spacer(Modifier.size(cell))
                        else Badge(vm, a.name, cell, cell - 10.dp, dark, autoplay = from == to, state, chosen == a.name, if (from == to) "$from, beating" else "$from becomes $to")
                    }
                }
            }
        }
    }
}

// A badge on its swatch: a tap plays it again and chooses it.
@Composable
private fun Badge(vm: IconsViewModel, name: String, box: Dp, size: Dp, dark: Boolean, autoplay: Boolean, state: AnimatedPlays, chosen: Boolean, description: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(box).swatch("color", vm.previewBackground, dark)
            .then(if (chosen) Modifier.border(2.dp, palette.Accent, shape) else Modifier)
            .clickable(onClickLabel = "Play $description") { state.play(name) },
        contentAlignment = Alignment.Center,
    ) {
        Playing(vm, name, size, !dark, autoplay, state.plays[name] ?: 0, description)
    }
}

// A published animated file at size, in light or dark mode: playing, once it plays by itself (autoplay) or has been
// played; otherwise, and while its frames are drawn, still at its end (a loop: its start).
@Composable
private fun Playing(vm: IconsViewModel, name: String, size: Dp, light: Boolean, autoplay: Boolean, play: Int, description: String) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val text by produceState<String?>(null, name) { value = runCatching { vm.repo.svg(path(name)) }.getOrNull() }
    val svg = text
    val moving = autoplay || play > 0
    val bytes by produceState<ByteArray?>(null, svg, moving, light, px) {
        value = null
        if (svg == null || !moving) return@produceState
        val key = "$name ${svg.hashCode()} $light $px"
        value = played.get(key) ?: try {
            withContext(Dispatchers.Default) { publishedPreview(svg, size.value.toInt(), light, px) }.also { played.put(key, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    val still by produceState<String?>(null, svg, autoplay, light) {
        value = svg?.let { s ->
            val key = "$name ${s.hashCode()} $light $autoplay"
            stills.get(key) ?: withContext(Dispatchers.Default) {
                runCatching { frameSvg(s, if (autoplay) 0.0 else playTime(s).seconds, light) }.getOrNull()
            }?.also { stills.put(key, it) }
        }
    }
    val b = bytes
    if (b != null) AnimatedImage(b, size, description, again = play)
    else SvgImage(still, size, description, light = light)
}

// The chosen badge's file, to save to Downloads or share.
@Composable
private fun FileRow(vm: IconsViewModel, name: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = "$name.svg"
    fun withFile(what: String, use: suspend (NamedBytes) -> Unit) {
        scope.launch {
            runCatching { use(NamedBytes(file, vm.repo.svg(path(name)).toByteArray())) }
                .onFailure { Toast.makeText(context, "Could not $what: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(palette.Mantle).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(file, style = MaterialTheme.typography.labelMedium, color = palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("SVG only: anything that does not play it shows the first state", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
        }
        IconButton(onClick = {
            withFile("save") { f ->
                withContext(Dispatchers.IO) { saveToDownloads(context, listOf(f)) }
                Toast.makeText(context, "Saved $file to Downloads/$FOLDER", Toast.LENGTH_LONG).show()
            }
        }) { Icon(painterResource(R.drawable.ic_tabler_download), contentDescription = "Save $file to Downloads", Modifier.size(22.dp), tint = palette.Subtext0) }
        IconButton(onClick = { withFile("share") { f -> share(context, listOf(f), name) } }) {
            Icon(painterResource(R.drawable.ic_tabler_share), contentDescription = "Share $file", Modifier.size(22.dp), tint = palette.Subtext0)
        }
    }
}
