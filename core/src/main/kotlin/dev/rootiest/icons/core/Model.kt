package dev.rootiest.icons.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// index.json, the catalogue icons.rootiest.fyi publishes (rootiest-icons scripts/make-site.py): every icon and emblem,
// the colors, and what it takes to build an icon as the generator does.

val IndexJson = Json { ignoreUnknownKeys = true }

@Serializable
data class Index(
    val site: String,
    val colors: Colors,
    val compose: ComposeConfig,
    val icons: List<IconEntry>,
    val emblems: List<EmblemEntry>,
    // the animated status badges (color/animated/<name>.svg); absent from an older catalogue
    val animated: List<AnimatedEntry> = emptyList(),
)

@Serializable
data class Parts(val logo: Boolean = false, val emblem: Boolean = false, val tone2: Boolean = false)

// How the generator builds a published icon (its manifest row), to build its emblem on another logo.
@Serializable
data class Build(val emblem: String, val nudge: List<Double>, val colors: VariantColors, val colorOnly: Boolean = false)

@Serializable
data class IconEntry(
    val name: String,
    val kind: String, // logo, service, variant, status or badge
    val title: String,
    val description: String = "",
    val variants: List<String> = listOf("color"),
    val padded: Boolean = false,
    // an auto/ version: the dark and light versions in one SVG that switches with the viewer's mode
    val auto: Boolean = false,
    // false for an SVG-only icon (an -auto one: a raster cannot switch)
    val rasters: Boolean = true,
    val layouts: List<Int> = emptyList(),
    val parts: Parts = Parts(),
    val emblem: String? = null,
    val category: String? = null,
    val state: String? = null,
    val build: Build? = null,
)

@Serializable
data class EmblemEntry(
    val name: String,
    val title: String,
    val description: String = "",
    val twoTone: Boolean = false,
    val keepFill: Boolean = false,
)

// An animated status badge: a change from one state to another (plays once, ends on the new state), a heartbeat's
// loop (from and to the same state), or a shape's cycle through every state (from and to null, loops).
@Serializable
data class AnimatedEntry(
    val name: String,
    val from: String? = null,
    val to: String? = null,
    val shape: String = "",
    val padded: Boolean = false,
)

// The colors of a built icon in one variant: logo, emblem, second tone (or none) and, for the color variant, the
// light-background colors [emblem, tone 2, logo or none]. In index.json: [logo, emblem, tone2|null, [..]|null].
@Serializable(with = ColorSetSerializer::class)
data class ColorSet(val logo: String, val emblem: String, val emblem2: String?, val onLight: List<String?>?)

object ColorSetSerializer : KSerializer<ColorSet> {
    override val descriptor: SerialDescriptor = JsonArray.serializer().descriptor
    override fun deserialize(decoder: Decoder): ColorSet {
        val a = (decoder as JsonDecoder).decodeJsonElement().jsonArray
        fun text(e: JsonElement) = if (e is JsonNull) null else e.jsonPrimitive.content
        return ColorSet(text(a[0])!!, text(a[1])!!, text(a[2]), (a[3] as? JsonArray)?.map(::text))
    }

    override fun serialize(encoder: Encoder, value: ColorSet) = encoder.encodeSerializableValue(
        JsonArray.serializer(),
        buildJsonArray {
            add(JsonPrimitive(value.logo)); add(JsonPrimitive(value.emblem)); add(JsonPrimitive(value.emblem2))
            add(value.onLight?.let { l -> JsonArray(l.map { JsonPrimitive(it) }) } ?: JsonNull)
        },
    )
}

// {variant: [without a second tone, with one]}
typealias VariantColors = Map<String, List<ColorSet>>

// Where an emblem goes: the box's center and size, and the small layouts [px, scale, center x, center y, gap].
@Serializable(with = PresetSerializer::class)
data class Preset(val cx: Double, val cy: Double, val w: Double, val h: Double, val small: List<List<Double>>)

object PresetSerializer : KSerializer<Preset> {
    override val descriptor: SerialDescriptor = JsonArray.serializer().descriptor
    override fun deserialize(decoder: Decoder): Preset {
        val a = (decoder as JsonDecoder).decodeJsonElement().jsonArray
        val n = a.take(4).map { it.jsonPrimitive.double }
        return Preset(n[0], n[1], n[2], n[3], a[4].jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.double } })
    }

    override fun serialize(encoder: Encoder, value: Preset) = encoder.encodeSerializableValue(
        JsonArray.serializer(),
        buildJsonArray {
            listOf(value.cx, value.cy, value.w, value.h).forEach { add(JsonPrimitive(it)) }
            add(JsonArray(value.small.map { row -> JsonArray(row.map { JsonPrimitive(it) }) }))
        },
    )
}

// The generator's logo, canvas and emblem placement, and render-icon.py's defaults for icons from other sets.
@Serializable
data class ComposeConfig(
    val logoD: String,
    val viewBox: String,
    val viewBoxPadded: String,
    val knockout: Double,
    val edge: Double,
    val smallLayouts: List<List<Double>>,
    val sizeCss: String,
    val presets: Map<String, Preset>,
    val logoExtent: List<Double>,
    val rootiestColors: VariantColors,
    val logoRed: String,
    val strokeScale: Double = 1.3,
    val brandL: List<Double> = listOf(0.35, 0.92),
    val iconifyHosts: List<String> = listOf("https://api.iconify.design"),
    val simpleIcons: String = "",
)
