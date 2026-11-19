package dev.rootiest.icons.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.rootiest.icons.Catalogue
import dev.rootiest.icons.EmblemSubject
import dev.rootiest.icons.IconSubject
import dev.rootiest.icons.IconifySubject
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.R
import dev.rootiest.icons.Route
import dev.rootiest.icons.core.EmblemEntry
import dev.rootiest.icons.core.IconEntry
import dev.rootiest.icons.core.Index
import dev.rootiest.icons.core.MONO
import dev.rootiest.icons.core.recolor
import dev.rootiest.icons.ui.theme.palette

private fun String.hexColor() = Color(android.graphics.Color.parseColor(this))

private fun matches(query: String, category: String, vararg fields: String?, cats: List<String?>): Boolean {
    if (category != "all" && category !in cats) return false
    val q = query.trim().lowercase()
    return q.isEmpty() || fields.any { it != null && q in it.lowercase() }
}

// The catalogue: every icon, status icon and emblem, filtered by the search box and the category, and what the
// other sets have for the search. The search, variant and category stay at the top while the icons scroll.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogueScreen(vm: IconsViewModel) {
    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = { Text("Rootiest Icons") },
                    actions = {
                        IconButton(onClick = { vm.open(Route.Article) }) { Icon(painterResource(R.drawable.ic_tabler_vector_bezier_2), contentDescription = "Drawn with Math: why SVG") }
                        IconButton(onClick = { vm.open(Route.Own) }) { Icon(painterResource(R.drawable.ic_tabler_photo_up), contentDescription = "Your own logo and emblem") }
                        IconButton(onClick = { vm.open(Route.Settings) }) { Icon(painterResource(R.drawable.ic_tabler_settings), contentDescription = "Settings") }
                    },
                )
                (vm.catalogue as? Catalogue.Ready)?.let { Filters(vm, it.studio.index) }
            }
        },
    ) { padding ->
        when (val c = vm.catalogue) {
            Catalogue.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Catalogue.Failed -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("The catalogue could not be loaded.", style = MaterialTheme.typography.titleMedium)
                Text(c.why, color = palette.Subtext0, textAlign = TextAlign.Center)
                Button(onClick = vm::refresh) { Text("Try again") }
            }
            is Catalogue.Ready -> Grid(vm, c.studio.index, padding)
        }
    }
}

@Composable
private fun Grid(vm: IconsViewModel, index: Index, padding: PaddingValues) {
    val icons = index.icons.filter { it.kind in setOf("logo", "service", "variant") }
    val status = index.icons.filter { it.kind in setOf("status", "badge") }
    fun shown(list: List<IconEntry>) = list.filter { matches(vm.query, vm.category, it.name, it.title, it.description, it.category, it.state, it.emblem, cats = listOf(it.category, it.state)) }
    val emblems = index.emblems.filter { matches(vm.query, vm.category, it.name, it.title, it.description, cats = emptyList()) }
    val shownIcons = shown(icons)
    val shownStatus = shown(status)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        if (vm.query.isBlank()) full("article") { ArticleCard { vm.open(Route.Article) } }
        section("Icons", shownIcons.size, icons.size, "The logo with an emblem for each service. Tap one for its sizes, colors and files.")
        items(shownIcons, key = { "icon-${it.name}" }) { IconTile(vm, it) }
        section("Status icons and badges", shownStatus.size, status.size, null)
        items(shownStatus, key = { "icon-${it.name}" }) { IconTile(vm, it) }
        section("Emblems", emblems.size, index.emblems.size, "The marks on their own. Dark and light show them in white and in black.")
        items(emblems, key = { "emblem-${it.name}" }) { EmblemTile(vm, it) }
        if (vm.query.isNotBlank()) {
            full("other") {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionTitle("From other sets")
                    Text(
                        vm.other.status.ifEmpty { "Tabler, Material, Simple Icons and some 200 more, through Iconify: on the logo or alone." },
                        style = MaterialTheme.typography.bodySmall, color = palette.Subtext0,
                    )
                }
            }
            items(vm.other.names, key = { "other-$it" }) { name -> OtherTile(vm, name) }
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun LazyGridScope.section(title: String, shown: Int, total: Int, about: String?) {
    if (shown == 0) return
    full("section-$title") {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionTitle(title, if (shown == total) "$total" else "$shown of $total")
            about?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0) }
        }
    }
}

// Drawn with Math, the site's interactive article on SVG, opened in the app.
@Composable
private fun ArticleCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(palette.Mantle)
            .clickable(onClickLabel = "Open Drawn with Math", onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_tabler_vector_bezier_2), contentDescription = null, Modifier.size(28.dp), tint = palette.AccentText)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("Drawn with Math", style = MaterialTheme.typography.titleMedium, color = palette.Text)
            Text("Why we like SVG: an interactive tour of how it works", style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
        }
    }
}

@Composable
private fun Filters(vm: IconsViewModel, index: Index) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::search,
            placeholder = { Text("Search these and 200 other sets", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_tabler_search), contentDescription = null, Modifier.size(20.dp)) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) { Icon(painterResource(R.drawable.ic_tabler_x), contentDescription = "Clear the search", Modifier.size(20.dp)) }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("color" to "Color", "auto" to "Auto", "dark" to "Dark", "light" to "Light"), vm.variant, Modifier.weight(1f)) { vm.variant = it }
            SchemeToggle(vm)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("all") + index.colors.categories.keys.filter { it != "neutral" }).forEach { cat ->
                FilterChip(
                    selected = vm.category == cat,
                    onClick = { vm.category = cat },
                    label = { Text(if (cat == "all") "All" else cat) },
                    leadingIcon = if (cat == "all") null else {
                        { Box(Modifier.size(10.dp).clip(CircleShape).background(index.colors.categories.getValue(cat).getValue("Accent")[0].hexColor())) }
                    },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = palette.Surface0, selectedLabelColor = palette.Text),
                )
            }
        }
    }
}

// dark: the preview mode it is drawn in; badge: it shows a file that follows light or dark mode by itself.
@Composable
// A tile opens its icon; a tap on its name copies the name instead (copy: as render-rootiest-icons and the morph's
// "by name" take it).
private fun Tile(svg: String?, variant: String, vm: IconsViewModel, name: String, title: String, copy: String, faded: Boolean = false, dark: Boolean = previewDark(vm), badge: Boolean = false, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Open $title", onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(92.dp).swatch(variant, vm.previewBackground, dark), contentAlignment = Alignment.Center) {
            SvgImage(svg, 64.dp, null, light = !dark)
            if (badge) AutoBadge(swatchIsLight(variant, vm.previewBackground, dark).not(), Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClickLabel = "Copy the name $copy") {
                context.getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("name", copy))
                // Android 13 and later confirm a copy themselves
                if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(context, "Copied $copy", android.widget.Toast.LENGTH_SHORT).show()
            }.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium, color = if (faded) palette.Subtext0 else palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(title, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IconTile(vm: IconsViewModel, e: IconEntry) {
    // an icon made in color only shows that one, on the color background
    val variant = when {
        vm.variant == "auto" -> if (e.auto) "auto" else "color"
        vm.variant in e.variants -> vm.variant
        else -> "color"
    }
    val svg by produceState<String?>(null, e.name, variant) { value = runCatching { vm.repo.svg("$variant/${e.name}.svg") }.getOrNull() }
    Tile(svg, variant, vm, e.name.removePrefix("rootiest-"), e.title, e.name, faded = variant != vm.variant,
        badge = variant == "auto" || (variant == "color" && e.name.endsWith("-auto"))) { vm.open(Route.Detail(IconSubject(e))) }
}

@Composable
private fun EmblemTile(vm: IconsViewModel, e: EmblemEntry) {
    val variant = vm.variant
    // Auto: white or black for the preview mode, as the auto version (dark and light in one file) shows there
    val dark = previewDark(vm)
    val mono = if (variant == "auto") (if (dark) "dark" else "light") else variant
    val svg by produceState<String?>(null, e.name, mono) {
        value = runCatching {
            val text = vm.repo.svg("emblems/${e.name}.svg")
            if (mono == "color" || e.keepFill) text else MONO.getValue(mono).let { recolor(text, it.first, it.second) }
        }.getOrNull()
    }
    Tile(svg, variant, vm, e.name, e.title, e.name, dark = dark, badge = variant == "auto" && !e.keepFill) { vm.open(Route.Detail(EmblemSubject(e))) }
}

// An icon from another set, as its set draws it, in the text color; opens on the logo.
@Composable
private fun OtherTile(vm: IconsViewModel, name: String) {
    val text = palette.Text
    val hex = "#%06x".format(0xFFFFFF and android.graphics.Color.argb(255, (text.red * 255).toInt(), (text.green * 255).toInt(), (text.blue * 255).toInt()))
    val svg = vm.other.bodies[name]?.replace("currentColor", hex)?.replaceFirst("<svg ", "<svg color=\"$hex\" fill=\"$hex\" ")
    // drawn in the app's text color, so on the app theme's background whatever the preview mode
    Tile(svg, "color", vm, name.substringAfter(':'), name.substringBefore(':'), name, dark = palette === dev.rootiest.icons.ui.theme.Mocha) {
        vm.external("rootiest:$name")?.let { vm.open(Route.Detail(IconifySubject(it), mine = vm.ownLogo.logo != null)) }
    }
}
