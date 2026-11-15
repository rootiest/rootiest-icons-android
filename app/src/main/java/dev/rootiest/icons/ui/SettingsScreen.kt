package dev.rootiest.icons.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.rootiest.icons.BuildConfig
import dev.rootiest.icons.Catalogue
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.R
import dev.rootiest.icons.Route
import dev.rootiest.icons.data.Appearance
import dev.rootiest.icons.data.PreviewBackground
import dev.rootiest.icons.data.SITE

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: IconsViewModel) {
    val context = LocalContext.current
    fun browse(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(painterResource(R.drawable.ic_tabler_arrow_left), contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item { SectionHeader("Appearance") }
            item {
                ChoiceCard(
                    listOf(
                        Choice(Appearance.System, "System default", "Light or dark, as the phone is"),
                        Choice(Appearance.Light, "Light", "Catppuccin Latte"),
                        Choice(Appearance.Dark, "Dark", "Catppuccin Mocha"),
                    ),
                    vm.appearance, vm::changeAppearance,
                )
            }
            item { SectionHeader("Preview background") }
            item {
                ChoiceCard(
                    listOf(
                        Choice(PreviewBackground.Auto, "Each variant's own", "Dark icons on dark, light icons on light"),
                        Choice(PreviewBackground.Dark, "Dark"),
                        Choice(PreviewBackground.Light, "Light"),
                        Choice(PreviewBackground.Checker, "Checkerboard", "Shows what is transparent"),
                    ),
                    vm.previewBackground, vm::changePreviewBackground,
                )
            }
            item { SectionHeader("Icons") }
            item {
                SettingsCard {
                    val ready = vm.catalogue as? Catalogue.Ready
                    SettingRow(
                        "Refresh the catalogue",
                        ready?.studio?.index?.let { "${it.icons.size} icons and ${it.emblems.size} emblems, from ${SITE.removePrefix("https://")}" } ?: "Not loaded",
                        R.drawable.ic_tabler_refresh, onClick = vm::refresh,
                    )
                    Divider()
                    SettingRow("Your own logo and emblem", listOfNotNull(vm.ownLogo.file?.name, vm.ownEmblem.file?.name).joinToString(", ").ifEmpty { "None loaded" },
                        R.drawable.ic_tabler_photo_up) { vm.open(Route.Own) }
                }
            }
            item { SectionHeader("About") }
            item {
                SettingsCard {
                    SettingRow("Rootiest Icons", "Version ${BuildConfig.VERSION_NAME}", R.drawable.ic_tabler_info_circle)
                    Divider()
                    SettingRow("icons.rootiest.fyi", "The same icons, and the same Render panel, in a browser", R.drawable.ic_tabler_world) { browse(SITE) }
                    Divider()
                    SettingRow("Command line tool", "render-rootiest-icons: every render here shows its command", R.drawable.ic_tabler_code) { browse("$SITE/#render") }
                    Divider()
                    SettingRow("Open-source licenses", "The libraries and icon sets the app is built with", R.drawable.ic_tabler_license) { vm.open(Route.Licenses) }
                }
            }
            item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Note("Icons from other sets come through Iconify, each under its set's own license. Brand logos belong to their owners.", Modifier.padding(4.dp))
                }
            }
        }
    }
}
