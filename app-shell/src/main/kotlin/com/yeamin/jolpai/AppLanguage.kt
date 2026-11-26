package com.yeamin.jolpai

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeamin.jolpai.core.gesture.GestureAction
import com.yeamin.jolpai.core.gesture.GestureType
import com.yeamin.jolpai.core.gesture.GestureZone
import com.yeamin.jolpai.core.model.LayoutId
import com.yeamin.jolpai.core.model.ToolbarItem
import com.yeamin.jolpai.core.settings.HapticLevel
import com.yeamin.jolpai.core.settings.KeyFont
import com.yeamin.jolpai.core.settings.KeyboardStyle
import com.yeamin.jolpai.core.settings.PanelPlacement
import com.yeamin.jolpai.core.settings.SoundPack
import com.yeamin.jolpai.core.settings.SplitKeys
import com.yeamin.jolpai.core.settings.VoiceLanguage
import com.yeamin.jolpai.data.ClipRetention
import java.util.Locale

/**
 * The settings app's own language: English or বাংলা. It is separate from the keyboard's
 * layouts, and it changes only this app (the keyboard's own panels stay as they are).
 *
 * Every string the app shows is written where it is shown as `tr("English", "বাংলা")`.
 * [bangla] is Compose snapshot state, so every screen that read a string redraws the moment
 * the language changes: no activity restart, and the page stack and scroll positions stay.
 * Choose strings only while drawing (or right before showing a toast); a tr() result kept in
 * `remember {}` or a ViewModel would stay in the old language.
 */
object AppLanguage {
    private const val PREFS = "app_ui"
    private const val KEY = "language"

    var bangla by mutableStateOf(false)
        private set

    /** Reads the saved choice. Before the first choice, a phone set to Bangla starts in Bangla. */
    fun load(context: Context) {
        bangla = when (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)) {
            "bn" -> true
            "en" -> false
            else -> Locale.getDefault().language == "bn"
        }
    }

    fun set(context: Context, bangla: Boolean) {
        this.bangla = bangla
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, if (bangla) "bn" else "en")
            .apply()
    }
}

/** The text for the app's current language. */
fun tr(en: String, bn: String): String = if (AppLanguage.bangla) bn else en

private const val BANGLA_DIGITS = "০১২৩৪৫৬৭৮৯"

/** In Bangla, ASCII digits become Bangla digits ("12 words" → "১২টি শব্দ"); in English unchanged. */
fun String.digits(): String =
    if (!AppLanguage.bangla) this
    else buildString(length) { for (c in this@digits) append(if (c in '0'..'9') BANGLA_DIGITS[c - '0'] else c) }

/** A number in the app's language: 1234 → "1234" or "১২৩৪". */
fun num(n: Number): String = n.toString().digits()

/**
 * English | বাংলা, a small two-part pill. Each part names its language in that language, so
 * someone who reads only one of them can still find it.
 */
@Composable
fun LanguageSwitch(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.semantics { contentDescription = "App language / অ্যাপের ভাষা" },
    ) {
        Row(Modifier.padding(3.dp).selectableGroup()) {
            LanguageChip("English", selected = !AppLanguage.bangla) { AppLanguage.set(context, false) }
            LanguageChip("বাংলা", selected = AppLanguage.bangla) { AppLanguage.set(context, true) }
        }
    }
}

@Composable
private fun LanguageChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "langChip",
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "langChipText",
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/*
 * Names from the shared keyboard modules (keyboard-core, core-data), which keep their English
 * labels for the keyboard itself. The settings app shows these instead.
 */

/** "English" / "ইংরেজি"; the Bangla layouts already have Bangla names. */
val LayoutId.uiName: String get() = if (this == LayoutId.ENGLISH) tr("English", "ইংরেজি") else displayName

val HapticLevel.uiLabel: String
    get() = when (this) {
        HapticLevel.OFF -> tr("Off", "বন্ধ")
        HapticLevel.SYSTEM -> tr("Phone default", "ফোনের নিজস্ব")
        HapticLevel.LIGHT -> tr("Light", "হালকা")
        HapticLevel.MEDIUM -> tr("Medium", "মাঝারি")
        HapticLevel.STRONG -> tr("Strong", "জোরালো")
    }

val SoundPack.uiLabel: String
    get() = when (this) {
        SoundPack.SYSTEM -> tr("Phone's click", "ফোনের ক্লিক")
        SoundPack.SOFT -> tr("Soft tap", "নরম টোকা")
        SoundPack.TYPEWRITER -> tr("Typewriter", "টাইপরাইটার")
        SoundPack.BUBBLE -> tr("Bubble", "বুদবুদ")
        SoundPack.WOOD -> tr("Wooden", "কাঠের")
    }

val SplitKeys.uiLabel: String
    get() = when (this) {
        SplitKeys.OFF -> tr("Never", "কখনো না")
        SplitKeys.LANDSCAPE -> tr("In landscape", "ফোন আড়াআড়ি থাকলে")
        SplitKeys.ALWAYS -> tr("Always", "সবসময়")
    }

val VoiceLanguage.uiLabel: String
    get() = when (this) {
        VoiceLanguage.AUTO -> tr("Same as the keyboard layout", "কীবোর্ডের লেআউট অনুযায়ী")
        VoiceLanguage.BANGLA -> tr("বাংলা (Bangladesh)", "বাংলা (বাংলাদেশ)")
        VoiceLanguage.ENGLISH -> tr("English", "ইংরেজি")
    }

val PanelPlacement.uiLabel: String
    get() = when (this) {
        PanelPlacement.ABOVE -> tr("Above the keyboard", "কীবোর্ডের ওপরে")
        PanelPlacement.KEYS -> tr("In place of the keys", "কী-গুলোর জায়গায়")
    }

val PanelPlacement.uiSummary: String
    get() = when (this) {
        PanelPlacement.ABOVE -> tr("The keys stay; any key closes it", "কী-গুলো থাকে; যেকোনো কী চাপলে বন্ধ হয়")
        PanelPlacement.KEYS -> tr("Tap the button again to get the keys back", "কী ফেরাতে বোতামটি আবার চাপুন")
    }

val KeyFont.uiLabel: String
    get() = when (this) {
        KeyFont.SYSTEM -> tr("Phone default", "ফোনের নিজস্ব")
        KeyFont.CUSTOM -> tr("Your own font", "নিজের ফন্ট")
        else -> label
    }

val KeyFont.Group.uiTitle: String
    get() = when (this) {
        KeyFont.Group.BANGLA -> tr("Bangla and English", "বাংলা ও ইংরেজি")
        KeyFont.Group.BANGLA_STYLISH -> tr("Stylish Bangla", "স্টাইলিশ বাংলা")
        KeyFont.Group.ENGLISH -> tr("English", "ইংরেজি")
        KeyFont.Group.STYLISH -> tr("Stylish English", "স্টাইলিশ ইংরেজি")
        KeyFont.Group.OTHER -> ""
    }

val ClipRetention.uiLabel: String
    get() = when (this) {
        ClipRetention.HOUR -> tr("1 hour", "১ ঘণ্টা")
        ClipRetention.DAY -> tr("1 day", "১ দিন")
        ClipRetention.WEEK -> tr("7 days", "৭ দিন")
        ClipRetention.MONTH -> tr("30 days", "৩০ দিন")
        ClipRetention.FOREVER -> tr("Until I delete them", "আমি না মোছা পর্যন্ত")
    }

/** What a toolbar button does (its name is [toolbarName]). */
val ToolbarItem.uiTitle: String
    get() = when (this) {
        ToolbarItem.EMOJI -> tr("Emoji, stickers and GIFs", "ইমোজি, স্টিকার ও GIF")
        ToolbarItem.CLIPBOARD -> tr("Copied text and snippets", "কপি করা লেখা ও স্নিপেট")
        ToolbarItem.VOICE -> tr("Type by speaking", "কথা বলে টাইপ")
        ToolbarItem.EDIT -> tr("Select, copy, paste, undo", "সিলেক্ট, কপি, পেস্ট, আনডু")
        ToolbarItem.TRANSLATE -> tr("Translate English ⇄ Bangla", "ইংরেজি ⇄ বাংলা অনুবাদ")
        ToolbarItem.CHECK -> tr("Check spelling and grammar", "বানান ও ব্যাকরণ যাচাই")
        ToolbarItem.NUMBER_PAD -> tr("Big number keys", "বড় নম্বর কী")
    }

val KeyboardStyle.uiLabel: String
    get() = when (this) {
        KeyboardStyle.CLASSIC -> tr("Classic", "ক্লাসিক")
        KeyboardStyle.GBOARD -> tr("Simple", "সহজ")
        KeyboardStyle.MODERN -> tr("Modern", "মডার্ন")
    }

val KeyboardStyle.uiSummary: String
    get() = when (this) {
        KeyboardStyle.CLASSIC -> tr(
            "Like Ridmik: slide the space bar to change language, emoji and clipboard in place of the keys",
            "রিদমিকের মতো: স্পেস বার টেনে ভাষা বদল, ইমোজি ও ক্লিপবোর্ড কী-এর জায়গায়",
        )
        KeyboardStyle.GBOARD -> tr(
            "Like Gboard: few buttons on top, emoji and clipboard in place of the keys",
            "জিবোর্ডের মতো: ওপরে অল্প কয়েকটি বোতাম, ইমোজি ও ক্লিপবোর্ড কী-এর জায়গায়",
        )
        KeyboardStyle.MODERN -> tr(
            "Every tool on top; emoji and clipboard open above the keys",
            "সব টুল ওপরে; ইমোজি ও ক্লিপবোর্ড কী-এর ওপরে খোলে",
        )
    }

val GestureZone.uiTitle: String
    get() = when (this) {
        GestureZone.SPACE -> tr("Space bar", "স্পেস বার")
        GestureZone.COMMA -> tr("Comma key", "কমা কী")
        GestureZone.PERIOD -> tr("Full stop key", "দাঁড়ি (ফুল স্টপ) কী")
        GestureZone.SHIFT -> tr("Shift", "শিফট")
        GestureZone.BACKSPACE -> tr("Backspace", "ব্যাকস্পেস")
        GestureZone.TOOLBAR -> tr("Top bar", "ওপরের বার")
    }

val GestureType.uiTitle: String
    get() = when (this) {
        GestureType.SWIPE_UP -> tr("Swipe up", "ওপরে সোয়াইপ")
        GestureType.SWIPE_DOWN -> tr("Swipe down", "নিচে সোয়াইপ")
        GestureType.SWIPE_LEFT -> tr("Swipe left", "বাঁয়ে সোয়াইপ")
        GestureType.SWIPE_RIGHT -> tr("Swipe right", "ডানে সোয়াইপ")
        GestureType.LONG_PRESS -> tr("Long-press", "চেপে ধরা")
        GestureType.DOUBLE_TAP -> tr("Double-tap", "দুবার ট্যাপ")
    }

val GestureAction.uiTitle: String
    get() = when (this) {
        GestureAction.EMOJI -> tr("Open emoji panel", "ইমোজি খুলুন")
        GestureAction.CLIPBOARD -> tr("Open clipboard", "ক্লিপবোর্ড খুলুন")
        GestureAction.GIF -> tr("Open GIF panel", "GIF খুলুন")
        GestureAction.STICKERS -> tr("Open stickers", "স্টিকার খুলুন")
        GestureAction.VOICE -> tr("Voice typing", "ভয়েস টাইপিং")
        GestureAction.TRANSLATE -> tr("Open translation", "অনুবাদ খুলুন")
        GestureAction.AI_WRITING -> tr("AI writing assistant", "এআই লেখার সহকারী")
        GestureAction.SETTINGS -> tr("Open keyboard settings", "কীবোর্ড সেটিংস খুলুন")
        GestureAction.SYMBOLS -> tr("Open symbols", "চিহ্ন (?123) খুলুন")
        GestureAction.NEXT_LANGUAGE -> tr("Next language", "পরের ভাষা")
        GestureAction.PREVIOUS_LANGUAGE -> tr("Previous language", "আগের ভাষা")
        GestureAction.TOGGLE_NUMBER_ROW -> tr("Toggle number row", "নম্বরের সারি চালু/বন্ধ")
        GestureAction.TOGGLE_ONE_HANDED -> tr("Toggle one-handed mode", "এক হাতে টাইপ চালু/বন্ধ")
        GestureAction.TOGGLE_FLOATING -> tr("Toggle floating keyboard", "ভাসমান কীবোর্ড চালু/বন্ধ")
        GestureAction.INSERT_PERIOD -> tr("Insert full stop", "দাঁড়ি বসান")
        GestureAction.INSERT_COMMA -> tr("Insert comma", "কমা বসান")
        GestureAction.INSERT_TEXT -> tr("Insert custom text", "নিজের লেখা বসান")
        GestureAction.SELECT_ALL -> tr("Select all", "সব সিলেক্ট")
        GestureAction.COPY -> tr("Copy", "কপি")
        GestureAction.CUT -> tr("Cut", "কাট")
        GestureAction.PASTE -> tr("Paste", "পেস্ট")
        GestureAction.UNDO -> tr("Undo", "আনডু")
        GestureAction.REDO -> tr("Redo", "রিডু")
        GestureAction.NUMBER_PAD -> tr("Number pad", "নম্বর প্যাড")
    }

val GestureAction.uiDescription: String
    get() = when (this) {
        GestureAction.EMOJI -> tr("Emoji, kaomoji and search", "ইমোজি, কাওমোজি ও খোঁজ")
        GestureAction.CLIPBOARD -> tr("Clipboard history and snippets", "কপির ইতিহাস ও স্নিপেট")
        GestureAction.GIF -> tr("Trending GIFs and GIF search", "ট্রেন্ডিং GIF ও GIF খোঁজা")
        GestureAction.STICKERS -> tr("Your own stickers", "আপনার নিজের স্টিকার")
        GestureAction.VOICE -> tr("Dictate in the top bar", "ওপরের বারে বলে লিখুন")
        GestureAction.TRANSLATE -> tr("Not available yet", "এখনো নেই")
        GestureAction.AI_WRITING -> tr("Not available yet", "এখনো নেই")
        GestureAction.SETTINGS -> tr("The Jolpai app", "জলপাই অ্যাপ")
        GestureAction.SYMBOLS -> tr("The ?123 page", "?123 পাতা")
        GestureAction.NEXT_LANGUAGE -> tr("Switch to the next layout", "পরের লেআউটে যান")
        GestureAction.PREVIOUS_LANGUAGE -> tr("Switch to the previous layout", "আগের লেআউটে যান")
        GestureAction.TOGGLE_NUMBER_ROW -> tr("Show or hide the digits row", "সংখ্যার সারি দেখান বা লুকান")
        GestureAction.TOGGLE_ONE_HANDED -> tr("Keys to one side, or back", "কী-গুলো এক পাশে, বা আবার মাঝে")
        GestureAction.TOGGLE_FLOATING -> tr("Float the keyboard, or dock it", "কীবোর্ড ভাসিয়ে রাখুন, বা নিচে বসান")
        GestureAction.INSERT_PERIOD -> tr("\".\" (or \"।\" on Bangla layouts)", "\"।\" (ইংরেজিতে \".\")")
        GestureAction.INSERT_COMMA -> tr("\",\"", "\",\"")
        GestureAction.INSERT_TEXT -> tr("Text you choose, e.g. @gmail.com", "আপনার বেছে নেওয়া লেখা, যেমন @gmail.com")
        GestureAction.SELECT_ALL -> tr("Select the whole field", "পুরো লেখা সিলেক্ট")
        GestureAction.COPY -> tr("Copy the selection", "সিলেক্ট করা অংশ কপি")
        GestureAction.CUT -> tr("Cut the selection", "সিলেক্ট করা অংশ কাট")
        GestureAction.PASTE -> tr("Paste the clipboard", "কপি করা লেখা পেস্ট")
        GestureAction.UNDO -> tr("Takes back your last change", "শেষ পরিবর্তনটি ফিরিয়ে নেয়")
        GestureAction.REDO -> tr("Puts back what Undo took away", "আনডু করা জিনিস ফিরিয়ে আনে")
        GestureAction.NUMBER_PAD -> tr("Open the number pad, or go back to the letters", "নম্বর প্যাড খুলুন, বা অক্ষরে ফিরুন")
    }
