package dev.rootiest.icons.core

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The animated status badges, as rootiest-icons publishes them (copies in resources/animated/): how long each plays and
// whether it loops, read from its own text, and its frames.
class AnimatedTest {
    private fun file(name: String) = javaClass.getResource("/animated/$name.svg")!!.readText()

    private fun assertPlays(name: String, seconds: Double, loops: Boolean) {
        val p = playTime(file(name))
        assertEquals(name, seconds, p.seconds, 1e-9)
        assertEquals(name, loops, p.loops)
    }

    @Test
    fun playTimes() {
        assertPlays("icon-ok-to-bad-circle", 0.8, false)
        assertPlays("icon-cycle-square", 8.25, true)
        assertPlays("icon-ok-heartbeat", 1.6, true)
        // a beat in the old color and a beat in the new: the sweep's 1.6 s twice
        assertPlays("icon-ok-to-bad-heartbeat", 3.2, false)
        // the states' 9.6 s, which the 1.6 s beats fit
        assertPlays("icon-cycle-heartbeat", 9.6, true)
        assertEquals(PlayTime(0.0, false), playTime("<svg xmlns=\"http://www.w3.org/2000/svg\"/>"))
    }

    @Test
    fun frames() {
        val (change, once) = playFrames(file("icon-ok-to-bad-circle"))
        assertTrue(once)
        assertEquals(21, change.size)  // 20 at 25 a second, and the end
        assertEquals(0.8, change.last(), 1e-9)
        val (cycle, cycleOnce) = playFrames(file("icon-cycle-heartbeat"))
        assertFalse(cycleOnce)
        assertEquals(240, cycle.size)
        assertEquals(81, playFrames(file("icon-ok-to-bad-heartbeat")).first.size)
    }

    // A badge's change ends on the new state: its color and its mark (the SMIL path) are the new state's.
    @Test
    fun changeEndsOnNewState() {
        val text = file("icon-ok-to-bad-circle")
        val start = frameSvg(text, 0.0)
        val end = frameSvg(text, 0.8)
        assertTrue("color:#7bd77f" in start)
        assertTrue("color:#fe9196" in end)
        assertTrue("color:#9f2c3b" in frameSvg(text, 0.8, light = true))
        assertNotEquals(Regex("<mask id=\"glyph\"[\\s\\S]*?</mask>").find(start)!!.value, Regex("<mask id=\"glyph\"[\\s\\S]*?</mask>").find(end)!!.value)
        assertFalse("<animate" in end)
    }

    // The heartbeat's change sweeps twice (an iteration count of 2): the band is back at its start at 1.6 s, then at
    // its end once both beats are done, where it stays.
    @Test
    fun countedIterations() {
        val text = file("icon-ok-to-bad-heartbeat")
        fun band(t: Double) = Regex("class=\"sweep-band\"[^>]*transform=\"([^\"]*)\"").find(frameSvg(text, t))!!.groupValues[1]
        assertEquals("translate(0 0)", band(0.0))
        assertEquals("translate(0 0)", band(1.6))
        assertEquals("translate(1.7 0)", band(3.2))
        assertEquals("translate(1.7 0)", band(5.0))
    }

    @Test
    fun indexEntries() {
        val list = IndexJson.decodeFromString(
            ListSerializer(AnimatedEntry.serializer()),
            """[{"name":"icon-ok-to-bad-circle","from":"ok","to":"bad","shape":"circle","padded":true},
               {"name":"icon-cycle-circle","from":null,"to":null,"shape":"circle"},
               {"name":"icon-ok-heartbeat","from":"ok","to":"ok","shape":"heartbeat","later":1}]""",
        )
        assertEquals(AnimatedEntry("icon-ok-to-bad-circle", "ok", "bad", "circle", true), list[0])
        assertNull(list[1].from)
        assertFalse(list[1].padded)
        assertEquals("heartbeat", list[2].shape)
    }
}
