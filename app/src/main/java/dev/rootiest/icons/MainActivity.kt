package dev.rootiest.icons

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import dev.rootiest.icons.data.Appearance
import dev.rootiest.icons.data.OwnFile
import dev.rootiest.icons.ui.IconsApp
import dev.rootiest.icons.ui.theme.RootiestTheme

class MainActivity : ComponentActivity() {

    private val vm: IconsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) takeSharedSvg(intent)
        setContent {
            val dark = when (vm.appearance) {
                Appearance.System -> isSystemInDarkTheme()
                Appearance.Light -> false
                Appearance.Dark -> true
            }
            LaunchedEffect(vm.appearance) { applyNightMode(vm.appearance) }
            // the bars' icons follow the app's choice, not the phone's
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            RootiestTheme(dark) {
                IconsApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        takeSharedSvg(intent)
    }

    // An SVG shared to the app, or opened with it, becomes your emblem and opens.
    private fun takeSharedSvg(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        val file = readSvg(uri) ?: return
        vm.setOwn("emblem", file)
        vm.open(Route.Detail(OwnEmblemSubject, mine = false))
    }

    // The app's own light or dark choice for what the system draws for it (the splash screen, the recents card).
    private fun applyNightMode(appearance: Appearance) {
        if (Build.VERSION.SDK_INT < 31) return
        getSystemService(UiModeManager::class.java).setApplicationNightMode(
            when (appearance) {
                Appearance.System -> UiModeManager.MODE_NIGHT_AUTO
                Appearance.Light -> UiModeManager.MODE_NIGHT_NO
                Appearance.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    private companion object {
        // the navigation bar's scrims behind three-button navigation, as enableEdgeToEdge's own defaults
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

// An SVG file picked or shared, as a file of your own: its name and text; null when it cannot be read or is too
// large to be an icon (over 2 MB).
fun android.content.Context.readSvg(uri: Uri): OwnFile? = try {
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "icon.svg"
    val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    if (bytes.size > 2 * 1024 * 1024) null else OwnFile(name.replace(Regex("[\\\\/]"), "-"), bytes.toString(Charsets.UTF_8), false)
} catch (e: Exception) {
    null
}
