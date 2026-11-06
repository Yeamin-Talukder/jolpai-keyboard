# Roadmap and feature status

Legend: ✅ done · 🔨 in progress · ⬜ pending · 🔍 investigate (built only if it can be made reliable) · ⚠️ needs an external key or service

Architecture and reasons: [ARCHITECTURE.md](ARCHITECTURE.md)

**Rule for every phase:** build passes → tests pass → manual check on the phone → this file
updated. Do not start the next phase while a critical issue from the current one is open.

---

## Phase 1 — Research and architecture ✅
- ✅ Check the dev environment (Studio 2026.1.4, SDK 34–37, no emulator → real phone)
- ✅ National (জাতীয়) layout verified with the user (HTML demo, 2026-11-06)
- ✅ Android IME, clipboard, speech, rich-content and GIF limits researched
- ✅ Architecture document
- ✅ App name **Lipi Keyboard**, package id `com.yeamin.lipi` (chosen 2026-11-06; the display name can change any time, the package id only before the first Play Store upload)
- ✅ Renamed to **Jolpai Keyboard (জলপাই)** by the user on 2026-11-07 (Play Store title "Jolpai — Bangla Keyboard", olive-green icon planned). Only user-visible text changed; the package id, Kotlin class names (`Lipi*`), database and the `lipi-theme-1` / `lipi-backup-1` file formats keep "lipi" so installs and old backups still work. New launcher icon (2026-11-07): an olive with stem and two leaves on cream #F3EFD9, adaptive with a separate monochrome layer for themed icons (app-shell res/drawable/ic_launcher_*.xml). Still to do: Play Store 512px icon and feature graphic; decide whether to change the package id before the first Play Store upload.
- ✅ Default keyboard theme ("System", used on a fresh install) is now **Material You**: wallpaper colours in the phone's light or dark mode (Android 12+; plain Light/Dark colours before that). User request 2026-11-07. `BuiltInThemes.forSystem()`; theme thumbnails now show the real wallpaper colours for Material You themes too.
- ✅ Seven soft dark themes added 2026-11-07 (user request "cool and soft dark"): Olive night (Jolpai brand), Misty blue, Arctic night, Storm, Moonlight, Sage night, Periwinkle. Muted cool colours, pastel accents; listed first among the dark themes.

## Phase 2 — Core keyboard engine 🔨
Status 2026-11-06: builds, 79 unit tests pass (incl. the 413-case Avro corpus), lint clean. **Not yet run on a phone.**
- ✅ Project scaffold: AGP 9.4.1, Gradle 9.6.1, Kotlin 2.3.20, Hilt 2.60.1, KSP, compile/target SDK 37, min SDK 26
- ✅ Modules created so far: `bangla-engine` (JVM), `keyboard-core`, `keyboard-ui`, `app-shell` (others are added in the phase that needs them)
- ✅ `LipiImeService` (`InputMethodService`) + IME declaration (English and Bangla subtypes)
- ✅ Setup app: enable/choose keyboard with live status, test fields for every input type, typing settings
- ✅ `EditorSession`: InputConnection wrapper, selection tracking; `FieldClassifier` for password/email/URL/number/phone/multiline/no-learning fields
- ✅ Canvas `KeyboardView`: multi-touch with in-order commit for fast typing, key preview, long-press choices, slide between keys
- ✅ English QWERTY: shift, double-tap caps lock, auto-capitalisation, double-space full stop, accents on long-press
- ✅ Backspace: acts on press, repeats when held; deletes Bangla one code point at a time, emoji as a whole
- ✅ Enter key: editor action (Send, Search, Go, Next, Done) or newline, from `imeOptions`
- ✅ Symbol pages (English and Bangla digits), number pad, phone pad
- ✅ Email/URL fields: `@` or `/` on the bottom row; email, URL and password fields open in English
- ✅ National (জাতীয়) layout + `NationalComposer`: ্+kar → vowel, standalone kar → vowel, ো/ৌ, explicit hasanta, optional reph reordering
- ✅ Language key: tap to cycle, long-press to pick a layout or open the system keyboard picker; last layout remembered; default layout setting
- ✅ Private-field indicator in the toolbar
- ✅ Avro phonetic: OpenBangla's Avro rule table (MPL-2.0, commit `92902e3`, unmodified, notice in `bangla-engine/src/main/resources/avro/`) runs on our own engine; it matches the reference parser on all 413 of its test cases
- ✅ Avro layout: the word shows as composing text; Space/Enter/layout switch commit it; backspace edits the Roman letters; toolbar chips insert the Bangla word or keep the English as typed; Bangla digits; double-space types "। "
- ✅ Layouts setting: choose which of English / অভ্র / জাতীয় the 🌐 key cycles through
- Note: rule-based Avro is literal (`bhalobashi` → ভালবাশি; `bhalObasi` → ভালোবাসি). Phase 3 adds dictionary candidates.
- 🔨 Manual test on the phone. First device run 2026-11-06 on a Pixel 7 Pro (Android 17, SDK 37) over wireless adb: installs, enables and selects as keyboard; the setup screen detects it; জাতীয় typing works in Telegram (আ). **Bug found and fixed:** the key-preview overlay and the Compose panel area measured themselves to all available height, so the input window covered ~95 % of the screen (2232 px); they now take exactly the keyboard's height (`KeyPopupView.onMeasure`, `OverlayFrame`), and the keyboard is 787 px tall. No crashes logged; memory ~139 MB total PSS with dictionaries loaded. Still to check: WhatsApp, Messenger, Chrome, Gmail, password fields, the app's own test fields
- Changed from the plan: space-bar swipe is used for cursor movement (Phase 4), so language switching uses the 🌐 key and toolbar chip instead of a space swipe

## Phase 3 — Suggestions 🔨
Status 2026-11-06: built and unit-tested on the PC. **Not yet run on a phone.**

Data (all licence-clean; notices in `suggestion-engine/src/main/resources/{en,bn}/`):
- English: AOSP LatinIME `en_US_wordlist.combined` (Apache-2.0): 160,668 words with frequencies, 1,300 flagged offensive, 47 misspelling shortcuts
- Bangla words + Avro phonetic patterns: OpenBangla upodesh (MIT), commit `fc61225`: 159,425 words
- Avro suffixes and Roman autocorrections: OpenBangla riti (MPL-2.0), commit `e1fd5a8`, unmodified
- Regenerate with `tools/prepare-dictionaries.pl`

Done:
- ✅ New modules: `suggestion-engine` (pure Kotlin) and `core-data` (Room)
- ✅ Compact sorted word lists (one `CharArray` per language, a few MB each, loaded in the background)
- ✅ English completions by frequency; typo correction with key-proximity, swap, apostrophe and double-letter costs
- ✅ Autocorrect on space, Enter and punctuation; only when confident; never in password, email, URL or no-suggestion fields; Backspace right after undoes it, and the undone word isn't corrected again
- ✅ Capitalisation kept (Teh → The, i → I)
- ✅ Avro dictionary candidates: Kotlin port of upodesh's search plus riti's ranking and suffixes (গুলো, ের …); matches every expected result in both projects' own tests
- ✅ Avro: the word you pick for a Roman spelling becomes the default next time; the English spelling is always offered last
- ✅ জাতীয়: word completions from the Bangla word list
- ✅ Suggestion strip: typed word, then best suggestion (highlighted when space will autocorrect to it), then the next; Avro candidates scroll sideways
- ✅ Next-word suggestions from word pairs the user has typed (English and Bangla)
- ✅ Learning stored in Room on the device; never in password, incognito/no-learning or no-suggestion fields; excluded from cloud backup and device transfer
- ✅ Personal dictionary: add from the strip ("Add to dictionary" after keeping an unknown word) or in the app; remove in the app
- ✅ Settings: show suggestions, autocorrect, next-word suggestions, block offensive words, learn from my typing, clear learned data
- ✅ Speed (PC, warmed up): English with typo search ~1.5 ms per key; Avro candidates ~0.5 ms per key. English/জাতীয় suggestions run on a background thread so a slow phone never delays a key press; Avro runs inline because it decides the underlined text

Not done / limits:
- ⬜ Manual test on a phone (typing feel, suggestion quality, speed on a low-end phone)
- ⬜ Personal dictionary import/export (comes with backup and restore, Phase 7)
- ⬜ Remove a single learned word from the strip (long-press). For now, "clear learned data" clears everything
- No built-in next-word model: the AOSP list has no word pairs, so next-word suggestions start empty and grow as you type
- Bangla words have no frequency data, so জাতীয় completions prefer words you've typed, then shorter words
- Grammar correction, sentence completion and rewriting need a language model; still planned as an optional add-on

## Phase 4 — Clipboard and productivity 🔨
Status 2026-11-06: built and unit-tested on the PC. **Not yet run on a phone**, and clipboard
capture and image paste can only be checked there.

Done:
- ✅ New module `clipboard-manager`; Room database v2 (clip and snippet tables) with a 1→2 migration
- ✅ Clipboard capture with `OnPrimaryClipChangedListener` while Lipi is the active keyboard (Android allows only the default IME and the focused app); no polling
- ✅ Never saved: copies while typing in a password/private field, and clips marked `EXTRA_IS_SENSITIVE` (password managers, banking apps)
- ✅ Retention: 1 hour / 1 day (default) / 7 days / 30 days / until deleted; at most 100 unpinned items; pinned items are kept
- ✅ Copying the same text again moves it to the top instead of adding a duplicate
- ✅ Images: copied into private storage (≤ 10 MB), shown as thumbnails, pasted with `commitContent` + read-only URI grant only where the app accepts that image type (otherwise the panel says so)
- ✅ 📋 panel in the keyboard: recent / pinned / snippets / folders; tap to paste; long-press for pin, copy, move to folder, save as snippet, delete; clear unpinned (tap twice)
- ✅ App clipboard screen: search, filter, copy, pin, folders (create by name), delete, clear
- ✅ Snippets: name, optional shortcut, text; typing the shortcut then space expands it (all layouts, including Avro); the strip offers it as you type; managed in the app; shown in the 📋 panel
- ✅ ✎ editing panel: select mode, select all, copy, cut, paste, undo/redo (Ctrl+Z, labelled "where supported"), cursor keys and line start/end (repeat while held), select word left/right, delete word, insert date/time (Bangla digits and month names on Bangla layouts)
- ✅ Swipe gestures (setting, on by default): slide on the space bar to move the cursor; slide left from ⌫ to select whole words, release to delete
- ✅ Calculator: type `12*3+4=` and the strip offers `40` (also × ÷ − and Bangla digits)
- ✅ Smarter tools (2026-11-06, user request):
  - Hold x / c / v (English and Avro letters) to cut / copy / paste, like Ctrl+X/C/V (c's accented letters gave way to this)
  - Calculator without "=": `250*4` offers `= 1000`, and tapping replaces the sum with the answer (`12*3+4=` still adds the answer after "="). To keep phone numbers and dates out: a lone - or / needs spaces around it unless + * × ÷ is used, numbers with a leading 0 rule it out, and the sum must start a word
  - Automatic clipboard categories (`ClipCategory`, worked out on the phone from the text): links, emails, codes & OTPs, phone numbers, addresses (English and Bangla words), code, images. Filter chips in the keyboard panel and the app (with icons); search also matches the category name
  - Smart snippets: each snippet has a category (messages, quick replies, signatures, addresses, phone numbers, emails, code, other), suggested from the text and changeable; templates to start from (email signature, address, phone number, quick reply, message, code); filter and search in the app and the keyboard panel. Database v5 (4→5 migration adds `snippet.category`; checked on the phone: the database upgraded to v5 without errors); backups include the category
- ✅ Number and phone pads redesigned (2026-11-06): number fields get a calculator-style 4×5 pad (operators + − * / on the left, wide digits, ⌫ ␣ % ↵ on the right, `,` and `.` beside 0; hold a digit for its Bangla form); phone fields get a dial pad with letters (ABC, DEF …) on the digits, + * # - ( ) and hold 0 for +
- ✅ Panels lost their "ABC" back buttons (clipboard, emoji, editing, voice): the toolbar icon that opened a panel closes it again. The emoji panel's ⌫ moved to the end of its tab row, so the emoji grid gained the bottom row's height
- ✅ Fixed (2026-11-06): opening the clipboard crashed the keyboard. A category pattern used an optional word boundary (word-boundary marker with a question mark after it) that Android's ICU regex engine rejects but the PC's Java engine accepts, so the unit tests passed. Lesson: keep regexes to features both engines share
- ✅ Data protection (2026-11-06, user asked whether typed words are protected):
  - Database encrypted with SQLCipher 4.19.1 (`SupportOpenHelperFactory`); the 256-bit key is random, stored wrapped by an AES-GCM key in the Android Keystore (`DatabaseKey`, prefs excluded from backup). An existing plain database is converted in place on first open (`sqlcipher_export`); if that fails the plain database keeps working and conversion is retried next start; if the key is ever lost the unreadable database is replaced by an empty one rather than crashing. Checked on the phone: header changed from "SQLite format 3" to random bytes, and the 112 learned words and settings survived
  - Clipboard pictures stored encrypted (AES-GCM, `ClipImages`); pasting decrypts a copy into the cache for that one paste (deleted after 5 minutes); older plain pictures are converted
  - Copied one-time codes (4–8 digits) are deleted from history after 1 minute unless pinned
  - The phone now runs the release build (R8, not debuggable: `run-as` refused), signed with the debug key so it updated in place. Publishing still needs a real signing key (see README "Release builds"); switching to it needs a reinstall
  - Two bugs found on the phone and fixed during this: the conversion's ATTACH ran on a different connection than the later statements (fixed by using execSQL on the single connection), and ATTACH could not create the encrypted file because the plain database was opened without CREATE
- ✅ Predictive touch given more room (user request): a likely key may now reach 0.45 key widths past its edge (was 0.3); the always-direct centre is the middle 40 % (was 50 %). Simulation unchanged for common English (97.2 %) and জাতীয় (97.1 %); names/terms 90.9 % (plain 90.4 %); random letters 91.5 % (plain 92.5 %)
- ✅ Number and phone pads have an ABC (কখগ on জাতীয়) key back to the letters, whose ?123 key then returns to the pad; % moved to holding / on the number pad
- ✅ Voice typing stays in the keyboard (user request, Google voice typing style): the panel has a language chip on top, the words as you speak and a pulsing microphone (reacts to your voice) in the middle, and keyboard (back to keys) and backspace buttons at the bottom; no settings button. Microphone permission is asked with Android's own dialog over the app being typed in (an invisible `MicPermissionActivity`), and listening starts when you come back; only if the user blocked the dialog for good does it offer Android's app settings. Built and unit tests pass; not yet installed (phone not connected)
- ✅ Custom gesture shortcuts (user brief): `gesture/GestureShortcuts.kt` (zones, gesture types, action registry with availability, conflicts/reserved gestures, defaults, versioned text storage in DataStore, swipe classifier, double-tap tracker), recognition in `KeyboardView` (swipes, holds, double-taps per key zone; built-in swipes yield only for directions that have a shortcut; one action per touch) and the top bar (`SwipeToolbar`), actions run through existing key actions (`KeyAction.Shortcut`, host commands for settings and mode toggles, emoji panel opened on the GIF or sticker tab), and a Custom gestures screen (master switch, active count, restore defaults, sensitivity, distance and hold sliders, gesture list, editor with animated demo, conflict warnings and a test pad using the same recogniser). Translation and AI writing are listed as unavailable. Tested in `GestureShortcutsTest` (18 tests): every swipe direction, minimum distance, sensitivity (a 35° swipe passes when loose, fails when strict), hold time from settings, double-tap window, reserved and freed gestures, replacement warnings, repeated actions, the dispatcher ignoring disabled/unavailable/textless/reserved gestures, storage round trip, broken/outdated/unknown entries, every action running through the controller (panels, GIF tab, symbols, languages, custom text, full stop per layout, editing commands, service commands), unavailable actions doing nothing, and gestures off in password fields. Typing safety: letter keys carry no gesture zone (gestures only on Space, comma, full stop, Shift, Backspace and the top bar), and 20,000 simulated taps with realistic drift never registered as a swipe even at the loosest settings. Limits: a double-tap shortcut delays that key's single tap by up to 280 ms (said in the editor); triple-tap is not offered (not reliable enough next to double-tap); toolbar swipes don't work over scrolling Avro candidates. On the phone (2026-11-07): installed, no crashes, toolbar toggle confirmed; the space-bar swipe test was inconclusive because the user was typing at the same moment
- ✅ Voice typing in the top bar (user request, like Google voice typing): tapping the mic turns the top bar into a voice bar (pulsing mic that swells with your voice, animated waveform, the words as you speak, language chip, collapse, shrink and close buttons) while the keys stay usable; typing a key ends dictation first (what was heard is kept). Holding the mic opens the full voice panel. Shrinking the keyboard while dictating, by button or by swiping on the voice bar: down collapses the keys (only the bar remains, the app gets the room), up brings them back; left or right shrinks the bar to a pill at that side with the rest of the strip passing touches to the app (touchable region, like floating mode). The Voice gesture action now starts the voice bar. Installed on the phone without crashes; not tried with the microphone by Claude (it would record on the user's phone)
- ✅ Minimised voice pill (user request, like Google's keyboard): swiping the voice bar left or right turns it into a small floating pill, wide (grip, mic, waveform, words, language, turn, back, close) or tall (the same stacked, waveform sideways). Drag it by the grip anywhere in the lower 45 % of the screen, kept above the navigation bar; swipe up or down on the wide pill to make it tall, left or right on the tall pill to make it wide, or tap the turn button; the back button returns to the bar with the keys. While it shows, the keyboard window is a see-through area and only the pill takes touches, so the app is usable around it (same mechanism as floating mode). Not in floating mode (collapse only there). Installed without crashes; not tried with the microphone by Claude
  - Reworked after feedback (pill badly sized, swipe-to-turn awkward, no way to fix mistakes): the pill is now a row (or column) of equal 40 dp round controls — grip, mic, waveform, language, ⌫, turn, back to keyboard, close — without the words (they already appear in the text field). Drag it by any part; dropping a wide pill against the left or right edge turns it tall and docks it there, dragging a tall pill back towards the middle turns it wide (like Google's keyboard); the turn button still works. ⌫ in the pill and in the collapsed bar deletes a character and repeats while held; listening pauses (what was heard is kept) and resumes 0.7 s after the last delete
- ✅ Clipboard images and the database are excluded from backups and device transfer

Not done / limits:
- ⬜ Manual test on a phone (capture, image paste in WhatsApp/Gmail/Messages, gestures)
- Search inside the keyboard's 📋 panel isn't possible yet (the keyboard can't type into its own panel). Search is in the app's clipboard screen
- Android 12+ may show a "pasted from clipboard" notice when Lipi reads a copy; to be confirmed on a phone
- Formatted (rich) text is saved as plain text

## Phase 5 — Voice, emoji, media 🔨
Status 2026-11-06: built and unit-tested on the PC. **Not yet run on a phone.** Voice, GIF
sending and stickers can only be checked on a device.

Data: Unicode Emoji 17.0 `emoji-test.txt` and CLDR 49 English and Bangla annotations
(Unicode License V3; notice in `emoji-engine/src/main/resources/emoji/`), regenerated with
`tools/prepare-emoji.pl`. 1,914 emoji, 330 with skin tones, Bangla keywords for 1,910.

Done:
- ✅ New modules: `emoji-engine` (pure Kotlin), `voice-input`, `media`; database v3 adds emoji recents/favourites (2→3 migration)
- ✅ 😊 panel: recents, favourites, 9 categories, skin tones (long-press; default tone setting), emoji the phone can't draw are hidden (`PaintCompat.hasGlyph`), ⌫ and ABC
- ✅ Emoji search in English and Bangla (pizza, বিড়াল …) using in-keyboard search mode
- ✅ In-keyboard search mode: the keys type into a panel's search box instead of the app (any layout, including Avro); live emoji results in the strip; used by emoji, GIF and clipboard search
- ✅ Emoji suggestions in the strip for typed words, English and Bangla (also for Avro words), placed after the word
- ✅ Kaomoji tab
- ✅ Stickers: import pictures in the app (Photo Picker, PNG/WebP/JPEG/GIF ≤ 2 MB), send with `commitContent` where the app accepts images
- ⚠️ GIFs: GIPHY with the user's own API key (Tenor's API closed 2026-06-30); trending + search; "Powered by GIPHY"; sent as `image/gif` via `commitContent`; off (with an explanation) until a key is set; only the search words go to GIPHY
- ✅ 🎤 panel: Android `SpeechRecognizer`, continuous dictation (restarts after pauses), live words shown as composing text, tap to stop/start, বাংলা ⇄ English switch, ⌫ word, clear errors (no permission, no internet, language unavailable, no speech service)
- ✅ Offline status per language from the speech service (Android 13+ `checkRecognitionSupport`), with "Download" where the service offers an offline pack. Offline Bangla is **not** claimed unless the phone reports it
- ✅ Spoken punctuation in English and Bangla (comma/কমা, full stop/দাঁড়ি, question mark/প্রশ্নবোধক চিহ্ন, new line/নতুন লাইন …)
- ✅ Microphone permission asked in the app with an explanation, never from the keyboard
- ✅ Toolbar: choose and reorder 😊 📋 🎤 ✎ in the app; password fields show only ✎

Not done / limits:
- ⬜ Manual test on a phone (voice in Bangla and English, GIF sending, stickers in WhatsApp/Messages)
- GIF previews are still frames (the first frame), not animated
- Handwriting: still "investigate"
- Emoji combinations (Emoji Kitchen style) need Google's service; not planned

## Phase 6 — Customisation 🔨
Status 2026-11-06: built and unit-tested on the PC. **Not yet run on a phone.** Floating mode
especially needs checking on a device, because it changes how the keyboard window takes touches.

Done:
- ✅ New module `theme-engine` (pure Kotlin): theme model, 57 built-in themes, JSON import/export; database v4 adds custom themes (3→4 migration)
- ✅ Built-in themes: Light, Dark, AMOLED black, Material You light/dark (wallpaper colours on Android 12+), Lavender, Mint, Sakura (gradient), Paper, Minimal, Professional, Midnight, Mocha, Frost, Ocean, Forest, Twilight (gradient, see-through keys), Aurora (gradient, see-through outlined keys), Sunset (gradient), Glass (see-through keys), Neon (glowing outlines), High contrast. A test checks every theme's label contrast is at least 3:1 on its keys, at both ends of a gradient
- ✅ "System" theme follows the phone's dark mode
- ✅ Custom theme editor with live preview (the real keyboard view): colours (swatches or hex), gradient, outline, corners, key spacing, key opacity, label size, bold labels, shadows, background picture (scaled ≤ 1080 px, centre-cropped) with a darkening overlay
- ✅ Theme gallery: apply, customise (copy) any theme, duplicate, delete, export (copy as text), import (paste)
- ✅ Theme and size changes apply to the open keyboard immediately (the service rebuilds its view)
- ✅ Height per orientation (70–140 %), number row (Latin or Bangla digits), label size
- ✅ Vibration: off / phone default / light / medium / strong (custom strengths only while the phone's touch vibration is on); a slightly different feel for Space/Enter/Backspace and swipe ticks
- ✅ Key sounds with volume; separate sounds for Space, Enter and Backspace; silent in silent/vibrate mode and Do Not Disturb
- ✅ One-handed mode (left/right), with on-keyboard buttons to switch side or return to full width
- ✅ Floating mode: a smaller card you drag by its handle; the app keeps its full size and touches outside the card reach the app (touchable region); the dock button returns to normal; position remembered
- ✅ Visual refresh (2026-11-06, after the user's feedback from the first device run that the keyboard looked poor and was hard to use):
  - One icon set, Material Symbols Rounded (the Material 3 icons, Apache 2.0 — `keyboard-ui/NOTICE.txt`, fetched by `tools/prepare-icons.pl`), replaces the mix of emoji and text symbols on Shift, Backspace, Enter (per action: return/go/search/send/next/previous/done), the emoji and language keys, the toolbar and the one-handed/floating controls. The settings app uses the same files
  - Shift shows off / on (filled) / caps lock (filled with bar, accent key)
  - Enter is always the accent-coloured key
  - Suggestions sit in up to three centred slots with thin dividers, and the word that Space will commit is bold (replacing the large blue pill); Avro candidates scroll the same way
  - The toolbar spreads its tools and settings evenly and highlights the open panel. While suggestions are showing, a grid button at the start brings the tools back, so emoji and clipboard stay one tap away
  - The duplicate language chip and the hide button are gone: the space bar and the system's own hide button do those jobs
  - Lighter corner hints, a short space-bar label ("English", "অভ্র", "জাতীয়")
  - Softer preview bubble and long-press menu
  - New default Light/Dark colours: flat dark keys without shadow, a Material blue accent, 8 dp corners, 12 dp for Material You
  - A new test checks that every theme's accent colour keeps its icon readable (≥ 3:1)
- ✅ Ridmik-style National layout and bottom row (2026-11-06, the user asked for "the national keyboard layout like this" with Ridmik Classic screenshots; tests in `RidmikLayoutTest`):
  - Letters: Z = ঁ (shift ঃ), X = ো (shift ৌ), shift-W = ্য, shift-A = ঋ, shift-G = র্ (reph); ্র is ্ + র or on র's long-press. This replaces the earlier BDS-table positions the user had confirmed from the HTML demo
  - Corner hints as Ridmik shows them (vowel signs show their full vowel: ু → উ, ি → ই, া → আ, ো → ও; ত → ৎ; consonants their shift letter); holding a key types its hint
  - ?১২৩ page: Bangla digits (English digit on long-press), `@ # ৳ % & * - + ( )`, `ALT ! " ' : ; / ?`; ALT page with `~ \` | ♪ ♥ π ÷ ♠ { }`, Tab, `£ ¢ ♦ ♣ ^ _ = [ ]`, র‍্য, `® © ™ \ < >`, „ and …
  - Bottom row `?123 · 😊 · , · space · . (জাতীয়: ।) · Enter` for every layout (password fields: no emoji key)
- ✅ Cursor control (2026-11-06, user feedback: no language swipe on space; Gboard cursor swipe; a real range for backspace; iPhone trackpad):
  - Space swipe moves the cursor a character at a time (Gboard). The short-lived "swipe space to change language" was removed; a language button at the right of the top bar (EN / অভ্র / জাতীয়) switches layouts, hold it for the system keyboard chooser. The bottom row has no 🌐 key, so the space bar is wider
  - Backspace swipe selects words before the cursor and follows the finger both ways (slide back right to select fewer; back to the start selects nothing); releasing deletes the range. `KeyAction.SelectBack`/`EndSelectBack`, tested in `RidmikLayoutTest`
  - Hold the space bar (0.4 s, without sliding) for trackpad mode, like iOS: the keys go blank, sideways movement moves the cursor one character per 8 dp, and up/down moves between lines (setting "Trackpad: move between lines", on by default). Not yet tried on the phone
- ✅ Predictive touch targets (2026-11-06, iOS-style; user request):
  - `NextCharPredictor` (suggestion-engine) predicts the next character from the word typed so far: English from the frequency-weighted word list plus learned and personal words; জাতীয় from the Bangla list (no frequencies) plus learned words; Avro by asking the Avro dictionary search how many words each next Roman letter still reaches, plus Roman spellings the user picked. Cached per prefix (128 entries), computed off the main thread in its own slot (newest wins), and dropped the moment the prefix changes, so a stale prediction is never used
  - `TouchResolver` (keyboard-core) picks the key with the best log P(touch | key) + log P(key): a Gaussian around each key centre (σ 0.32 key sizes) plus the smoothed prediction. Safeguards: the inner half of a key is always a direct hit; a key reaches at most 0.3 key sizes past its edge; only letter keys compete (Space, Shift, Backspace never change hands); one key per touch, so regions never overlap; keys look the same. ~0.6 µs per touch on the PC, no allocation
  - `TouchCalibration`: learns per-key tap offsets slowly from kept taps (3 %) and faster from corrections (15 %: letter, Delete within 3 s, then a neighbouring letter), limited to ±0.3 key sizes. Off in password/private fields; saved in a private preferences file excluded from backups; reset and off switches in Typing & gestures
  - Settings: "Predictive touch" (on), "Learn my touch" (on), "Reset learned touch"
  - Off on symbol pages, in password fields and number/phone pads
  - Measured with a typing simulation (`PredictiveTouchTest`: real dictionaries, touches scattered around the intended key with sd 0.27 key widths × 0.22 key heights). Letters typed correctly, plain → predictive: 600 common English words 93.1 % → 97.2 %; জাতীয় words 93.6 % → 97.1 %; Avro words 92.6 % → 93.9 %; names and technical terms 90.4 % → 91.9 %; random letter strings 92.5 % → 91.5 % (the expected small cost for text that isn't words, within the test's 2-point limit). Not yet measured on the phone with real fingers
- ✅ Settings app redesign (2026-11-06, user asked for "modern clean minimalistic iconic based design … proper material 3 icons"): one design system (`SettingsUi.kt`: large collapsing top bar, rounded groups, rows with tinted Material Symbol badges, switches/radios on whole rows); the long home page became a status card, a try-it field and grouped categories with live summaries, each opening its own screen (Languages, Typing & gestures, Suggestions, Clipboard & snippets, Toolbar, Privacy, Try the keyboard); every existing screen restyled; real back stack and slide transitions. Checked on the phone: the home screen renders (Material You colours)
- ✅ Motion and feel (2026-11-07, user request: smooth page transitions, a nicer settings app, more themes, a softer typing feel):
  - Settings pages push like iOS/Android settings: the new page glides in over the old one from the right edge (380 ms) while the old one drifts a quarter left and dims; back is the exact reverse. Reworked after the user said the first version (a small shared-axis shift) was still not smooth: a screen recording on the phone showed the page jumping halfway in on the first frame, because composing and measuring the new page takes 20–55 ms; the motion now starts 50 ms late so that frame passes before anything moves. Phone installs over adb also ran uncompiled (`status=verify`), which made it worse; `adb shell cmd package compile -m speed -f com.yeamin.lipi` after installing fixes that (Play installs get compiled profiles anyway). Android 13+ predictive back: the page shrinks, rounds its corners and follows the finger, and springs back if the gesture is cancelled (`enableOnBackInvokedCallback`)
  - Settings polish: each group has its own tint (Typing in primary, Look & feel in tertiary, Privacy in primary), rows and switches squash slightly under the finger with a spring, a gradient status card, and a "Quick themes" strip on the home page that applies a theme with one tap
  - 10 new themes (see the list above): soft pastels, warm and cool darks, two see-through gradient themes and Neon
  - Keys now sink (shrink 7 %), darken and lose their shadow over ~45 ms when touched and ease back over ~170 ms after release, instead of switching colour instantly; Enter darkens rather than losing its accent colour. The letter bubble pops up with a small spring overshoot, only nudges when rolling between keys, and fades out after release; the long-press row grows out of its key
  - Installed on the phone; the feel has not been judged by the user yet
- ✅ Fixes and soft themes (2026-11-07, user feedback):
  - Back returns to the same scroll position: each page on the stack keeps its saved state (`SaveableStateHolder` keyed by page); a page left by going back forgets it, so reopening starts at the top
  - Bottom space: on Android 17 the keyboard is told the short app navigation bar (63 px on the Pixel 7 Pro) although the system draws its hide ⌄ and switch 🌐 buttons in the taller keyboard bar (108 px), so the bottom row sat on them. The keys now always keep clear of the platform's `navigation_bar_frame_height` (40 dp if the platform doesn't say, capped at 56 dp), except when the navigation bar sits at the side in landscape. Trimmed after the user found it too tall: the keys may reach the top fifth of that bar (the buttons sit in its middle) with no extra gap; checked on the phone, the bottom row ends just above ⌄ and 🌐. A keyboard rebuilt by a theme change now starts with the last known bottom space and asks for fresh insets when attached (it looked cut off until the first key press)
  - 11 more themes (45 in all): Coral, Honey (gradient), Pistachio, Sand dune (gradient), Polar, Seafoam (gradient, see-through keys), Graphite (orange accent), Berry, Moss, Cosmic (gradient, see-through keys), Ember (gradient)
  - 12 colourful themes (57 in all): Tangerine, Lemonade (gradient), Bubblegum (gradient), Azure, Cotton candy (pink-to-blue gradient, see-through keys), Grape (gradient), Emerald, Sapphire (gradient), Ruby, Teal (orange accent), Lime, Mango tango (orange-to-pink gradient, see-through keys)
- ✅ Hold time setting (2026-11-07, user request: জাতীয় has many characters behind a hold): Typing & gestures → "Hold for more characters" sets how long a key is held before its corner letter or long-press menu appears, 150–800 ms in 25 ms steps (default 350), with Quick/Normal/Relaxed presets; saved when the slider is released, backed up with the other settings, tested in RidmikLayoutTest. Custom gesture long-presses keep their own duration
- ✅ Rounded top corners, settings at top left, voice language switching (2026-11-07, user requests):
  - The keyboard's top corners are rounded (18 dp; the app shows through, the bottom stays square). Checked on the phone
  - The settings gear is the first button in the top bar (top left); tools follow in the user's order, voice stays second from the right. Checked on the phone
  - Switching voice language several times quickly stopped dictation: the switch stopped and restarted the same recogniser at once, the speech service answered with a client error that was ignored as "our own cancel", leaving it stuck starting; busy / too-many-requests / disconnected errors ended dictation; and quick taps read the language before the previous tap was saved. Now each switch throws the recogniser away and starts a fresh one after 350 ms (quick taps collapse into one restart), callbacks from older sessions are ignored, those transient errors retry up to 4 times with a growing pause, and the service remembers the language just picked. Not tried with the microphone by Claude (it would record on the user's phone)
- ✅ Jolpai look for the settings app (2026-11-07, user request after the rebrand): always dark, with the app icon's olive greens (JolpaiColors.kt): olive-black surfaces, olive primary (switches, sliders, selection), sage secondary, cream-gold tertiary; replaces Material You wallpaper colours. Window, launch screen (Android 12+) and system bars match, so there is no white flash. Hold-time section redesigned (icon row, plain slider with Faster/Slower, three-part Quick/Normal/Relaxed selector). Installed; not yet seen on screen by Claude
  - Selected theme: a ring around the sketch with a gap in the page colour and a springy check badge, visible on light and dark themes alike (the old primary-coloured border vanished on light themes with a light Material You palette)
  - Key preview and long-press menu on see-through themes (Twilight, Aurora, Sunset, Glass): the bubble treated a translucent white key as opaque white, under white text. `ThemeColors.popup` now composites the key over the background and darkens or lightens until the text has ≥ 4.5:1 contrast; a test checks every theme and an awkward custom one
  - 12 more themes (34 in all), soft and low-glare with rounder keys: Cloud, Peach, Sky (gradient), Matcha, Vanilla, Rose gold (gradient), Lagoon (gradient), Sunrise (gradient, see-through keys), Fjord, Lilac dusk (gradient), Dusk rose, Deep sea (gradient). Theme sketches show gradients

Not done / limits:
- ⬜ Manual test on a phone (floating mode in several apps, Material You colours, vibration strengths)
- 🔍 Glide typing: not built. It needs a gesture decoder and a tuned language model to be reliable; still "investigate"
- Sound "profiles" beyond the system click sounds are not included (no bundled sound files)
- Key width is set by the layout; width per key isn't adjustable separately

## Phase 7 — Polish and release 🔨
Status 2026-11-06: built on the PC. **The whole app still needs testing on a real phone**
before any release.

Done:
- ✅ TalkBack: every key is a virtual accessibility node (`ExploreByTouchHelper`) with a spoken name (Shift state, Delete, Enter action, Bangla signs by name: হসন্ত, র-ফলা, এ-কার …); lift-to-type and double-tap both type
- ✅ Larger keys (height up to 140 %, label size up to 140 %) and a High contrast theme
- ✅ Landscape: separate height setting
- ✅ Privacy review: no logging calls anywhere; no analytics/crash SDKs; learning/clipboard/suggestion rules enforced in one place and unit-tested; database, clipboard pictures and stickers excluded from cloud backup and device transfer; written policy in [PRIVACY.md](PRIVACY.md)
- ✅ Backup & restore: a JSON file in a place the user picks (settings, personal dictionary, snippets, custom themes; learned words only if ticked; never clipboard, pictures or the GIPHY key); restore merges
- ✅ About screen: version, privacy summary, permissions, and every bundled data source with its NOTICE and licence text
- ✅ Release build: R8 shrinking and resource shrinking, optional signing from a git-ignored `keystore.properties`

Not done:
- ⬜ **Testing on real phones** (low-end, mid-range, flagship): typing feel, every feature in this file, crash-free use. This is the main remaining work
- ⬜ Latency and memory profiling on a phone (Macrobenchmark needs a device)
- 🔍 Tablet layouts and split keyboard
- ⬜ Instrumented (on-device) tests for the Room migrations and the IME service

## Later / investigate
- 🔍 Bangla glide typing
- 🔍 Handwriting (ML Kit Digital Ink)
- ⚠️ Translation (ML Kit on-device, opt-in model download)
- ⚠️ Grammar correction / rewriting / smart compose (optional model provider)
- 🔍 Settings sync across devices (would need an account — conflicts with "no account needed"; export/import first)

---

## Open questions for the user
1. ~~Avro rule table~~: approved and added 2026-11-06.
2. **Test phone:** model and Android version, so we know which API-level features can be tested.
3. **Licence** for the app's own code (affects nothing technically, since MPL Avro files stay MPL).
4. **GIF provider:** GIPHY or Klipy, and who supplies the API key.
