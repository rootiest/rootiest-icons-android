package dev.rootiest.icons.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.rootiest.icons.Choices
import dev.rootiest.icons.Drawn
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.R
import dev.rootiest.icons.Source
import dev.rootiest.icons.commandLine
import dev.rootiest.icons.core.ColorSpec
import dev.rootiest.icons.core.Colors
import dev.rootiest.icons.core.ANIMATIONS
import dev.rootiest.icons.core.colorSpec
import dev.rootiest.icons.core.defaultDuration
import dev.rootiest.icons.core.fmtG
import dev.rootiest.icons.core.plannedFiles
import dev.rootiest.icons.core.stemOf
import dev.rootiest.icons.render.FOLDER
import dev.rootiest.icons.render.ICO_MAX
import dev.rootiest.icons.render.Animation
import dev.rootiest.icons.render.MAX_PX
import dev.rootiest.icons.render.Rendered
import dev.rootiest.icons.render.renderFiles
import dev.rootiest.icons.render.saveToDownloads
import dev.rootiest.icons.render.share
import dev.rootiest.icons.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun parseColor(hex: String) = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

// Everything render-rootiest-icons makes, made here: the colors, the layouts, any sizes, PNG, WebP, ICO and SVG, and
// a background to flatten onto. The files are named as the command names them, and the command is shown.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderPanel(vm: IconsViewModel, s: Source, d: Drawn, c: Choices, change: (Choices) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = (vm.catalogue as dev.rootiest.icons.Catalogue.Ready).studio.index.colors
    val small = d.small
    var sizesText by remember(s) { mutableStateOf(c.sizes.joinToString(", ")) }
    var durationText by remember(s) { mutableStateOf(c.duration?.let { fmtG(it) } ?: "") }
    val durationOk = durationText.isBlank() || durationText.trim().toDoubleOrNull()?.let { it in 0.2..60.0 } == true
    val animation = Animation(c.animate, c.loop && c.animate == "draw", c.duration)
    var results by remember(d.text, c) { mutableStateOf<List<Rendered>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val stem = stemOf(d.stemBase, d.isEmblem, c.variant, c.padded && !d.isEmblem, d.shown ?: dev.rootiest.icons.core.ColorOptions())
    val layouts = if (small.isEmpty()) listOf("auto") else c.layouts
    val files = plannedFiles(stem, layouts, c.sizes, c.formats, animation.tag)
    val problem = when {
        c.sizes.isEmpty() -> "Give at least one size, from 1 to $MAX_PX px."
        c.formats.isEmpty() -> "Pick at least one format."
        layouts.isEmpty() -> "Pick at least one layout."
        "ico" in c.formats && c.sizes.max() > ICO_MAX -> "An .ico holds sizes up to $ICO_MAX px: drop the larger sizes or ICO."
        c.background != null && parseColor(c.background) == null -> "The background is not a color like #1e1e2e."
        c.animate != "none" && "svg" !in c.formats -> "Animation plays in the SVG only: pick SVG (the other formats stay still)."
        !durationOk -> "A duration is a number of seconds from 0.2 to 60."
        else -> null
    }

    SectionHeader("Render")
    SettingsCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Colors: only the parts this icon or emblem has, in the color variant.
            val p = d.parts
            if (c.variant != "color") Text("Colors apply to the color variant.", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
            else if (!(p.logo || p.emblem || p.tone2)) Text(if (d.isEmblem) "This emblem keeps its own colors." else "Nothing here takes another color.", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
            else {
                val default = if (d.brandDefault) "Default (brand)" else if (d.built || s.brand != null) "Default" else "As published"
                val brand = s.brand?.let { ColorSpec("hex", it, "brand") }
                if (d.isEmblem) {
                    if (p.emblem) ColorRow("Emblem", c.colors.color, colors, default, brand) { change(c.copy(colors = c.colors.copy(color = it))) }
                } else {
                    if (p.logo) ColorRow(if (p.emblem) "Whole icon" else "Logo", c.colors.color, colors, default, brand) { change(c.copy(colors = c.colors.copy(color = it))) }
                    if (p.logo && p.emblem) ColorRow("Logo", c.colors.logo, colors, "—", brand) { change(c.copy(colors = c.colors.copy(logo = it))) }
                    if (p.emblem) ColorRow("Emblem", c.colors.emblem, colors, if (p.logo) "—" else default, brand) { change(c.copy(colors = c.colors.copy(emblem = it))) }
                }
                if (p.tone2) ColorRow("Second tone", c.colors.tone2, colors, "—", null, hexOnly = true) { change(c.copy(colors = c.colors.copy(tone2 = it))) }
            }
            if (small.isNotEmpty()) {
                Field("Layout") {
                    Chips(listOf("auto", "full") + small.map(Int::toString), c.layouts, mapOf("auto" to "auto")) { change(c.copy(layouts = it)) }
                }
            }
            Field("Sizes") {
                OutlinedTextField(
                    value = sizesText,
                    onValueChange = { text ->
                        sizesText = text
                        val vals = text.split(Regex("[\\s,]+")).filter(String::isNotEmpty).map { it.toIntOrNull() }
                        change(c.copy(sizes = if (vals.isNotEmpty() && vals.all { it != null && it in 1..MAX_PX }) vals.filterNotNull().distinct() else emptyList()))
                    },
                    isError = c.sizes.isEmpty(),
                    singleLine = true,
                    supportingText = { Text("px, comma-separated, up to $MAX_PX") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Field("Formats") {
                Chips(listOf("png", "webp", "ico", "svg"), c.formats, mapOf("png" to "PNG", "webp" to "WebP", "ico" to "ICO", "svg" to "SVG")) { change(c.copy(formats = it)) }
            }
            Field("Animation (SVG)") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ANIMATIONS.forEach { a ->
                            FilterChip(
                                selected = c.animate == a,
                                onClick = {
                                    change(c.copy(animate = a, loop = c.loop && a == "draw",
                                        formats = if (a != "none" && "svg" !in c.formats) c.formats + "svg" else c.formats))
                                },
                                label = { Text(mapOf("none" to "None", "draw" to "Draw", "heartbeat" to "Heartbeat").getValue(a)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.Surface0, selectedLabelColor = palette.Text),
                            )
                        }
                    }
                    if (c.animate != "none") {
                        Text(
                            if (c.animate == "draw") "The SVG draws itself, line by line, then fills in."
                            else "A bright sweep crosses the emblem, again and again, as a heart monitor draws.",
                            style = MaterialTheme.typography.bodySmall, color = palette.Subtext0,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (c.animate == "draw") {
                                Switch(checked = c.loop, onCheckedChange = { change(c.copy(loop = it)) })
                                Text("Loop", Modifier.padding(end = 8.dp))
                            }
                            OutlinedTextField(
                                value = durationText,
                                onValueChange = { text ->
                                    durationText = text
                                    val v = text.trim().toDoubleOrNull()
                                    change(c.copy(duration = if (text.isBlank() || v == null || v !in 0.2..60.0) null else v))
                                },
                                isError = !durationOk,
                                singleLine = true,
                                placeholder = { Text(fmtG(defaultDuration(c.animate, c.loop))) },
                                suffix = { Text("s") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(110.dp),
                            )
                        }
                    }
                }
            }
            Field("Background") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = c.background != null, onCheckedChange = { change(c.copy(background = if (it) "#1e1e2e" else null)) })
                    if (c.background == null) Text("Transparent", color = palette.Subtext0)
                    else {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(parseColor(c.background) ?: Color.Transparent))
                        OutlinedTextField(c.background, { change(c.copy(background = it.trim())) }, singleLine = true, isError = parseColor(c.background) == null, modifier = Modifier.width(140.dp))
                    }
                }
            }
            Text(
                problem ?: "${files.size} file${if (files.size == 1) "" else "s"}: ${files.take(3).joinToString(", ")}${if (files.size > 3) ", …" else ""}",
                style = MaterialTheme.typography.bodySmall, color = if (problem != null) palette.Red else palette.Subtext0,
            )
            Button(
                enabled = problem == null && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val made = runCatching {
                            withContext(Dispatchers.Default) {
                                renderFiles(d.text, stem, small, layouts, c.sizes, c.formats, c.background?.let { android.graphics.Color.parseColor(it) }, animation)
                            }
                        }
                        busy = false
                        results = made.getOrNull()
                        made.exceptionOrNull()?.let { Toast.makeText(context, "Render failed: ${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
                    }
                },
            ) { Text(if (busy) "Rendering…" else if (files.size > 1) "Render ${files.size} files" else "Render") }
        }
    }
    results?.let { made ->
        val all = made.map { it.file }
        val total = all.sumOf { it.bytes.size }
        SettingsCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val saved = runCatching { withContext(Dispatchers.IO) { saveToDownloads(context, all) } }
                            Toast.makeText(context, saved.fold({ "Saved $it file${if (it == 1) "" else "s"} to Downloads/$FOLDER" }, { "Could not save: ${it.message}" }), Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_tabler_download), contentDescription = null, Modifier.size(18.dp))
                        Text("  Save")
                    }
                    OutlinedButton(onClick = { runCatching { share(context, all, stem) }.onFailure { Toast.makeText(context, "Could not share: ${it.message}", Toast.LENGTH_LONG).show() } }) {
                        Icon(painterResource(R.drawable.ic_tabler_share), contentDescription = null, Modifier.size(18.dp))
                        Text(if (all.size > 1) "  Share as zip" else "  Share")
                    }
                }
                Text("${all.size} file${if (all.size == 1) "" else "s"}, ${kb(total)}. Tap one to share it alone.", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    made.forEach { r ->
                        Column(
                            Modifier.width(104.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Share ${r.file.name}") { runCatching { share(context, listOf(r.file), stem) } },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(104.dp).swatch(c.variant, vm.previewBackground, previewDark(vm)), contentAlignment = Alignment.Center) {
                                r.preview?.let { Image(it.asImageBitmap(), contentDescription = r.file.name, Modifier.size(minOf(it.width, 96).dp)) }
                            }
                            Text(r.file.name, style = MaterialTheme.typography.labelSmall, maxLines = 3)
                            Text(kb(r.file.bytes.size), style = MaterialTheme.typography.labelSmall, color = palette.Subtext0)
                        }
                    }
                }
            }
        }
    }
    Note("The same with the command line tool:", Modifier)
    val command = commandLine(d, c.copy(layouts = layouts))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.Mantle).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(command, Modifier.weight(1f).padding(vertical = 10.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        IconButton(onClick = {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("command", command))
        }) { Icon(painterResource(R.drawable.ic_tabler_copy), contentDescription = "Copy the command", Modifier.size(20.dp), tint = palette.Subtext0) }
    }
}

private fun kb(n: Int) = if (n < 1024) "$n B" else if (n < 1048576) "%.1f KB".format(n / 1024.0) else "%.1f MB".format(n / 1048576.0)

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = palette.Subtext0)
        content()
    }
}

// Several of a few choices, in the order they are listed.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(values: List<String>, selected: List<String>, names: Map<String, String>, change: (List<String>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            FilterChip(
                selected = v in selected,
                onClick = { change(values.filter { (it in selected) != (it == v) }) },
                label = { Text(names[v] ?: v) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.Surface0, selectedLabelColor = palette.Text),
            )
        }
    }
}

// One part's color: the default, a category, a status state, a brand's own, or any hex color.
@Composable
private fun ColorRow(label: String, value: ColorSpec?, colors: Colors, default: String, brand: ColorSpec?, hexOnly: Boolean = false, change: (ColorSpec?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(value != null && !value.isCategory && value.label != "brand") }
    var hex by remember { mutableStateOf(if (custom) value!!.value else "#ff8800") }
    val swatch = value?.let { parseColor(if (it.isCategory) colors.categories.getValue(it.value).getValue("Accent")[0] else it.value) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
        Box {
            OutlinedButton(onClick = { open = true }) {
                swatch?.let { Box(Modifier.size(12.dp).clip(CircleShape).background(it)) }
                Text((if (swatch != null) "  " else "") + when {
                    custom -> "Custom"
                    value == null -> default
                    else -> value.label
                })
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                @Composable
                fun item(text: String, spec: ColorSpec?, isCustom: Boolean = false) = DropdownMenuItem(
                    text = { Text(text) },
                    leadingIcon = spec?.let { sp ->
                        { Box(Modifier.size(12.dp).clip(CircleShape).background(parseColor(if (sp.isCategory) colors.categories.getValue(sp.value).getValue("Accent")[0] else sp.value) ?: Color.Transparent)) }
                    },
                    onClick = {
                        open = false
                        custom = isCustom
                        change(if (isCustom) colorSpec(colors, hex, true) else spec)
                    },
                )
                item(default, null)
                if (brand != null && !hexOnly) item("brand (${brand.value})", brand)
                if (!hexOnly) {
                    colors.categories.keys.forEach { item(it, ColorSpec("cat", it, it)) }
                    colors.states.forEach { (state, cat) -> item(state, ColorSpec("cat", cat, state)) }
                }
                item("Custom…", null, isCustom = true)
            }
        }
        if (custom) {
            OutlinedTextField(
                hex,
                { text ->
                    hex = text.trim()
                    colorSpec(colors, hex, true)?.let(change)
                },
                singleLine = true, isError = colorSpec(colors, hex, true) == null, modifier = Modifier.width(120.dp),
            )
        }
    }
}
