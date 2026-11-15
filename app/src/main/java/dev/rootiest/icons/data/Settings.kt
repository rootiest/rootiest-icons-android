package dev.rootiest.icons.data

import android.content.Context
import androidx.core.content.edit
import java.io.File

// Light or dark: follow the phone (the default), or always one of them.
enum class Appearance { System, Light, Dark }

// What previews are drawn on: the variant's own (dark for the dark icons, light for the light ones), always dark,
// always light, or a checkerboard that shows what is transparent.
enum class PreviewBackground { Auto, Dark, Light, Checker }

// The user's settings, kept in the app's private storage.
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var appearance: Appearance
        // an unknown name (from a newer version, say) falls back to the default
        get() = Appearance.entries.find { it.name == prefs.getString("appearance", null) } ?: Appearance.System
        set(value) = prefs.edit { putString("appearance", value.name) }

    var previewBackground: PreviewBackground
        get() = PreviewBackground.entries.find { it.name == prefs.getString("preview-background", null) } ?: PreviewBackground.Auto
        set(value) = prefs.edit { putString("preview-background", value.name) }
}

// An SVG file of your own: its name, its text, and whether it keeps its own colors.
data class OwnFile(val name: String, val raw: String, val keep: Boolean)

// Your own logo and emblem, kept in the app's private storage (they never leave the phone).
class OwnStore(context: Context) {
    private val dir = File(context.filesDir, "own").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("own", Context.MODE_PRIVATE)

    fun load(what: String): OwnFile? {
        val file = File(dir, "$what.svg")
        val name = prefs.getString("$what-name", null) ?: return null
        return if (file.isFile) OwnFile(name, file.readText(), prefs.getBoolean("$what-keep", false)) else null
    }

    fun save(what: String, file: OwnFile?) {
        if (file == null) {
            File(dir, "$what.svg").delete()
            prefs.edit { remove("$what-name"); remove("$what-keep") }
        } else {
            File(dir, "$what.svg").writeText(file.raw)
            prefs.edit { putString("$what-name", file.name); putBoolean("$what-keep", file.keep) }
        }
    }
}
