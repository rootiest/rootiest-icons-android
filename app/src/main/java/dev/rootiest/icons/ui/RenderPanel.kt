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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.produceState
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
import dev.rootiest.icons.EmblemSubject
import dev.rootiest.icons.IconSubject
import dev.rootiest.icons.IconifySubject
import dev.rootiest.icons.OwnEmblemSubject
import dev.rootiest.icons.OwnLogoSubject
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.R
import dev.rootiest.icons.Source
import dev.rootiest.icons.commandLine
import dev.rootiest.icons.core.ColorSpec
import dev.rootiest.icons.core.Colors
import dev.rootiest.icons.core.ANIMATIONS
import dev.rootiest.icons.core.FPS
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

// The choices' animation, with a morph's target drawn (null until it is) and named as --morph-to takes it, the
// published icons it can morph into, why its target could not be drawn, and whether your emblem file can be one.
internal data class Moving(
    val animation: Animation, val targets: List<String>, val failed: String? = null,
    val ownTarget: String? = null,
)

@Composable
internal fun rememberMoving(vm: IconsViewModel, s: Source, c: Choices, d: Drawn?): Moving {
    val studio = (vm.catalogue as dev.rootiest.icons.Catalogue.Ready).studio
    // Morphing here, as render-icon.py's morph_target() builds the target: another icon (an emblem, for an emblem)
    // built as this one is, in the same variant, padding, colors, logo and placement. A published one, an icon from
    // another set by name, or your emblem file; a published icon is its file unless your logo or another placement
    // has it built here (Studio.draw decides, as for this icon). In auto, a published target needs its auto/ file.
    val subject = s.subject
    val whole = subject is IconSubject || subject is OwnLogoSubject || c.whole
    val logo = vm.ownLogo.logo
    val ownEmblem = vm.ownEmblem.file
    val rebuilt = (whole && (c.mine || subject is OwnLogoSubject) && logo != null) || (whole && c.placedElsewhere)
    val targets = when {
        whole -> studio.index.icons.filter {
            !(subject is IconSubject && it.name == subject.entry.name) && (if (c.variant == "auto") it.auto else c.variant in it.variants) &&
                (!c.padded || it.padded) &&
                (!rebuilt || (it.build != null && !it.build!!.colorOnly))
        }.map { it.name }
        else -> studio.index.emblems.filter { !(subject is EmblemSubject && it.name == subject.entry.name) }.map { it.name }
    }
    val ownTarget = ownEmblem?.name?.takeIf { subject !is OwnEmblemSubject }
    // the colors this icon shows (its brand's, when that is the default) go to the target too; with none, the target
    // takes its own brand color, as render-icon.py resolves each
    val colors = d?.shown?.takeIf { c.variant == "color" } ?: c.colors
    val drawnTarget by produceState<Result<Drawn>?>(null, c.morphTo, c.morphKind, c.animate, c.variant, c.padded, colors, c.mine,
        c.whole, c.preset, c.scale, c.x, c.y, logo, ownEmblem) {
        value = null
        val name = c.morphTo
        if (c.animate != "morph" || name == null) return@produceState
        value = runCatching {
            val ts = when (c.morphKind) {
                "iconify" -> IconifySubject(vm.external(name) ?: throw IllegalArgumentException("$name is not a set and an icon, like tabler:heart"))
                "own" -> OwnEmblemSubject
                else -> if (whole) IconSubject(studio.index.icons.first { it.name == name })
                    else EmblemSubject(studio.index.emblems.first { it.name == name })
            }
            studio.draw(studio.load(ts, ownEmblem), c.copy(whole = whole, mine = c.mine || subject is OwnLogoSubject, colors = colors), logo, ownEmblem)
        }
    }
    val target = drawnTarget?.getOrNull()
    val looping = c.animate == "draw" || c.animate == "morph"
    val name = target?.args?.firstOrNull()
    return Moving(Animation(c.animate, c.loop && looping, c.duration, name.takeIf { c.animate == "morph" }, target?.text), targets,
        drawnTarget?.exceptionOrNull()?.let { "Could not draw ${c.morphTo}: ${it.message ?: it.javaClass.simpleName}" }, ownTarget)
}

// Everything render-rootiest-icons makes, made here: the colors, the layouts, any sizes, PNG, WebP, ICO, SVG and GIF,
// an animation, and a background to flatten onto. The files are named as the command names them, and the command is shown.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderPanel(vm: IconsViewModel, s: Source, d: Drawn, c: Choices, moving: Moving, change: (Choices) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = (vm.catalogue as dev.rootiest.icons.Catalogue.Ready).studio.index.colors
    val small = d.small
    var sizesText by remember(s) { mutableStateOf(c.sizes.joinToString(", ")) }
    var durationText by remember(s) { mutableStateOf(c.duration?.let { fmtG(it) } ?: "") }
    val durationOk = durationText.isBlank() || durationText.trim().toDoubleOrNull()?.let { it in 0.2..60.0 } == true
    val (animation, targets) = moving
    // auto: dark and light in one SVG, which a raster cannot be, so an SVG is all it makes (render-icon.py)
    val svgOnly = c.variant == "auto"
    val formats = if (svgOnly) listOf("svg") else c.formats
    val previewLight = !previewDark(vm) // the results drawn in the mode the previews show
    val looping = c.animate == "draw" || c.animate == "morph"
    var results by remember(d.text, c) { mutableStateOf<List<Rendered>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val stem = stemOf(d.stemBase, d.isEmblem, c.variant, c.padded && !d.isEmblem, d.shown ?: dev.rootiest.icons.core.ColorOptions())
    val layouts = if (small.isEmpty()) listOf("auto") else c.layouts
    val files = plannedFiles(stem, layouts, c.sizes, formats, animation.tag)
    val problem = when {
        !svgOnly && c.sizes.isEmpty() -> "Give at least one size, from 1 to $MAX_PX px."
        formats.isEmpty() -> "Pick at least one format."
        layouts.isEmpty() -> "Pick at least one layout."
        "ico" in formats && c.sizes.max() > ICO_MAX -> "An .ico holds sizes up to $ICO_MAX px: drop the larger sizes or ICO."
        !svgOnly && c.background != null && parseColor(c.background) == null -> "The background is not a color like #1e1e2e."
        c.animate != "none" && formats.none { it in setOf("svg", "webp", "gif") } -> "Animation plays in the SVG, WebP and GIF: pick one of them."
        c.animate == "morph" && c.morphTo == null -> "Pick an icon to morph into."
        c.animate == "morph" && moving.failed != null -> moving.failed
        c.animate == "morph" && animation.target == null -> "Getting ${c.morphTo}…"
        !durationOk -> "A duration is a number of seconds from 0.2 to 60."
        else -> null
    }

    SectionHeader("Render")
    SettingsCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Colors: only the parts this icon or emblem has, in the color and auto variants (auto also takes a pair,
            // one color for dark backgrounds and one for light ones).
            val p = d.parts
            val auto = c.variant == "auto"
            if (c.variant != "color" && !auto) Text("Colors apply to the color and auto variants.", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
            else if (!(p.logo || p.emblem || p.tone2)) Text(if (d.isEmblem) "This emblem keeps its own colors." else "Nothing here takes another color.", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
            else {
                val default = if (d.brandDefault) "Default (brand)" else if (d.built || s.brand != null) "Default" else "As published"
                val brand = s.brand?.let { ColorSpec("hex", it, "brand") }
                if (d.isEmblem) {
                    if (p.emblem) ColorRow("Emblem", c.colors.color, colors, default, brand, auto) { change(c.copy(colors = c.colors.copy(color = it))) }
                } else {
                    if (p.logo) ColorRow(if (p.emblem) "Whole icon" else "Logo", c.colors.color, colors, default, brand, auto) { change(c.copy(colors = c.colors.copy(color = it))) }
                    if (p.logo && p.emblem) ColorRow("Logo", c.colors.logo, colors, "—", brand, auto) { change(c.copy(colors = c.colors.copy(logo = it))) }
                    if (p.emblem) ColorRow("Emblem", c.colors.emblem, colors, if (p.logo) "—" else default, brand, auto) { change(c.copy(colors = c.colors.copy(emblem = it))) }
                }
                if (p.tone2) ColorRow("Second tone", c.colors.tone2, colors, "—", null, auto, hexOnly = true) { change(c.copy(colors = c.colors.copy(tone2 = it))) }
                if (auto) Text("In auto, one color is drawn a little lighter in dark mode and deeper in light mode; a pair is used exactly.",
                    style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
            }
            if (small.isNotEmpty()) {
                Field("Layout") {
                    Chips(listOf("auto", "full") + small.map(Int::toString), c.layouts, mapOf("auto" to "auto")) { change(c.copy(layouts = it)) }
                }
            }
            if (svgOnly) {
                Text("SVG only: auto switches between the dark and light versions with the viewer's mode, which a raster " +
                    "cannot do. Pick dark or light for PNG, WebP, ICO or GIF.", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
            } else Field("Sizes") {
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
            if (!svgOnly) Field("Formats") {
                Chips(listOf("png", "webp", "ico", "svg", "gif"), c.formats, mapOf("png" to "PNG", "webp" to "WebP", "ico" to "ICO", "svg" to "SVG", "gif" to "GIF")) { change(c.copy(formats = it)) }
                if (c.animate != "none" && ("webp" in c.formats || "gif" in c.formats)) {
                    Text("WebP and GIF are animated, $FPS frames a second; PNG and ICO stay still.", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
                }
            }
            Field("Animation") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ANIMATIONS.forEach { a ->
                            FilterChip(
                                selected = c.animate == a,
                                onClick = {
                                    change(c.copy(animate = a, loop = c.loop && (a == "draw" || a == "morph"),
                                        formats = if (a != "none" && c.formats.none { it in setOf("svg", "webp", "gif") }) c.formats + "svg" else c.formats))
                                },
                                label = { Text(a.replaceFirstChar(Char::uppercase)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.Surface0, selectedLabelColor = palette.Text),
                            )
                        }
                    }
                    if (c.animate != "none") {
                        Text(
                            when (c.animate) {
                                "draw" -> "The SVG draws itself, line by line, then fills in."
                                "heartbeat" -> "A bright sweep crosses the emblem, again and again, as a heart monitor draws."
                                "pulse" -> "The emblem breathes: it grows a little and back, again and again."
                                "spin" -> "The emblem turns, again and again (best for round ones)."
                                "shake" -> "The emblem wobbles, then rests, again and again."
                                "pop" -> "The emblem bounces in, once."
                                "morph" -> "It turns into another icon: the emblem's shapes morph, the rest cross-fades; once, or there and back."
                                else -> "A white glint crosses the emblem now and then."
                            },
                            style = MaterialTheme.typography.bodySmall, color = palette.Subtext0,
                        )
                        if (c.animate == "morph") {
                            var open by remember { mutableStateOf(false) }
                            var asking by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { open = true }) { Text(c.morphTo?.let { "Into ${it.removePrefix("rootiest-")}" } ?: "Morph into…") }
                                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                                    DropdownMenuItem(text = { Text("By name, from another set…") }, onClick = { open = false; asking = true })
                                    moving.ownTarget?.let { f ->
                                        DropdownMenuItem(text = { Text("Your emblem file, $f") }, onClick = { open = false; change(c.copy(morphTo = f, morphKind = "own")) })
                                    }
                                    HorizontalDivider()
                                    targets.forEach { t ->
                                        DropdownMenuItem(text = { Text(t.removePrefix("rootiest-")) }, onClick = { open = false; change(c.copy(morphTo = t, morphKind = "published")) })
                                    }
                                }
                            }
                            if (asking) MorphByName(c.morphTo.takeIf { c.morphKind == "iconify" } ?: "", { asking = false }) { name ->
                                asking = false
                                change(c.copy(morphTo = name, morphKind = "iconify"))
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (looping) {
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
            if (!svgOnly) Field("Background") {
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
                                renderFiles(d.text, stem, small, layouts, c.sizes, formats, c.background?.takeIf { !svgOnly }?.let { android.graphics.Color.parseColor(it) },
                                    animation, previewLight = if (svgOnly) previewLight else null)
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
                                val side = minOf(r.preview?.width ?: 96, 96).dp
                                if (r.animated != null) AnimatedImage(r.animated, side, r.file.name)
                                else r.preview?.let { Image(it.asImageBitmap(), contentDescription = r.file.name, Modifier.size(side)) }
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
    val command = commandLine(d, c.copy(layouts = layouts), animation.morphTo)
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

// One part's color: the default, a category, a status state, a brand's own, or any hex color; in the auto variant
// (pairs) also a dark/light pair, one color for dark backgrounds and one for light ones, as `cdd6f4/4c4f69`.
@Composable
private fun ColorRow(label: String, value: ColorSpec?, colors: Colors, default: String, brand: ColorSpec?, pairs: Boolean, hexOnly: Boolean = false, change: (ColorSpec?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // what the row edits: a hex color of its own, or a pair; remembered per variant, so leaving auto (which drops a
    // pair) starts the row again from its value
    var custom by remember(pairs) { mutableStateOf(value != null && value.kind == "hex" && value.label != "brand") }
    var pair by remember(pairs) { mutableStateOf(pairs && value?.isPair == true) }
    var hex by remember { mutableStateOf(if (custom) value!!.value else "#ff8800") }
    var dark by remember { mutableStateOf(if (pair) value!!.value else "#cdd6f4") }
    var light by remember { mutableStateOf(if (pair) value!!.light!! else "#4c4f69") }
    fun pairSpec() = colorSpec(colors, "$dark/$light")
    fun dot(spec: ColorSpec) = parseColor(if (spec.isCategory) colors.categories.getValue(spec.value).getValue("Accent")[0] else spec.value)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium)
            Box {
                OutlinedButton(onClick = { open = true }) {
                    value?.let(::dot)?.let { Box(Modifier.size(12.dp).clip(CircleShape).background(it)) }
                    if (value?.isPair == true) parseColor(value.light!!)?.let { Box(Modifier.padding(start = 2.dp).size(12.dp).clip(CircleShape).background(it)) }
                    Text((if (value != null) "  " else "") + when {
                        pair -> "Dark/light pair"
                        custom -> "Custom"
                        value == null -> default
                        else -> value.label
                    })
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    @Composable
                    fun item(text: String, spec: ColorSpec?, mode: String = "") = DropdownMenuItem(
                        text = { Text(text) },
                        leadingIcon = spec?.let { sp -> { Box(Modifier.size(12.dp).clip(CircleShape).background(dot(sp) ?: Color.Transparent)) } },
                        onClick = {
                            open = false
                            custom = mode == "custom"
                            pair = mode == "pair"
                            change(when (mode) {
                                "custom" -> colorSpec(colors, hex, true)
                                "pair" -> pairSpec()
                                else -> spec
                            })
                        },
                    )
                    item(default, null)
                    if (brand != null && !hexOnly) item("brand (${brand.value})", brand)
                    if (!hexOnly) {
                        colors.categories.keys.forEach { item(it, ColorSpec("cat", it, it)) }
                        colors.states.forEach { (state, cat) -> item(state, ColorSpec("cat", cat, state)) }
                    }
                    item("Custom…", null, "custom")
                    if (pairs) item("Dark/light pair…", null, "pair")
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
        if (pair) {
            // one color for dark backgrounds, one for light ones, each a hex color (the # optional)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((side, text) in listOf("On dark" to dark, "On light" to light)) {
                    OutlinedTextField(
                        text,
                        { t ->
                            if (side == "On dark") dark = t.trim() else light = t.trim()
                            pairSpec()?.let(change)
                        },
                        label = { Text(side) },
                        leadingIcon = colorSpec(colors, text, true)?.let { parseColor(it.value) }?.let { col ->
                            { Box(Modifier.size(14.dp).clip(CircleShape).background(col)) }
                        },
                        singleLine = true, isError = colorSpec(colors, text, true) == null, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// The morph's target by name: an icon from another set, set:icon (rootiest: before it is taken off).
@Composable
private fun MorphByName(start: String, dismiss: () -> Unit, pick: (String) -> Unit) {
    var text by remember { mutableStateOf(start) }
    val name = text.trim().removePrefix("rootiest:")
    val ok = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*:[a-z0-9]+(?:-[a-z0-9]+)*$").matches(name)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Morph into") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("An icon from another set, as set:icon. Tap a name in the search results to copy it.",
                    style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, placeholder = { Text("tabler:heart") },
                    isError = text.isNotBlank() && !ok,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None))
            }
        },
        confirmButton = { TextButton(onClick = { pick(name) }, enabled = ok) { Text("Morph") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
