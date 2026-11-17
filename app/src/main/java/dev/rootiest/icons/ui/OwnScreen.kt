package dev.rootiest.icons.ui

import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.rootiest.icons.IconsViewModel
import dev.rootiest.icons.OwnEmblemSubject
import dev.rootiest.icons.OwnLogoSubject
import dev.rootiest.icons.OwnState
import dev.rootiest.icons.R
import dev.rootiest.icons.Route
import dev.rootiest.icons.core.manyColors
import dev.rootiest.icons.data.OwnFile
import dev.rootiest.icons.data.PreviewBackground
import dev.rootiest.icons.readSvg
import dev.rootiest.icons.ui.theme.Latte
import dev.rootiest.icons.ui.theme.Mocha
import dev.rootiest.icons.ui.theme.palette

// Your own logo and emblem: an SVG each, from a file or the clipboard. They stay on the phone.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnScreen(vm: IconsViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your own") },
                navigationIcon = { IconButton(onClick = vm::back) { Icon(painterResource(R.drawable.ic_tabler_arrow_left), contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "An SVG of your own as the emblem, as the logo, or both. Any emblem here (published, from another set, or yours) goes on " +
                    "your logo, built exactly as render-rootiest-icons --logo builds it. Nothing leaves the phone.",
                color = palette.Subtext0, style = MaterialTheme.typography.bodyMedium,
            )
            SectionHeader("Logo")
            Zone(vm, "logo", vm.ownLogo, "Takes the Rootiest logo's place. With a logo here, every icon can be shown on yours.") { vm.open(Route.Detail(OwnLogoSubject, mine = true)) }
            SectionHeader("Emblem")
            Zone(vm, "emblem", vm.ownEmblem, "Goes on a logo, or stands alone, like any emblem here.") { vm.open(Route.Detail(OwnEmblemSubject, mine = vm.ownLogo.logo != null)) }
            Note("Flat shapes only: no gradients, masks or text. They are recolored like every emblem unless you keep their colors.", Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun Zone(vm: IconsViewModel, what: String, state: OwnState, about: String, open: () -> Unit) {
    val context = LocalContext.current
    fun take(file: OwnFile?) {
        if (file == null || !Regex("<svg[\\s>]").containsMatchIn(file.raw)) {
            Toast.makeText(context, "That is not an SVG", Toast.LENGTH_SHORT).show()
            return
        }
        vm.setOwn(what, file)
        if (what == "emblem") open()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) take(context.readSvg(uri)) }
    SettingsCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(about, style = MaterialTheme.typography.bodySmall, color = palette.Subtext0)
            val f = state.file
            if (f == null) Text("Nothing loaded", color = palette.Subtext0)
            else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(64.dp).swatch("color", vm.previewBackground, palette === Mocha), contentAlignment = Alignment.Center) {
                        // the file as it is: what it leaves to currentColor or the default black shows in the swatch's text color
                        val tint = when (vm.previewBackground) {
                            PreviewBackground.Dark -> Mocha.Text
                            PreviewBackground.Light -> Latte.Text
                            else -> palette.Text
                        }
                        if (state.problem == null) SvgImage(f.raw, 48.dp, null, tint = tint)
                    }
                    Column {
                        Text(f.name)
                        if (state.problem != null) Text("${state.problem}.", color = palette.Red, style = MaterialTheme.typography.bodySmall)
                        else if (manyColors(f.raw) && !f.keep) Text("It has several colors: they become one unless kept.", color = palette.Subtext0, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = f.keep, onCheckedChange = { vm.setOwn(what, f.copy(keep = it)) })
                    Text("Keep its colors")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = open, enabled = state.problem == null) { Text("Open") }
                    TextButton(onClick = { vm.setOwn(what, null) }) { Text("Clear") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // any type: some file apps do not label an SVG as image/svg+xml; what is picked is checked instead
                OutlinedButton(onClick = { pick.launch(arrayOf("image/svg+xml", "*/*")) }) {
                    Icon(painterResource(R.drawable.ic_tabler_upload), contentDescription = null, Modifier.size(18.dp))
                    Text("  Choose an SVG…")
                }
                TextButton(onClick = {
                    val text = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    take(text?.let { OwnFile("pasted-$what.svg", it, false) })
                }) { Text("Paste") }
            }
        }
    }
}
