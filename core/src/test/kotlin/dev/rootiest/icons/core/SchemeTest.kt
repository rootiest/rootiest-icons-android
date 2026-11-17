package dev.rootiest.icons.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// schemeSvg(): an SVG that switches with light or dark mode, fixed to one of them.
class SchemeTest {
    private fun svg(css: String) = "<svg xmlns=\"http://www.w3.org/2000/svg\">\n  <defs>\n" +
        "    <style id=\"current-color-scheme\" type=\"text/css\">\n      $css\n    </style>\n" +
        "    <style id=\"sizes\" type=\"text/css\">.Size16{display:none}@media (max-width:16px){.SizeFull{display:none}}</style>\n" +
        "  </defs>\n</svg>\n"

    private val auto = svg(".ColorScheme-Accent{color:#ffffff}.ColorScheme-Emblem{color:#9a9996}@media (prefers-color-scheme: light){.ColorScheme-Emblem{color:#77767b}.ColorScheme-Accent{color:#000000}}")
    private val idAuto = svg(".ColorScheme-Accent{color:#ff004c}.ColorScheme-Emblem{color:#dadafe}@media (prefers-color-scheme: light){.ColorScheme-Emblem{color:#121218}}")

    @Test
    fun dark() {
        assertEquals(svg(".ColorScheme-Accent{color:#ffffff}.ColorScheme-Emblem{color:#9a9996}"), schemeSvg(auto, false))
        assertEquals(svg(".ColorScheme-Accent{color:#ff004c}.ColorScheme-Emblem{color:#dadafe}"), schemeSvg(idAuto, false))
    }

    @Test
    fun light() {
        assertEquals(svg(".ColorScheme-Accent{color:#000000}.ColorScheme-Emblem{color:#77767b}"), schemeSvg(auto, true))
        val id = schemeSvg(idAuto, true)
        assertEquals(svg(".ColorScheme-Accent{color:#ff004c}.ColorScheme-Emblem{color:#121218}"), id)
        assertFalse("prefers-color-scheme" in id)
    }

    @Test
    fun ruleOnlyInLightIsAdded() {
        val two = svg(".ColorScheme-Accent{color:#ff004c}@media (prefers-color-scheme: light){.ColorScheme-Emblem2{color:#000000}}")
        assertEquals(svg(".ColorScheme-Accent{color:#ff004c}.ColorScheme-Emblem2{color:#000000}"), schemeSvg(two, true))
    }

    @Test
    fun fixedTextIsUnchanged() {
        val fixed = svg(".ColorScheme-Accent{color:#ff004c}.ColorScheme-Emblem{color:#121218}")
        assertEquals(fixed, schemeSvg(fixed, true))
        assertEquals(fixed, schemeSvg(fixed, false))
        assertFalse(switches(fixed))
        assertTrue(switches(auto))
    }
}
