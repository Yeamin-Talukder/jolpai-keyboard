package com.yeamin.jolpai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.net.URLEncoder

class AppUpdatesTest {
    @Test fun `versions compare by their numbers`() {
        assertEquals(0, AppUpdates.compareVersions("0.7.0", "v0.7.0"))
        assertEquals(0, AppUpdates.compareVersions("1.0", "1.0.0"))
        assertEquals(0, AppUpdates.compareVersions("0.8.0-beta", "0.8.0"))
        assertTrue(AppUpdates.compareVersions("0.10.0", "0.9.9") > 0) // not string order
        assertTrue(AppUpdates.compareVersions("0.7.0", "0.7.1") < 0)
        assertTrue(AppUpdates.isNewer("v0.8.0", "0.7.0"))
        assertFalse(AppUpdates.isNewer("v0.7.0", "0.7.0"))
        assertFalse(AppUpdates.isNewer("0.6.9", "0.7.0"))
    }

    private val release = """
        {"tag_name":"v0.8.0","html_url":"https://github.com/Yeamin-Talukder/jolpai-keyboard/releases/tag/v0.8.0",
         "assets":[
           {"name":"jolpai-keyboard-0.8.0-x86_64.apk","browser_download_url":"https://x/x86_64.apk"},
           {"name":"jolpai-keyboard-0.8.0-x86.apk","browser_download_url":"https://x/x86.apk"},
           {"name":"jolpai-keyboard-0.8.0-arm64-v8a.apk","browser_download_url":"https://x/arm64.apk","size":23716000},
           {"name":"jolpai-keyboard-0.8.0-armeabi-v7a.apk","browser_download_url":"https://x/v7a.apk"},
           {"name":"SHA256SUMS.txt","browser_download_url":"https://x/sums"}
         ]}
    """.trimIndent()

    @Test fun `latest release picks the APK for the phone's processor`() {
        val arm = AppUpdates.parseLatest(release, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!
        assertEquals("0.8.0", arm.version)
        assertEquals("https://x/arm64.apk", arm.apkUrl)
        assertTrue(arm.pageUrl.endsWith("/v0.8.0"))
        // "x86" must not match the x86_64 file.
        assertEquals("https://x/x86.apk", AppUpdates.parseLatest(release, listOf("x86"))!!.apkUrl)
        assertEquals("https://x/v7a.apk", AppUpdates.parseLatest(release, listOf("armeabi-v7a"))!!.apkUrl)
        assertNull(AppUpdates.parseLatest(release, listOf("mips"))!!.apkUrl)
    }

    @Test fun `in-app update gets the APK's name, size and the checksum file`() {
        val r = AppUpdates.parseLatest(release, listOf("arm64-v8a"))!!
        assertEquals("jolpai-keyboard-0.8.0-arm64-v8a.apk", r.apkName)
        assertEquals(23716000L, r.apkSize)
        assertEquals("https://x/sums", r.sumsUrl)
        // No size in the reply: 0 (the download's own length is used).
        assertEquals(0L, AppUpdates.parseLatest(release, listOf("x86"))!!.apkSize)
    }

    @Test fun `checksums are read from SHA256SUMS in both sha256sum formats`() {
        val a = "d7c2841e4090612e13ebc8914b236b1bb49a9b1e64439ab6d461e87d7c5d5349"
        val b = "22558dfa298a42abbc7151cf9dc82b25ac0d5f1b101726f2de6d292c8496d13f"
        // 0.7.0 (binary mode, "*name") and 0.8.0 (text mode, two spaces) as published.
        val sums = "$a *jolpai-keyboard-0.7.0-arm64-v8a.apk\n${b.uppercase()}  jolpai-keyboard-0.8.0-arm64-v8a.apk\n"
        assertEquals(a, AppUpdates.checksumFor(sums, "jolpai-keyboard-0.7.0-arm64-v8a.apk"))
        assertEquals(b, AppUpdates.checksumFor(sums, "jolpai-keyboard-0.8.0-arm64-v8a.apk"))
        assertNull(AppUpdates.checksumFor(sums, "jolpai-keyboard-0.8.0-x86.apk"))
        assertNull(AppUpdates.checksumFor("not a sums file", "jolpai-keyboard-0.8.0-arm64-v8a.apk"))
    }

    @Test fun `bad or empty replies give no release`() {
        assertNull(AppUpdates.parseLatest("not json", listOf("arm64-v8a")))
        assertNull(AppUpdates.parseLatest("""{"message":"Not Found"}""", listOf("arm64-v8a")))
    }

    @Test fun `bug report link carries device details but nothing typed`() {
        val info = DeviceInfo("0.7.0", 2, "17", 37, "Google", "Pixel 7 Pro", "arm64-v8a")
        val url = AppUpdates.issueUrl(info) { URLEncoder.encode(it, "UTF-8") }
        assertTrue(url.startsWith("https://github.com/Yeamin-Talukder/jolpai-keyboard/issues/new?labels=bug&title="))
        val body = URLDecoder.decode(url.substringAfter("&body="), "UTF-8")
        assertTrue(body.contains("Jolpai Keyboard 0.7.0 (2)"))
        assertTrue(body.contains("Android: 17 (API 37)"))
        assertTrue(body.contains("Phone: Google Pixel 7 Pro"))
        assertTrue(body.contains("Processor: arm64-v8a"))
        assertFalse(url.contains(" ")) // fully encoded
    }

    @Test fun `changelog is newest first and only the newest entry may be unreleased`() {
        val rs = Changelog.RELEASES
        assertTrue(rs.isNotEmpty())
        assertTrue(rs.drop(1).all { it.released })
        val released = rs.filter { it.released }.map { it.version }
        assertEquals(released.sortedWith { a, b -> AppUpdates.compareVersions(b, a) }, released)
        assertTrue(released.contains("0.7.0"))
    }
}
