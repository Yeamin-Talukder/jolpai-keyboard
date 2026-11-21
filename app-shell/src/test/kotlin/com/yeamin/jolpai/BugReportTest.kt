package com.yeamin.jolpai

import com.yeamin.jolpai.core.settings.KeyboardSettings
import java.net.URLDecoder
import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {

    private val info = DeviceInfo("0.13.3", 12, "17", 37, "Google", "Pixel 7 Pro", "arm64-v8a")
    private val extras = DeviceExtras("cheetah", "CP1A.261105", "11.6 GB", "1080×2340 px, 420 dpi, font scale 1.0", "bn-BD")
    private val details = BugReport.details(info, extras, KeyboardSettings(), SetupStatus(enabled = true, selected = true))
    private val encode: (String) -> String = { URLEncoder.encode(it, "UTF-8") }

    private fun bodyOf(url: String) = URLDecoder.decode(url.substringAfter("&body="), "UTF-8")
    private fun titleOf(url: String) = URLDecoder.decode(url.substringAfter("&title=").substringBefore("&body="), "UTF-8")

    @Test fun `issue carries the words and the phone details`() {
        val url = BugReport.issueUrl(BugReportText("ৎ doesn't type", "Holding ত in WhatsApp types nothing", "1. Open WhatsApp", "ৎ"), details, encode)
        assertTrue(url.startsWith("https://github.com/Yeamin-Talukder/jolpai-keyboard/issues/new?labels=bug&title="))
        assertEquals("Bug: ৎ doesn't type", titleOf(url))
        val body = bodyOf(url)
        assertTrue(body.contains("Holding ত in WhatsApp types nothing"))
        assertTrue(body.contains("**Steps to reproduce**\n1. Open WhatsApp"))
        assertTrue(body.contains("| Phone | Google Pixel 7 Pro (cheetah) |"))
        assertTrue(body.contains("| Android | 17 (API 37) |"))
        assertTrue(body.contains("| Memory | 11.6 GB |"))
        assertTrue(body.contains("| Jolpai turned on / in use | yes / yes |"))
        assertTrue(body.contains("| Style | Modern |"))
    }

    @Test fun `details can be left out`() {
        val body = bodyOf(BugReport.issueUrl(BugReportText("x", "y"), null, encode))
        assertFalse(body.contains("Pixel"))
        assertFalse(body.contains("**Phone and app**"))
    }

    @Test fun `title falls back to the first line of what happened and is kept short`() {
        assertEquals("Bug: Keyboard closes", BugReport.title(BugReportText("", "Keyboard closes\nwhen I rotate")))
        val long = BugReport.title(BugReportText("a".repeat(200), ""))
        assertTrue(long.length <= 86 && long.endsWith("…"))
    }

    @Test fun `a very long report is shortened so GitHub still opens it, details kept`() {
        val url = BugReport.issueUrl(BugReportText("Long", "ক্ষ ".repeat(3000)), details, encode)
        assertTrue(url.length <= BugReport.MAX_URL_LENGTH)
        val body = bodyOf(url)
        assertTrue(body.contains("shortened to fit"))
        assertTrue(body.contains("| Phone | Google Pixel 7 Pro (cheetah) |"))
    }

    @Test fun `table cells can't break the table`() {
        val body = BugReport.body(BugReportText("s", "w"), listOf("Theme" to "a|b\nc"))
        assertTrue(body.contains("| Theme | a/b c |"))
    }
}
