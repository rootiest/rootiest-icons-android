package dev.rootiest.icons

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rootiest.icons.core.ExternalName
import dev.rootiest.icons.core.externalName
import dev.rootiest.icons.data.Appearance
import dev.rootiest.icons.data.OwnFile
import dev.rootiest.icons.data.OwnStore
import dev.rootiest.icons.data.PreviewBackground
import dev.rootiest.icons.data.Repo
import dev.rootiest.icons.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// A screen. Detail opens a subject; mine: on your logo from the start.
sealed interface Route {
    data object Catalogue : Route
    data class Detail(val subject: Subject, val mine: Boolean = false) : Route
    data object Own : Route
    data object Settings : Route
    data object Licenses : Route
}

// The catalogue: loading, failed (why), or ready to build from.
sealed interface Catalogue {
    data object Loading : Catalogue
    data class Failed(val why: String) : Catalogue
    data class Ready(val studio: Studio) : Catalogue
}

// Icons from other sets matching the search: the names, or why there are none.
data class OtherSets(val query: String = "", val names: List<String> = emptyList(), val status: String = "", val bodies: Map<String, String> = emptyMap())

// A file of your own as it stands: the file, your logo built from it, or why it cannot be used.
data class OwnState(val file: OwnFile? = null, val logo: OwnLogo? = null, val problem: String? = null)

class IconsViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    private val settings = SettingsStore(app)
    private val ownStore = OwnStore(app)

    var catalogue by mutableStateOf<Catalogue>(Catalogue.Loading)
        private set
    var appearance by mutableStateOf(settings.appearance)
        private set
    var previewBackground by mutableStateOf(settings.previewBackground)
        private set
    var ownLogo by mutableStateOf(OwnState(ownStore.load("logo")))
        private set
    var ownEmblem by mutableStateOf(OwnState(ownStore.load("emblem")))
        private set

    // what the catalogue shows
    var query by mutableStateOf("")
        private set
    var variant by mutableStateOf("color")
    var category by mutableStateOf("all")
    var other by mutableStateOf(OtherSets())
        private set

    val stack = mutableStateListOf<Route>(Route.Catalogue)
    private var searching: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        catalogue = Catalogue.Loading
        viewModelScope.launch {
            catalogue = try {
                Catalogue.Ready(Studio(repo, repo.index()))
            } catch (e: Exception) {
                Catalogue.Failed(e.message ?: "The catalogue could not be loaded")
            }
            checkOwn()
        }
    }

    fun open(route: Route) {
        stack.add(route)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun changeAppearance(value: Appearance) {
        settings.appearance = value
        appearance = value
    }

    fun changePreviewBackground(value: PreviewBackground) {
        settings.previewBackground = value
        previewBackground = value
    }

    // Load, replace or clear (null) your logo ("logo") or emblem ("emblem").
    fun setOwn(what: String, file: OwnFile?) {
        ownStore.save(what, file)
        if (what == "logo") ownLogo = OwnState(file) else ownEmblem = OwnState(file)
        checkOwn()
    }

    // Build your logo and try your emblem, so a file that cannot be used says why where it is listed.
    private fun checkOwn() {
        val studio = (catalogue as? Catalogue.Ready)?.studio ?: return
        viewModelScope.launch {
            ownLogo.file?.let { f ->
                ownLogo = try {
                    OwnState(f, studio.logo(f))
                } catch (e: Exception) {
                    OwnState(f, problem = e.message ?: "It cannot be used")
                }
            }
            ownEmblem.file?.let { f ->
                ownEmblem = try {
                    studio.load(OwnEmblemSubject, f)
                    OwnState(f)
                } catch (e: Exception) {
                    OwnState(f, problem = e.message ?: "It cannot be used")
                }
            }
        }
    }

    // The search box: filters the catalogue at once, and looks through the other sets after a pause, as the homepage.
    fun search(text: String) {
        query = text
        searching?.cancel()
        val q = text.trim()
        if (q.length < 3) {
            other = OtherSets(q, status = if (q.isEmpty()) "" else "Type 3 or more letters to look through every other set as well.")
            return
        }
        searching = viewModelScope.launch {
            delay(350)
            other = OtherSets(q, status = "Looking through the other sets…")
            other = try {
                val sets = repo.sets().keys
                var words = q
                var prefix: String? = null
                if (":" in q) {
                    val given = q.substringBefore(':').trim()
                    words = q.substringAfter(':').trim()
                    val named = externalName("$given:x", sets) ?: throw IllegalArgumentException("Iconify has no icon set $given")
                    prefix = named.prefix
                    if (named.icon != "x") words = "$words ${named.icon.removePrefix("x-")}"
                }
                val names = if (words.isBlank()) emptyList() else repo.search(words, prefix)
                val bodies = HashMap<String, String>()
                // one request per set for the tiles' pictures
                for ((set, icons) in names.groupBy({ it.substringBefore(':') }, { it.substringAfter(':') })) {
                    val data = runCatching { repo.iconsData(set, icons) }.getOrNull() ?: continue
                    for (icon in icons) {
                        dev.rootiest.icons.core.iconData(data, icon)?.let { bodies["$set:$icon"] = dev.rootiest.icons.core.iconSvg(it) }
                    }
                }
                OtherSets(q, names, if (names.isEmpty()) "No other set has that." else "${names.size} from other sets" + if (names.size >= 96) " (the first 96: add a set, like tabler:$words)" else "", bodies)
            } catch (e: Exception) {
                OtherSets(q, status = e.message ?: "The other sets could not be searched")
            }
        }
    }

    fun external(name: String): ExternalName? = externalName(name, emptySet())
}
