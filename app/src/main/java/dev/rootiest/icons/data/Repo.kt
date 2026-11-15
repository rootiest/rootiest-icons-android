package dev.rootiest.icons.data

import android.content.Context
import dev.rootiest.icons.core.Index
import dev.rootiest.icons.core.IndexJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

const val SITE = "https://icons.rootiest.fyi"

// An icon set at Iconify: its name, who made it, its license, and whether its icons keep their own colors.
@Serializable
data class SetInfo(val name: String = "", val author: Author? = null, val license: License? = null, val palette: Boolean = false) {
    @Serializable
    data class Author(val name: String = "")

    @Serializable
    data class License(val title: String = "", val spdx: String = "", val url: String = "")

    // "Tabler Icons by Paweł Kuna, MIT", as render-icon.py credits a set
    fun credit(prefix: String) = name.ifEmpty { prefix } + (author?.name?.takeIf { it.isNotEmpty() }?.let { " by $it" } ?: "") +
        (license?.let { ", ${it.title.ifEmpty { it.spdx }}" } ?: "")
}

// What the app reads from the network: the catalogue and SVGs icons.rootiest.fyi publishes, and the Iconify API for
// icons from other sets. Answers are kept in OkHttp's disk cache, and the last catalogue in the app's files, so what
// was seen once opens again offline.
class Repo(context: Context) {
    private val http = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 64L * 1024 * 1024))
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val saved = File(context.filesDir, "index.json")
    private val svgs = ConcurrentHashMap<String, String>()
    private var sets: Map<String, SetInfo>? = null
    private var brands: Map<String, String>? = null
    private var hosts: List<String> = listOf("https://api.iconify.design")
    private var simpleIcons = ""

    private fun get(url: String): String? {
        http.newCall(Request.Builder().url(url).header("User-Agent", "rootiest-icons-android").build()).execute().use { r ->
            if (r.code == 404) return null
            if (!r.isSuccessful) throw IOException("$url: HTTP ${r.code}")
            return r.body.string()
        }
    }

    // The catalogue: from the site, or the copy saved from the last time when the site cannot be reached.
    suspend fun index(): Index = withContext(Dispatchers.IO) {
        val text = try {
            (get("$SITE/index.json") ?: throw IOException("the catalogue is missing")).also(saved::writeText)
        } catch (e: IOException) {
            if (!saved.isFile) throw e
            saved.readText()
        }
        IndexJson.decodeFromString<Index>(text).also {
            hosts = it.compose.iconifyHosts
            simpleIcons = it.compose.simpleIcons
        }
    }

    // A published file's text, by its path on the site: color/rootiest-home.svg, emblems/home.svg.
    suspend fun svg(path: String): String = svgs[path] ?: withContext(Dispatchers.IO) {
        (get("$SITE/$path") ?: throw IOException("$path is not published")).also { svgs[path] = it }
    }

    // The Iconify API's answer to a path, trying its mirrors in turn; null when it has no such thing.
    private fun iconify(path: String): String? {
        val errors = ArrayList<String>()
        for (host in hosts) {
            try {
                return get(host + path)
            } catch (e: IOException) {
                errors.add(e.message ?: host)
            }
        }
        throw IOException("Cannot reach the Iconify API (${errors.joinToString("; ")})")
    }

    // Every set Iconify serves, by prefix; empty when it cannot be reached (names are then taken as given).
    suspend fun sets(): Map<String, SetInfo> = sets ?: withContext(Dispatchers.IO) {
        try {
            IndexJson.decodeFromString<Map<String, SetInfo>>(iconify("/collections") ?: "{}").also { sets = it }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // A set's answer for some of its icons (/<set>.json?icons=a,b), to read each from with iconData().
    suspend fun iconsData(prefix: String, icons: List<String>): JsonObject? = withContext(Dispatchers.IO) {
        iconify("/$prefix.json?icons=${URLEncoder.encode(icons.joinToString(","), "UTF-8")}")
            ?.let { IndexJson.parseToJsonElement(it) as? JsonObject }
    }

    // Icon names matching the words, in every set or in one (prefix).
    suspend fun search(words: String, prefix: String?): List<String> = withContext(Dispatchers.IO) {
        val q = "/search?query=${URLEncoder.encode(words, "UTF-8")}&limit=96" + (prefix?.let { "&prefix=$it" } ?: "")
        val found = IndexJson.parseToJsonElement(iconify(q) ?: "{}").jsonObject["icons"] as? JsonElement
        found?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
    }

    // Simple Icons' brand colors, {icon name: "#rrggbb"}; empty when they cannot be fetched.
    suspend fun brands(): Map<String, String> = brands ?: withContext(Dispatchers.IO) {
        try {
            IndexJson.parseToJsonElement(get(simpleIcons) ?: "[]").jsonArray.mapNotNull { b ->
                val o = b.jsonObject
                val slug = o["slug"]?.jsonPrimitive?.content
                val hex = o["hex"]?.jsonPrimitive?.content
                // Iconify spells Simple Icons' slugs with - for _
                if (slug != null && hex != null && Regex("[0-9a-fA-F]{6}").matches(hex)) slug.replace("_", "-") to "#${hex.lowercase()}" else null
            }.toMap().also { brands = it }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
