package dev.rootiest.icons.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

// The builder against rootiest-icons' own output (scripts/make-fixtures.py): the same text, byte for byte. Long
// expected outputs are in the fixtures as SHA-256 hashes, long inputs once each under "texts".
class FixturesTest {
    private val fx = Json.parseToJsonElement(javaClass.getResource("/fixtures.json")!!.readText()).jsonObject
    private val colors: Colors = IndexJson.decodeFromJsonElement(fx.getValue("colors"))
    private val cfg: ComposeConfig = IndexJson.decodeFromJsonElement(fx.getValue("cfg"))
    private val texts = fx.getValue("texts").jsonObject

    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun JsonObject.str(k: String) = (get(k) as? JsonElement)?.takeIf { it !is JsonNull }?.jsonPrimitive?.content
    private fun JsonObject.text(k: String) = str(k)?.let { texts.getValue(it).jsonPrimitive.content }
    private fun JsonObject.nums(k: String) = getValue(k).jsonArray.map { it.jsonPrimitive.double }
    private fun cases(name: String) = fx.getValue(name).jsonArray.map { it.jsonObject }
    private fun spec(e: JsonElement?) = (e as? JsonObject)?.let { ColorSpec(it.str("kind")!!, it.str("value")!!, it.str("label")!!, it.str("light")) }
    private fun options(o: JsonObject) = ColorOptions(spec(o["color"]), spec(o["logo"]), spec(o["emblem"]), spec(o["tone2"]))

    private fun check(name: String, wants: List<Pair<String, String>>) {
        val bad = wants.withIndex().filter { it.value.first != it.value.second }
        assertTrue("$name: ${bad.size} of ${wants.size} differ, first at ${bad.firstOrNull()?.index}: " +
            "want ${bad.firstOrNull()?.value?.first?.take(300)} got ${bad.firstOrNull()?.value?.second?.take(300)}", bad.isEmpty())
    }

    @Test
    fun numbers() {
        val all = cases("numbers")
        check("%.3f", all.map { it.str("f3")!! to pyFixed(it.getValue("x").jsonPrimitive.double, 3) })
        check("%.4f", all.map { it.str("f4")!! to pyFixed(it.getValue("x").jsonPrimitive.double, 4) })
        check("%g", all.map { it.str("g")!! to fmtG(it.getValue("x").jsonPrimitive.double) })
        check("%.4g", all.map { it.str("g4")!! to fmtG(it.getValue("x").jsonPrimitive.double, 4) })
        check("js", all.map { it.str("js")!! to jsNum(it.getValue("x").jsonPrimitive.double) })
    }

    @Test
    fun shades() = check("shades", cases("shades").map { c ->
        c.getValue("want").toString() to Json.encodeToString(shades(colors, ColorSpec("hex", c.str("hex")!!))).let {
            Json.parseToJsonElement(it).toString()
        }
    })

    @Test
    fun colorSpecs() {
        check("color spec", cases("colorSpecs").map { c ->
            spec(c["want"]).toString() to colorSpec(colors, c.str("text")!!, c.getValue("hexOnly").jsonPrimitive.boolean).toString()
        })
        // an index.json from before the auto variant's shift has none: render-icon.py's AUTO_SHIFT
        val old = IndexJson.decodeFromString<HexShades>("{\"emblemLift\":0.1,\"emblemC\":0.1,\"tone2Dark\":[0.3,0.1],\"tone2Light\":[0.9,0.1]}")
        assertEquals(0.07, old.autoShift, 0.0)
        assertTrue("index.json's autoShift", colors.hex.autoShift == 0.07)
    }

    @Test
    fun recoloring() {
        check("recolor", cases("recolor").map { c ->
            val text = c.text("text")!!
            val o = options(c.getValue("o").jsonObject)
            val variant = c.str("variant")!!
            c.str("want")!! to sha(if (c.getValue("emblem").jsonPrimitive.boolean) emblemRecolored(colors, text, o, variant) else iconRecolored(colors, text, o, variant))
        })
        assertTrue("auto recolorings", cases("recolor").count { it.str("variant") == "auto" } > 100)
        check("mono", cases("mono").map { it.str("want")!! to sha(recolor(it.text("text")!!, it.str("main"), it.str("tone2"))) })
    }

    @Test
    fun autoVariant() {
        fun orNull(make: () -> String) = try { make() } catch (e: UnusableSvg) { null }
        val all = cases("autos")
        check("auto emblems", all.filter { it.str("text") != null }.map { c ->
            c.str("want")!! to sha(emblemVariant(c.text("text")!!, "auto"))
        })
        check("auto pairs", all.filter { it.str("dark") != null }.map { c ->
            (c.str("want") ?: "refused") to (orNull { autoSvg(c.str("dark")!!, c.str("light")!!) } ?: "refused")
        })
    }

    private fun emblem(e: JsonObject, thick: Double?) = Emblem(
        body = e.text("body")!!.let { if (thick != null) thicken(it, thick) else it },
        bbox = e.nums("bbox"), about = e.str("about")!!, colors = IndexJson.decodeFromJsonElement(e.getValue("colors")),
        mono = e.text("mono"), nudge = e.nums("nudge"),
    )

    @Test
    fun builtIcons() = check("built", cases("built").map { c ->
        val thick = c.getValue("thick").jsonPrimitive.doubleOrNull
        val e = (c["e"] as? JsonObject)?.let { emblem(it, thick) }
        val logo = (c["logo"] as? JsonObject)?.let { Logo(it.text("body")!!, it.str("transform")!!, it.str("about")!!, it.text("mono")) }
        val p = c.getValue("place").jsonObject
        val place = Place(p.str("preset")!!, p.getValue("scale").jsonPrimitive.double, p.getValue("dx").jsonPrimitive.double, p.getValue("dy").jsonPrimitive.double)
        c.str("want")!! to sha(builtSvg(cfg, e, logo, c.str("variant")!!, c.getValue("padded").jsonPrimitive.boolean, place))
    })

    @Test
    fun emblemsAlone() = check("alone", cases("alone").map { c ->
        val thick = c.getValue("thick").jsonPrimitive.doubleOrNull
        val body = c.text("body")!!.let { if (thick != null) thicken(it, thick) else it }
        c.str("want")!! to sha(emblemSvg(cfg, body, c.nums("bbox"), c.getValue("keep").jsonPrimitive.boolean, c.str("about")!!))
    })

    @Test
    fun filesOfYourOwn() {
        val own = fx.getValue("own").jsonObject
        fun list(k: String) = own.getValue(k).jsonArray.map { it.jsonObject }
        check("fit", list("fits").map { it.str("want")!! to fitLogo(cfg, it.nums("bbox")) })
        check("text", list("texts").map { it.str("want")!! to ownText(it.str("raw")!!, it.getValue("keep").jsonPrimitive.boolean) })
        check("slug", list("slugs").map { it.str("want")!! to slug(it.str("name")!!) })
        check("tag", list("tags").map {
            it.str("want")!! to placementTag(it.str("preset")!!, it.getValue("scale").jsonPrimitive.doubleOrNull,
                it.getValue("x").jsonPrimitive.double, it.getValue("y").jsonPrimitive.double)
        })
        val cleaned = list("cleans").map { it to cleanSvg(it.text("raw")!!, it.str("name")!!) }
        check("clean viewBox", cleaned.map { (c, got) -> c.str("viewBox")!! to got.viewBox })
        check("clean body", cleaned.map { (c, got) -> "${c.str("name")} ${c.str("body")}" to "${c.str("name")} ${sha(got.body)}" })
    }

    @Test
    fun iconifyData() = check("iconify", cases("icondata").map { c ->
        (c.str("want") ?: "none") to (iconData(c.getValue("data").jsonObject, c.str("icon")!!)?.let(::iconSvg) ?: "none")
    })

    @Test
    fun fileNames() = check("plans", cases("plans").map { c ->
        val stem = stemOf(c.str("stem")!!, c.getValue("emblem").jsonPrimitive.boolean, c.str("variant")!!,
            c.getValue("padded").jsonPrimitive.boolean, options(c.getValue("o").jsonObject))
        c.getValue("want").jsonArray.joinToString(",") { it.jsonPrimitive.content } to
            plannedFiles(stem, c.getValue("layouts").jsonArray.map { it.jsonPrimitive.content },
                c.getValue("sizes").jsonArray.map { it.jsonPrimitive.int }, c.getValue("formats").jsonArray.map { it.jsonPrimitive.content },
                animationTag(c.str("animate")!!, c.getValue("loop").jsonPrimitive.boolean, c.getValue("duration").jsonPrimitive.doubleOrNull,
                    c.str("morphTo"), spec(c["morphColor"]), spec(c["morphTone2"]))).joinToString(",")
    })

    @Test
    fun badgePlayTimes() = check("play times", cases("playTimes").map { c ->
        val text = c.text("text")!!
        "${c.str("name")} ${c.getValue("seconds").jsonPrimitive.double} ${c.getValue("frames").jsonPrimitive.int} ${c.getValue("last").jsonPrimitive.double} ${c.getValue("once").jsonPrimitive.boolean}" to
            playFrames(text).let { (t, once) -> "${c.str("name")} ${playTime(text).seconds} ${t.size} ${t.last()} $once" }
    })

    @Test
    fun morphTargetColors() = check("morph colors", cases("morphColors").map { c ->
        options(c.getValue("want").jsonObject).toString() to
            morphColors(options(c.getValue("o").jsonObject), c.getValue("emblem").jsonPrimitive.boolean, spec(c["color"]), spec(c["tone2"])).toString()
    })

    @Test
    fun animations() {
        check("animate", cases("anims").map { c ->
            c.str("want")!! to sha(animateSvg(c.text("text")!!, c.str("animate")!!, c.getValue("duration").jsonPrimitive.doubleOrNull,
                c.getValue("loop").jsonPrimitive.boolean, c.text("target")))
        })
        check("animation tag", cases("animTags").map { c ->
            c.str("want")!! to animationTag(c.str("animate")!!, c.getValue("loop").jsonPrimitive.boolean, c.getValue("duration").jsonPrimitive.doubleOrNull,
                c.str("morphTo"))
        })
        check("frame times", cases("animTags").map { c ->
            val f = c.getValue("frames").jsonArray
            "${f[0].jsonPrimitive.int} ${f[1].jsonPrimitive.double} ${f[2].jsonPrimitive.boolean}" to
                frameTimes(c.str("animate")!!, c.getValue("loop").jsonPrimitive.boolean, c.getValue("duration").jsonPrimitive.doubleOrNull)
                    .let { (t, once) -> "${t.size} ${t.last()} $once" }
        })
    }

    @Test
    fun layouts() {
        val l = fx.getValue("layouts").jsonObject
        val text = l.text("text")!!
        val small = l.getValue("small").jsonArray.map { it.jsonPrimitive.int }
        assertEquals(small, layoutsOf(text))
        check("single", l.getValue("single").jsonObject.map { (k, v) -> v.jsonPrimitive.content to sha(singleLayoutSvg(text, k)) })
        check("resolve", l.getValue("resolve").jsonArray.map { it.jsonObject }.map {
            it.str("want")!! to resolveLayout(it.str("layout")!!, it.getValue("size").jsonPrimitive.int, small)
        })
    }

    @Test
    fun unusableFiles() {
        fun fails(raw: String) = try { ownSvg(raw, "x.svg", false); false } catch (e: UnusableSvg) { true }
        assertTrue(fails("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><rect fill=\"url(#g)\"/></svg>"))
        assertTrue(fails("not an svg"))
        assertTrue(fails("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><text>hi</text></svg>"))
        assertTrue(fails("<svg xmlns=\"http://www.w3.org/2000/svg\"><rect width=\"1\" height=\"1\"/></svg>"))
        assertTrue(fails("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><rect></svg>"))
    }

    @Test
    fun containers() {
        val files = listOf(NamedBytes("dir/a.png", byteArrayOf(1, 2, 3)), NamedBytes("b.ico", ByteArray(300) { it.toByte() }))
        val zip = java.util.zip.ZipInputStream(zipBytes(files).inputStream())
        for (f in files) {
            assertEquals(f.name, zip.nextEntry!!.name)
            assertTrue(f.bytes.contentEquals(zip.readBytes()))
        }
        val ico = icoBytes(listOf(16 to ByteArray(10), 256 to ByteArray(20)))
        assertEquals(6 + 32 + 30, ico.size)
        assertEquals(listOf(0, 0, 1, 0, 2, 0, 16, 16), ico.take(8).map { it.toInt() })
        assertEquals(0, ico[22].toInt()) // 256 px is written as 0
    }
}
