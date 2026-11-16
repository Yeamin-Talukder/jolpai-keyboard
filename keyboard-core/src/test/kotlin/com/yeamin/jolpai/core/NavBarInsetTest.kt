package com.yeamin.jolpai.core

import com.yeamin.jolpai.core.layout.NavBarInset
import org.junit.Assert.assertEquals
import org.junit.Test

class NavBarInsetTest {
    @Test fun `pixel edge to edge keeps clear of the taller keyboard bar`() {
        // Android 17 reports the short app bar (63 px) while the keyboard bar is 108 px.
        assertEquals(86, NavBarInset.bottomPadding(63, sideBar = false, imeBarHeight = 108, gapBelowWindow = 0))
    }

    @Test fun `a full reported bar is kept as is`() {
        // 3-button bar reported at full height, taller than the share of the keyboard bar.
        assertEquals(144, NavBarInset.bottomPadding(144, sideBar = false, imeBarHeight = 144, gapBelowWindow = 0))
    }

    @Test fun `window laid out above an opaque bar gets no extra room`() {
        // HyperOS / Android 14: the nav inset is already used up above the keyboard.
        assertEquals(0, NavBarInset.bottomPadding(0, sideBar = false, imeBarHeight = 96, gapBelowWindow = 0))
    }

    @Test fun `bar reported but the window ends above it`() {
        assertEquals(0, NavBarInset.bottomPadding(96, sideBar = false, imeBarHeight = 96, gapBelowWindow = 96))
    }

    @Test fun `only the overlapping part of the bar counts`() {
        assertEquals(36, NavBarInset.bottomPadding(96, sideBar = false, imeBarHeight = 96, gapBelowWindow = 60))
    }

    @Test fun `side bar in landscape leaves the bottom alone`() {
        assertEquals(0, NavBarInset.bottomPadding(0, sideBar = true, imeBarHeight = 108, gapBelowWindow = 0))
        assertEquals(20, NavBarInset.bottomPadding(20, sideBar = true, imeBarHeight = 108, gapBelowWindow = 0))
    }

    @Test fun `sides keep clear of the larger of side bar and cutout`() {
        assertEquals(0, NavBarInset.sidePadding(navBar = 0, cutout = 0))
        assertEquals(126, NavBarInset.sidePadding(navBar = 126, cutout = 0))
        assertEquals(118, NavBarInset.sidePadding(navBar = 0, cutout = 118))
        assertEquals(126, NavBarInset.sidePadding(navBar = 126, cutout = 118))
        assertEquals(0, NavBarInset.sidePadding(navBar = -1, cutout = -1))
    }

    @Test fun `negative values are treated as none`() {
        assertEquals(0, NavBarInset.bottomPadding(-5, sideBar = false, imeBarHeight = 108, gapBelowWindow = -3))
    }
}
