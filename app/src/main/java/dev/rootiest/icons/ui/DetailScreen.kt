package dev.rootiest.icons.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rootiest.icons.Catalogue
import dev.rootiest.icons.Choices
import dev.rootiest.icons.Drawn
import dev.rootiest.icons.EmblemSubject
import dev.rootiest.icons.IconSubject
import dev.rootiest.icons.IconifySubject
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.OwnEmblemSubject
import dev.rootiest.icons.OwnLogoSubject
import dev.rootiest.icons.R
import dev.rootiest.icons.Route
import dev.rootiest.icons.Source
import dev.rootiest.icons.Studio
import dev.rootiest.icons.core.NamedBytes
import dev.rootiest.icons.core.fmtG
import dev.rootiest.icons.core.switches
import dev.rootiest.icons.render.FOLDER
import dev.rootiest.icons.render.saveToDownloads
import dev.rootiest.icons.render.share
import dev.rootiest.icons.ui.theme.Latte
import dev.rootiest.icons.ui.theme.Mocha
import dev.rootiest.icons.ui.theme.palette
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// One icon or emblem: what it looks like in each variant, on which logo and where, and everything a render of it
// can be (the homepage's dialog and its Render panel).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: IconsViewModel, route: Route.Detail) {
    val studio = (vm.catalogue as? Catalogue.Ready)?.studio
    val subject = route.subject
    val ownEmblem = vm.ownEmblem.file
    val source by produceState<Result<Source>?>(null, subject, studio, if (subject is OwnEmblemSubject) ownEmblem else null) {
        value = studio?.let { s -> runCatching { s.load(subject, ownEmblem) } }
    }
    val title = source?.getOrNull()?.title ?: when (subject) {
        is IconSubject -> subject.entry.title
        is EmblemSubject -> subject.entry.title
        is IconifySubject -> subject.name.key
        OwnEmblemSubject -> ownEmblem?.name ?: "Your emblem"
        OwnLogoSubject -> "Your logo"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(painterResource(R.drawable.ic_tabler_arrow_left), contentDescription = "Back") } },
            )
        },
    ) { padding ->
        val loaded = source
        val s = loaded?.getOrNull()
        when {
            studio == null || loaded == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Could not build it: ${loaded.exceptionOrNull()?.message ?: "something went wrong"}.", color = palette.Subtext0)
            }
            else -> Detail(vm, studio, s, route, Modifier.padding(padding))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Detail(vm: IconsViewModel, studio: Studio, s: Source, route: Route.Detail, modifier: Modifier) {
    val subject = s.subject
    val logo = vm.ownLogo.logo
    val dark = previewDark(vm)
    var c by remember(s) {
        mutableStateOf(
            Choices(
                variant = when {
                    vm.variant in s.variants -> vm.variant
                    // Auto in the catalogue: an emblem or a built icon opens white or black for the preview mode
                    vm.variant == "auto" && subject !is IconSubject -> if (dark) "dark" else "light"
                    else -> "color"
                },
                whole = when (subject) {
                    is EmblemSubject -> route.mine
                    is IconifySubject -> subject.name.whole
                    else -> true
                },
                mine = route.mine && logo != null,
            ),
        )
    }
    val whole = subject is IconSubject || subject is OwnLogoSubject || c.whole
    val auto = c.variant == "auto"
    // a file that follows light or dark mode by itself: auto/, or an -auto icon
    val autoFile = auto || (subject is IconSubject && c.variant == "color" && subject.entry.name.endsWith("-auto"))
    // only what changes the picture: the output choices below do not draw it again
    val picture = c.copy(layouts = emptyList(), sizes = emptyList(), formats = emptyList(), background = null)
    val drawn by produceState<Result<Drawn>?>(null, s, picture, logo) {
        value = runCatching { studio.draw(s, c, logo, vm.ownEmblem.file) }
    }
    val d = drawn?.getOrNull()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(240.dp).swatch(c.variant, vm.previewBackground, dark), contentAlignment = Alignment.Center) {
            if (d != null) SvgImage(d.text, 192.dp, "${s.title}, ${c.variant}, ${if (dark) "dark" else "light"} mode", light = !dark)
            else drawn?.exceptionOrNull()?.let { Text(it.message ?: "It cannot be drawn", color = palette.Red, modifier = Modifier.padding(16.dp)) }
            if (d != null && switches(d.text)) {
                Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
                    if (autoFile) AutoBadge(!swatchIsLight(c.variant, vm.previewBackground, dark))
                    SchemeToggle(vm, onSwatch = true)
                }
            }
        }
        (subject as? IconSubject)?.entry?.description?.takeIf { it.isNotEmpty() }?.let { Text(it) }
        (subject as? EmblemSubject)?.entry?.description?.takeIf { it.isNotEmpty() }?.let { Text(it) }
        SettingsCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (s.details + listOfNotNull(
                    if (whole && c.mine && logo != null) "Logo" to "your file, ${logo.name}" else null,
                    s.brand?.let { "Brand color" to it + if (studio.brandDefault(s)) "" else " (too dark or light to be the default)" },
                    d?.let {
                        "Light/dark" to when {
                            auto -> "Follows it: the dark version in dark mode, the light one in light mode"
                            autoFile -> "Follows it: its colors for dark backgrounds in dark mode, for light ones in light mode"
                            switches(it.text) -> "Its emblem takes a deeper shade in light mode"
                            else -> "The same in both"
                        }
                    },
                )).forEach { (k, v) ->
                    Row {
                        Text(k, Modifier.width(96.dp), color = palette.Subtext0, style = MaterialTheme.typography.bodyMedium)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (s.standsAlone) {
            Segmented(listOf(true to "On the logo", false to "Emblem alone"), whole, Modifier.fillMaxWidth()) { c = c.copy(whole = it, padded = c.padded && it) }
        }
        if (s.variants.size > 1) {
            Segmented(s.variants.map { it to it.replaceFirstChar(Char::uppercase) }, c.variant, Modifier.fillMaxWidth()) { c = c.copy(variant = it) }
        }
        if (whole && s.emblem != null && !auto) {
            if (logo != null) {
                Segmented(listOf(false to "Rootiest logo", true to "Your logo"), c.mine, Modifier.fillMaxWidth()) {
                    // a published icon has dark and light files; built on your logo, a color-only one stays color
                    c = c.copy(mine = it)
                }
            } else {
                TextButton(onClick = { vm.open(Route.Own) }) { Text("Put it on a logo of your own…") }
            }
        }
        if (whole) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = c.padded, onCheckedChange = { c = c.copy(padded = it) })
                Text("Padded (4% margin)")
            }
        }
        s.problem?.let { Note("Its emblem cannot go on another logo or be moved: $it.", Modifier) }
        if (whole && s.emblem != null && !auto) Placement(c) { c = it }
        if (d != null && d.small.isNotEmpty()) {
            SectionHeader("Layouts")
            Note("Five layouts in one file: the emblem grows as the icon shrinks, so it still reads as a favicon.", Modifier)
            Row(
                Modifier.fillMaxWidth().swatch(c.variant, vm.previewBackground, dark).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom,
            ) {
                listOf(16, 24, 48, 96, 128).forEach { size ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SvgImage(d.text, minOf(size, 96).dp, "$size px", layout = dev.rootiest.icons.core.resolveLayout("auto", size, d.small), light = !dark)
                        Text(if (size == 128) "full" else "$size", style = MaterialTheme.typography.labelSmall, color = if (swatchIsLight(c.variant, vm.previewBackground, dark)) Latte.Subtext0 else Mocha.Subtext0)
                    }
                }
            }
        }
        if (d != null && auto) AutoFile(s, c, d.text)
        else if (d != null) {
            if (autoFile) Note(SVG_ONLY_COLOR, Modifier)
            RenderPanel(vm, s, d, c) { c = it }
        }
        Box(Modifier.height(24.dp))
    }
}

private const val SVG_ONLY = "SVG only: it switches between the dark and light versions with the viewer's mode, which a " +
    "raster cannot do. Render the dark or light version for PNG, WebP or ICO."

// an -auto icon, which has day and night versions instead
private const val SVG_ONLY_COLOR = "SVG only: it switches between its colors for dark and light backgrounds with the " +
    "viewer's mode, which a raster cannot do. For a PNG, use the day or night version; a render here draws its dark-mode colors."

// Auto in place of the Render panel: the auto/ file itself, to save or share.
@Composable
private fun AutoFile(s: Source, c: Choices, text: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = NamedBytes("${s.stem}-auto${if (c.padded) "-padded" else ""}.svg", text.toByteArray())
    SectionHeader("Download")
    SettingsCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(SVG_ONLY, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val saved = runCatching { withContext(Dispatchers.IO) { saveToDownloads(context, listOf(file)) } }
                        Toast.makeText(context, saved.fold({ "Saved ${file.name} to Downloads/$FOLDER" }, { "Could not save: ${it.message}" }), Toast.LENGTH_LONG).show()
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_tabler_download), contentDescription = null, Modifier.size(18.dp))
                    Text("  Save")
                }
                OutlinedButton(onClick = { runCatching { share(context, listOf(file), file.name.removeSuffix(".svg")) }.onFailure { Toast.makeText(context, "Could not share: ${it.message}", Toast.LENGTH_LONG).show() } }) {
                    Icon(painterResource(R.drawable.ic_tabler_share), contentDescription = null, Modifier.size(18.dp))
                    Text("  Share")
                }
            }
            Text(file.name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

// Where the emblem goes: the preset, its size and how far right and down (render-icon.py's --placement and
// --emblem-scale, -x and -y).
@Composable
private fun Placement(c: Choices, change: (Choices) -> Unit) {
    SectionHeader("Placement")
    SettingsCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Segmented(listOf("rootiest" to "Upper right", "badge" to "Badge"), c.preset, Modifier.fillMaxWidth()) { change(c.copy(preset = it)) }
            Text(
                if (c.preset == "badge") "Smaller, in the bottom-right corner: for a logo with no opening."
                else "In the logo's upper right, as the Rootiest icons have it.",
                style = MaterialTheme.typography.bodySmall, color = palette.Subtext0,
            )
            Nudge("Size", c.scale ?: 1.0, 0.2f..3f, "×") { change(c.copy(scale = if (it == 1.0) null else it)) }
            Nudge("Right", c.x, -50f..50f, "%") { change(c.copy(x = it)) }
            Nudge("Down", c.y, -50f..50f, "%") { change(c.copy(y = it)) }
            if (c.placedElsewhere) {
                TextButton(onClick = { change(c.copy(preset = "rootiest", scale = null, x = 0.0, y = 0.0)) }) { Text("Reset") }
            }
        }
    }
}

// A slider that draws again when let go, not as it moves: each change builds the icon anew. It snaps to twentieths
// of the size and to whole percents, without the tick marks steps would draw.
@Composable
private fun Nudge(label: String, value: Double, range: ClosedFloatingPointRange<Float>, unit: String, change: (Double) -> Unit) {
    var sliding by remember(value) { mutableStateOf(value.toFloat()) }
    fun snapped(v: Float) = if (unit == "×") (v * 20).roundToInt() / 20.0 else v.roundToInt().toDouble()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(56.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(sliding, { sliding = it }, Modifier.weight(1f), valueRange = range, onValueChangeFinished = { change(snapped(sliding)) })
        Text("${fmtG(snapped(sliding))}$unit", Modifier.width(56.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}
