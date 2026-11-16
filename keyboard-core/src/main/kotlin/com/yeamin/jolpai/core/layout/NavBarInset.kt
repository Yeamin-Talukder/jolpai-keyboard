package com.yeamin.jolpai.core.layout

/**
 * How much room the keys must leave at the bottom for the system's navigation bar.
 *
 * Android reports the navigation-bar inset to a window as the part of the bar that overlaps it.
 * Under a keyboard the system can draw a taller bar (with its hide ⌄ and switch 🌐 buttons) than
 * the inset it reports (63 px vs 108 px on a Pixel 7 Pro, Android 17), so when the keyboard is
 * drawn behind the bar we keep clear of most of that taller bar.
 *
 * When the bar does not overlap the keyboard window at all (nothing is reported, or the window
 * ends above the bottom of the screen), the system has already laid the keyboard out above it,
 * and adding room again left an empty band about one key-row tall (Xiaomi HyperOS, Android 14).
 */
object NavBarInset {
    /** Share of the keyboard's nav bar the keys keep clear of; its top fifth is empty space. */
    const val IME_BAR_SHARE = 0.8f

    /**
     * Bottom space in px.
     *
     * @param reportedBottom the navigation-bar inset the system reports at the bottom.
     * @param sideBar the navigation bar sits at the left or right (landscape).
     * @param imeBarHeight height of the bar the system shows under a keyboard (0 if unknown).
     * @param gapBelowWindow how far the keyboard window's bottom edge sits above the bottom of
     *   the screen (0 when it reaches the bottom, or when it isn't known yet).
     */
    fun bottomPadding(reportedBottom: Int, sideBar: Boolean, imeBarHeight: Int, gapBelowWindow: Int): Int {
        val reported = reportedBottom.coerceAtLeast(0)
        // Nothing overlaps: the window already sits above the bar (or there is none).
        if (reported == 0) return 0
        if (sideBar) return reported
        val bar = maxOf(reported, (imeBarHeight * IME_BAR_SHARE).toInt())
        // The bar hangs from the bottom of the screen; only the part that reaches into the
        // window covers the keys.
        return (bar - gapBelowWindow.coerceAtLeast(0)).coerceAtLeast(0)
    }

    /**
     * Space in px at the left or right: clear of a side navigation bar and of a camera cutout
     * (in landscape the punch hole sits at a side, and from Android 15 the keyboard is drawn
     * over it). Both are reported as the part that overlaps the window, so they don't add up.
     */
    fun sidePadding(navBar: Int, cutout: Int): Int = maxOf(navBar, cutout, 0)
}
