package dev.rootiest.icons.ui

import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.R
import dev.rootiest.icons.core.layoutsOf
import dev.rootiest.icons.core.rasterSvg
import dev.rootiest.icons.core.schemeSvg
import dev.rootiest.icons.data.PreviewBackground
import dev.rootiest.icons.render.Svg
import dev.rootiest.icons.ui.theme.Latte
import dev.rootiest.icons.ui.theme.Mocha
import dev.rootiest.icons.ui.theme.palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Small pieces shared by the screens.

// Drawn SVGs, by their text and size, so a tile scrolled back into view is not drawn again.
private val drawn = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

// An SVG at a size, in the layout a browser would show at that size (the emblem grows as the icon shrinks).
// layout: "auto", or one layout at any size. light: an SVG that switches with light or dark mode is drawn in light
// mode's colors (true) or dark mode's (false, as the rasters a render writes); null leaves that to rasterSvg (dark).
@Composable
internal fun SvgImage(svg: String?, size: Dp, description: String?, modifier: Modifier = Modifier, layout: String = "auto", tint: Color? = null, light: Boolean? = null) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, svg, px, layout, tint, light) {
        value = svg?.let { text ->
            val key = "${text.hashCode()}-${text.length}-$px-$layout-${tint?.toArgb()}-$light"
            drawn.get(key) ?: withContext(Dispatchers.Default) {
                val fixed = if (light != null) schemeSvg(text, light) else text
                runCatching { Svg.rasterize(rasterSvg(fixed, layout, size.value.toInt(), layoutsOf(fixed)), px, tint = tint?.toArgb()).asImageBitmap() }.getOrNull()
            }?.also { drawn.put(key, it) }
        }
    }
    Box(modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = description, Modifier.size(size)) }
    }
}

// Whether previews that switch with light or dark mode are drawn dark: the toggle's choice, or the app's theme.
@Composable
internal fun previewDark(vm: IconsViewModel): Boolean = vm.previewDark ?: (palette === Mocha)

// What a preview is drawn on, as the homepage: the variant's own background, or the one chosen in Settings. Auto and
// color previews follow the light or dark preview mode (dark: whether it is dark).
internal fun swatchColor(variant: String, background: PreviewBackground, dark: Boolean): Color? = when (background) {
    PreviewBackground.Dark -> Mocha.Base
    PreviewBackground.Light -> Color.White
    PreviewBackground.Checker -> null
    PreviewBackground.Auto -> when (variant) {
        "dark" -> Mocha.Base
        "light" -> Color.White
        "auto" -> if (dark) Mocha.Base else Color.White
        else -> if (dark) Mocha.Mantle else Latte.Mantle
    }
}

// Whether a swatch is light (the checkerboard is), for text drawn on it.
internal fun swatchIsLight(variant: String, background: PreviewBackground, dark: Boolean): Boolean =
    swatchColor(variant, background, dark)?.let { it == Color.White || it == Latte.Mantle } ?: true

@Composable
internal fun Modifier.swatch(variant: String, background: PreviewBackground, dark: Boolean): Modifier {
    val color = swatchColor(variant, background, dark)
    val shape = RoundedCornerShape(10.dp)
    if (color != null) return clip(shape).background(color)
    val a = Latte.Surface0
    val b = Color.White
    return clip(shape).drawBehind {
        val cell = 8.dp.toPx()
        drawRect(b)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(a, Offset(x, y), Size(cell, cell))
                x += 2 * cell
            }
            y += cell
            row++
        }
    }
}

// The light or dark preview toggle: a sun while previewing light, a moon while previewing dark.
@Composable
internal fun SchemeToggle(vm: IconsViewModel, modifier: Modifier = Modifier, onSwatch: Boolean = false) {
    val dark = previewDark(vm)
    val tint = if (!onSwatch) palette.Subtext0 else if (dark) Mocha.Subtext0 else Latte.Subtext0
    IconButton(onClick = { vm.togglePreview(dark) }, modifier) {
        Icon(
            painterResource(if (dark) R.drawable.ic_tabler_moon else R.drawable.ic_tabler_sun),
            contentDescription = if (dark) "Previewing in dark mode: show light" else "Previewing in light mode: show dark",
            Modifier.size(22.dp), tint = tint,
        )
    }
}

// The mark on a preview of a file that follows light or dark mode by itself (auto/, -auto).
@Composable
internal fun AutoBadge(dark: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.padding(4.dp).clip(CircleShape).background(if (dark) Mocha.Surface0 else Latte.Surface0).padding(3.dp)) {
        Icon(painterResource(R.drawable.ic_tabler_brightness_auto), contentDescription = "Follows light or dark mode",
            Modifier.size(14.dp), tint = if (dark) Mocha.Text else Latte.Text)
    }
}

@Composable
internal fun SectionTitle(text: String, count: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = palette.Text)
        count?.let { Text("  $it", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0) }
    }
}

// A list section's title, opaque so rows scroll under it.
@Composable
internal fun SectionHeader(text: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(top = 16.dp, bottom = 8.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = palette.Subtext0)
    }
}

// One of a few choices, side by side.
@Composable
internal fun <T> Segmented(choices: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier) {
        choices.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = palette.Surface0, activeContentColor = palette.Text,
                    inactiveContainerColor = Color.Transparent, inactiveContentColor = palette.Subtext0,
                    activeBorderColor = palette.Surface1, inactiveBorderColor = palette.Surface1,
                ),
            ) { Text(label, maxLines = 1) }
        }
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = palette.Subtext0, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0, modifier = modifier)
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
    }
}

@Composable
internal fun Divider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = palette.Surface0)
}

// A title over a smaller summary, after an icon (Tabler Icons outlines, as in other Rootiest projects); tappable when it
// has an onClick.
@Composable
internal fun SettingRow(title: String, summary: String, @DrawableRes icon: Int, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(24.dp), tint = palette.Subtext0)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = palette.Text)
            Spacer(Modifier.height(2.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
        }
    }
}

internal class Choice<T>(val value: T, val label: String, val summary: String? = null)

// Radio buttons in a card.
@Composable
internal fun <T> ChoiceCard(choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = palette.Mantle)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).selectableGroup()) {
            choices.forEach { choice ->
                // the whole row is the target, so a tap on the label works too
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = choice.value == selected, role = Role.RadioButton, onClick = { onSelect(choice.value) })
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = choice.value == selected, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(choice.label)
                        choice.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0) }
                    }
                }
            }
        }
    }
}
