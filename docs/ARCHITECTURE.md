# Architecture — Bangla-first Android Keyboard (IME)

Status: **Phases 2–3 built and unit-tested; not yet tested on a phone.** See [ROADMAP.md](ROADMAP.md).
Last updated: 2026-11-06 (Phase 3 added `suggestion-engine` and `core-data`, see §6)

> **Changes made while building Phase 2** (the sections below are updated to match):
> - Toolchain: AGP **9.4.1** (the version bundled with Android Studio 2026.1.4) and Gradle **9.6.1**, so
>   compile/target SDK is **37**, not 36.
> - `LipiImeService` lives in `keyboard-ui`, not `keyboard-core`, because it creates the views.
>   `keyboard-core` holds the `KeyboardController` state machine, which has no view code.
> - Modules are created in the phase that first needs them. Phase 2 has `bangla-engine`,
>   `keyboard-core`, `keyboard-ui` and `app-shell`.

This document records the technical decisions for the keyboard, the reasons behind
them, and the Android platform limits that shape what can and cannot be built.
Feature status lives in [ROADMAP.md](ROADMAP.md).

---

## 1. Goals and non-goals

**Goals**
- A system-wide Android keyboard (an `InputMethodService`) that works in every app.
- First-class Bangla: Avro phonetic, the National (জাতীয়) layout, and English, switchable instantly.
- Gboard-class responsiveness: a key press reaches the app in well under one frame (16 ms).
- Private by default: everything on-device unless the user opts in to a network feature.

**Non-goals (for now)**
- iOS, desktop, or web versions.
- Cloud accounts. The keyboard must be fully usable without one.
- Features that cannot be made reliable on stock Android (see §9). They are listed as
  "investigate" in the roadmap instead of being faked.

---

## 2. Development environment (checked on this PC)

| Item | Found | Decision |
|---|---|---|
| Android Studio | Installed (build 261.x) with bundled JBR 25 | Use the bundled JDK. Compile with a Java 17 toolchain. |
| Android SDK platforms | 34, 35, 36, 37 | compileSdk/targetSdk **37** (builds cleanly with AGP 9.4.1) |
| Build tools | 35.0.0, 36.0.0 | 36.0.0 |
| Gradle | 9.3.1 and 9.6.1 cached | Gradle **9.6.1** wrapper |
| Android Gradle Plugin | 9.4.1 bundled with Studio | AGP **9.4.1** with built-in Kotlin (no `kotlin-android` plugin) |
| Annotation processing | — | KSP 2.3.x (works with AGP 9 built-in Kotlin) for Hilt and Room |
| Emulator | No system image, no virtual device; only ~1.2 GB RAM free | **Test on a real phone over USB.** No emulator in the dev loop. |
| Disk | C: ~10 GB free, W: ~34 GB free | Project lives on W:. Watch the size of the Gradle cache on C:. |

Note on SDK 37: the official AGP 9.0 notes cap compileSdk at API 36.1. Android Studio 2026.1.4
bundles AGP 9.4.1, which compiles against API 37 without warnings, so the project targets 37.
Nothing in this design depends on API 37.

**minSdk = 26 (Android 8.0).** Reasons:
- Many Bangla users in Bangladesh have older, low-end phones, so we want wide reach.
- Everything critical works on 26: `commitContent` (image insertion, API 25+), notification
  channels, adaptive icons, and Room/Hilt/Compose.
- Newer APIs are used only where they exist, behind version checks:
  on-device speech (31+), recognition-support checks and model download (33+),
  `ClipDescription.EXTRA_IS_SENSITIVE` (33+), Material You dynamic colour (31+).

---

## 3. Module structure

The brief's suggested modules are kept, with two changes:
1. The Bangla engine and suggestion engine are **pure Kotlin/JVM modules** (no Android
   dependency). They hold the hardest logic, so their unit tests must run fast on the PC
   without a device.
2. Shared data (Room database, DataStore) gets its own `core-data` module, so feature
   modules don't each create a database.

```
:app-shell             Application class, Hilt root, the settings app (Compose), manifest + IME declaration
:keyboard-core         KeyboardController state machine, EditorSession (InputConnection wrapper), input processors, layouts, settings
:keyboard-ui           LipiImeService (InputMethodService), KeyboardView (Canvas), key popups, toolbar, later the suggestion strip and panels
:bangla-engine         [JVM] Avro phonetic transliterator, National-layout composer, Bangla Unicode utils
:suggestion-engine     [JVM] dictionary trie, autocorrect, next-word model, candidate ranking
:clipboard-manager     clipboard capture, history, pins, folders, snippets, image storage
:voice-input           SpeechRecognizer wrapper, language/offline detection, dictation session
:emoji-engine          emoji data, search (CLDR keywords incl. Bangla), skin tones, recents; GIF/sticker providers
:theme-engine          ThemeSpec model, built-in themes, custom backgrounds, live theme Flow
:keyboard-settings     settings repositories (DataStore), shared settings UI components + live preview
:core-common           [JVM] Result types, dispatchers, logging (never logs typed text)
:core-data             Room database, DAOs, DataStore instances, migrations
```

Dependency rule: **arrows point down only.** `app-shell` → feature modules → `keyboard-core`
→ engines → `core-common`. The engines know nothing about Android, and feature modules do
not depend on one another. Anything they share goes through `keyboard-core` interfaces.

---

## 4. The typing path (performance-critical)

```
Touch event
  → KeyboardView (Canvas)            hit-test against precomputed key rects, multi-touch pointers
  → KeyAction                        Char("ক") / Shift / Delete / Enter / Switch(layout) / ...
  → InputProcessor (per layout)      EnglishProcessor | NationalProcessor | AvroProcessor
  → EditorSession                    batches edits on one InputConnection: commitText / setComposingText / deleteSurroundingText
  → (async) SuggestionEngine         runs on a background dispatcher, cancelled on every new key
  → SuggestionStrip                  updated via StateFlow
```

Rules that keep it fast:
- **The key grid is a custom `View` drawn on a `Canvas`, not Compose.** It gives the
  lowest per-press latency, full control over multi-touch, and a glide trail can be drawn
  in the same pass. Because it is a plain View, the settings app can embed the exact same
  view as its live preview. **Compose is used for panels** (clipboard, emoji, voice, editing
  tools) and for the whole settings app. Inside the IME window this needs the lifecycle,
  ViewModel-store and saved-state owners wired up by hand; `keyboard-ui` does that once.
- The key press commits text **synchronously on the main thread**. Suggestions, learning and
  database writes are always async and cancellable (`Job` per keystroke).
- No disk I/O and no object allocation in `onDraw` or the touch handler. Layouts and
  dictionaries are loaded once and cached.
- Haptic and sound feedback fires on touch *down*, before the text commit.

### EditorSession (InputConnection wrapper)
- Tracks cursor and selection via `onUpdateSelection` instead of asking the app every
  time (`getTextBeforeCursor` is slow on some apps, e.g. WebView).
- Wraps multi-step edits in `beginBatchEdit`/`endBatchEdit`.
- Classifies every field from `EditorInfo` (see §8): text, email, URL, number, phone,
  password, multiline, no-suggestions, incognito.
- Uses `imeOptions` to pick the Enter key label/action (Send, Search, Go, Next, Done, newline).

---

## 5. Bangla engine

### 5.1 National (জাতীয়) layout
- Key map follows Ridmik Keyboard's National layout, copied from the user's screenshots on
  2026-11-06 at their request (`NationalKeyMap`, tested in `RidmikLayoutTest`). It differs from
  the BDS 1738:2004 table (and from `jatiya-keyboard-demo.html`, confirmed earlier) in a few
  positions: Z = ঁ/ঃ, X = ো/ৌ, shift-W = ্য, shift-A = ঋ, shift-G = র্; ্র is ্ + র.
  Each key's corner hint (the full vowel for vowel signs) is typed by holding the key.
- It is **not** Bijoy. Text is in Unicode logical order: the consonant is typed first,
  then the kar.
- `NationalComposer` rules (unit-tested):
  - hasanta `্` + kar → independent vowel (্+া=আ, ্+ি=ই, ্+ী=ঈ, ্+ু=উ, ্+ূ=ঊ, ্+ৃ=ঋ, ্+ে=এ, ্+ৈ=ঐ, ্+ৗ=ঔ)
  - kar typed with no consonant before it → independent vowel (common mobile behaviour; configurable)
  - ে + া → ো, ে + ৗ → ৌ
  - reph `র্` (Shift+A) typed after a consonant cluster is moved in front of that cluster
  - ZWNJ/ZWJ handling for explicit hasanta and য-ফলা/র-ফলা forms
- The full key map is stored as data (a layout definition file), not code, so the
  long-press extras (ৎ ঃ ঁ ৳) can be adjusted without touching logic.

### 5.2 Avro phonetic
The brief asks for a real transliteration engine, not character replacement. Avro's
published algorithm works like this, and our engine follows it:
1. **Pattern table, longest match first.** The input is scanned left to right. At each
   position the longest matching Roman pattern is chosen (`bh`, `kkh`, `rri`, `ng`, …).
2. **Context rules on each pattern.** A pattern can have several outputs, chosen by
   conditions on the neighbouring text: is the previous/next part a vowel, a consonant,
   punctuation, or an exact string, with negation. This is what produces correct kar vs
   full-vowel output (`ami` → আমি: `a` at word start → আ, `i` after a consonant → ি)
   and conjuncts (`kSh` → ক্ষ).
3. **Case-sensitive keys.** For example `t` → ত, `T` → ট, `sh` → শ, `Sh` → ষ.
4. **Dictionary candidates.** The engine also turns the Roman input into a search
   pattern (e.g. `o` can match অ or nothing, `s` can match স/শ/ষ) and looks it up in a
   Bangla dictionary trie. This gives multiple candidates (`bhalobashi` → ভালোবাসি) on top of
   the rule-based output. The candidate the user picks for a Roman word is remembered.

The engine is data-driven: the pattern table is a resource file and the matcher is generic
code. That keeps the algorithm testable and the rules auditable.

**Licence:** the reference Avro implementations are MPL (Avro Keyboard and ibus-avro: MPL 1.1;
OpenBangla's `rupantor`: MPL 2.0). MPL is a *file-level* copyleft. If we port or reuse
their rule table, that file stays under MPL with its original notice, and the rest of
the app can use any licence. **Done (2026-11-06):** `AvroPhonetic.json` from OpenBangla's
rupantor (commit `92902e3`) is bundled unmodified in `bangla-engine/src/main/resources/avro/`
with its MPL-2.0 licence and a NOTICE. The matcher (`PhoneticEngine`) is our own code.
An About screen with third-party notices is still to come (Phase 7).

Compatibility details, both covered by tests:
- An `exact` suffix condition never matches when it would end exactly at the end of the input
  (`e < length`). The reference parsers behave this way and the rule table relies on it.
- The rule table writes ড় ঢ় য় as precomposed letters (U+09DC, U+09DD, U+09DF), so the National
  layout uses the same letters. Both Bangla layouts produce identical text.

Composing behaviour (built): while typing a Roman word, the converted Bangla is shown as
**composing text** (underlined). Space, Enter, a layout switch, or a tap on a toolbar
candidate commits it. Backspace edits the Roman buffer, not the Bangla output.
Mixed typing: the toolbar always offers the word exactly as typed, for English words.
If the cursor is moved away while composing, the shown text is kept and composing ends.

Test corpus: all 413 input/output pairs from rupantor's tests pass, plus the brief's examples.
Rules alone are literal: `bhalobashi` gives ভালবাশি, and `bhalObasi` gives ভালোবাসি. Getting
ভালোবাসি from `bhalobashi` needs dictionary candidates, which are planned for Phase 3.

---

## 6. Suggestion engine

Built in Phase 3 (modules `suggestion-engine`, pure Kotlin, and `core-data`, Room).

| Part | How it works |
|---|---|
| Word lists | `SortedWords`: all words of a language packed into one `CharArray` plus an offset array, sorted. Prefix lookups are binary searches. About 2 bytes per character, so each language is a few MB. Loaded once in the background by `SuggestionResources`. |
| English data | AOSP LatinIME `en_US` list (Apache-2.0): 160k words with 0–255 frequencies; offensive words flagged and never suggested unless the setting is off. |
| Completion | Words in the prefix range, top 3 by frequency; the user's own words rank above dictionary words. |
| Autocorrect | `EnglishDictionary.corrections`: a weighted Damerau–Levenshtein search (neighbouring key 0.6, swap 0.7, apostrophe 0.3, doubled letter 0.6). It only checks words starting with the typed first letter, a neighbour of it followed by the typed second letter, or the first two letters swapped. Rows of the edit table are shared between neighbouring sorted words, and a whole block of words is skipped once a prefix can't match. Applied on space, Enter or punctuation only when the typed word is unknown, the cost is small for its length, and the fix is reasonably common. Backspace right after undoes it. |
| Bangla data | upodesh (MIT) word list and Avro patterns; riti (MPL-2.0) suffixes and Roman autocorrections. |
| Avro candidates | `AvroDictionarySearch` ports upodesh's search: the Roman word is split into patterns, each with several Bangla spellings, and only spellings that are prefixes of real words survive. `AvroSuggester` ports riti's ranking: autocorrect entry first, then dictionary words by edit distance to the rule output, then suffixed words, then the rule output, then the Roman input. Verified against both projects' own test expectations. |
| জাতীয় | Prefix completion on the Bangla list; the user's words first, then shorter words (the list has no frequencies). |
| Next word | Word pairs the user typed (`UserHistory` bigrams). No built-in model yet. |
| Learning | `UserHistory` keeps counts in memory for instant lookups and writes changes to Room through a queue on a background thread (`RoomHistoryStore`). Capped at 20k words per language. |
| Personal dictionary | Room table. Words are always suggested and never autocorrected. Added from the strip or in the app. |
| Privacy | No learning in password, no-learning (incognito) or no-suggestion fields; no suggestions in password, email or no-suggestion fields. `lipi.db` is excluded from cloud backup and device transfer. |
| Threading | English and জাতীয় suggestions are computed on `Dispatchers.Default`, and only the newest result is shown. Avro candidates are computed inline (~0.5 ms) because they decide the composing text. |

Grammar correction, sentence rewriting and "smart compose" need a language model.
They are designed as an **optional provider interface** (on-device model or a
user-configured API key), off by default, and never block typing. They are not
promised as built-in.

---

## 7. Features and how they work on Android

### 7.1 Clipboard
- Since Android 10, apps can read the clipboard only in the foreground, **except the
  current default IME**. While our keyboard is the selected input method, it can register
  `ClipboardManager.OnPrimaryClipChangedListener` and record copies made in any app.
  If the user switches to another keyboard, capture stops. The UI says so.
- Android 12+ may show a "pasted from clipboard" notice when the clipboard is read. We read
  only on change events, never by polling, and will check the behaviour on the test phone.
- **Sensitive clips are not stored:** clips marked `EXTRA_IS_SENSITIVE` (Android 13+),
  clips copied while a password field is focused, and anything copied from our own
  password-type fields.
- **Images:** a content URI from another app is only readable for a short time. When an
  image is captured, its bytes are copied (with a size cap) into app-private storage.
  It is then offered back through our own `FileProvider` and inserted with
  `InputConnectionCompat.commitContent` **only if the target field declares a matching
  MIME type** in `EditorInfo.contentMimeTypes`. Otherwise the UI explains that the app
  doesn't accept images.
- History, pins, folders and snippets are Room tables (`clip`, `snippet`, database v2).
  Retention is enforced when a copy is saved, when the keyboard starts, and when the setting
  changes. This is enough to keep the list current, so no WorkManager background job is needed.
- **Built (Phase 4):** `clipboard-manager` module (`ClipboardRepository`, `ClipboardWatcher`,
  Compose `ClipboardPanel`). The keyboard service is the `LifecycleOwner` /
  `SavedStateRegistryOwner` / `ViewModelStoreOwner` for Compose panels in the IME window.

### 7.1b Editing tools and gestures (built in Phase 4)
- Editing commands go through `TextEditor.run`: select all/cut/copy/paste use
  `performContextMenuAction`, cursor keys are DPAD key events (with Shift in select mode),
  and undo/redo are Ctrl+Z / Ctrl+Shift+Z key events, which only some apps honour.
- Word selection and deletion are computed by the keyboard (`TextUnits`) from the text around
  the cursor and applied with `setSelection` / `deleteSurroundingText`.
- Space-bar swipe moves the cursor one character per ~11 dp. A backspace swipe selects one
  more word per ~30 dp and deletes the selection on release. With gestures on, backspace
  deletes on release (or on hold-repeat) rather than on press, so a swipe never removes a
  stray character first.

### 7.1c Predictive touch targets
- `NextCharPredictor` (suggestion-engine) gives next-character probabilities for the word typed so far (English: frequency-weighted list + learned words; জাতীয়: Bangla list + learned words; Avro: how many dictionary words each next Roman letter can still reach via the Avro search). The controller asks for it after each key on its own background slot ([BackgroundWork]), caches per prefix and publishes `KeyboardUiState.nextChars`; it is cleared when the prefix changes so a late result is never used for the wrong prefix.
- `TouchResolver` (keyboard-core, pure Kotlin) runs on the main thread at touch-down: argmax over nearby letter keys of a Gaussian touch likelihood plus the log prediction, with a direct-hit core, a 0.3-key reach limit and letter-keys-only competition. `TouchCalibration` shifts each key's centre by the user's learned offset (kept in `shared_prefs/touch.xml`, excluded from backup).
- Accuracy is measured by `PredictiveTouchTest` (simulated thumb error over real dictionaries); see ROADMAP Phase 6 for the numbers.

### 7.2 Voice typing
- Uses Android `SpeechRecognizer`. The IME itself can't show a permission dialog, so the
  settings app asks for `RECORD_AUDIO` first, and the mic button links there if it hasn't been granted.
- Bangla (`bn-BD`, `bn-IN`) works **online** through the device's recognition service
  (normally Google's).
- **Offline Bangla is not assumed.** On Android 13+ we call `checkRecognitionSupport` for
  the chosen language and show the real status (offline installed / downloadable /
  online only / unsupported). `triggerModelDownload` is offered when available.
  On older Android versions the status shows "online only".
- Continuous dictation restarts the recognizer after each result, because Android stops
  it after silence. Pause/resume, "delete last word" and spoken punctuation ("দাঁড়ি",
  "comma", …) are handled by our own post-processing.
- Mixed Bangla–English speech depends on the provider and is not promised.
- **Built (Phase 5):** `voice-input` module (`VoiceTyping`, `VoiceCommands`) and the Compose
  `VoicePanel`. Partial results are shown as composing text and final results are committed with
  a trailing space. On Android 13+ the request asks for automatic formatting
  (`EXTRA_ENABLE_FORMATTING`). On versions before 13 the status reads "can't be checked"
  rather than guessing. `RECORD_AUDIO` is requested in the app's Voice screen.

### 7.3 Emoji, GIF, stickers
- Emoji data is generated from Unicode `emoji-test.txt`, and search keywords from CLDR
  annotations, **including Bangla keywords** (both under the Unicode licence). Emoji the
  device font can't draw are hidden (`PaintCompat.hasGlyph`, cached).
  **Built (Phase 5):** `emoji-engine` (pure Kotlin `EmojiData`, `Kaomoji`) and the Compose
  `EmojiPanel`. `androidx.emoji2` (bundled newer emoji font) is not used yet; it would add
  several MB.
- **GIF: the Tenor API was shut down on 2026-06-30** (new keys were blocked from January 2026).
  GIF search is a `GifProvider` interface; **GIPHY** is implemented (`media` module), using
  the user's own key entered in the app. Klipy can be added the same way. GIF search is off
  until a key is set, and the panel says why.
- Stickers: user-imported images (`StickerStore`, app-private files). GIFs are cached in
  the app's cache folder. Both are shared through the same `FileProvider` as clipboard images
  and inserted with `commitContent` under the same MIME rules.
- Kaomoji/emoticons: a bundled list, no network.
- **In-keyboard search mode** (`KeyAction.StartSearch`): the controller swaps its target
  `TextEditor` from the app to an `InMemoryEditor`, so every layout, Avro conversion included,
  types into the panel's search box. Suggestions and learning are off while searching.

### 7.4 Themes
- **Built (Phase 6):** `KeyboardTheme` in `theme-engine` holds the colours, gradient, key
  shape, spacing, opacity, label size and weight, shadows, outline, background image and
  overlay. Custom themes are stored in Room as their JSON export (`custom_theme`), and the
  active theme id is in DataStore.
- `KeyboardPalette` resolves a theme for drawing. Material You fills in
  `system_accent*`/`system_neutral*` colours on Android 12+; below that it uses the theme's
  own colours.
- When the theme, height or mode changes, the service rebuilds its input view
  (`setInputView`). This is simpler and safer than restyling every view in place, and it
  happens only on a settings change, never while typing.
- Custom backgrounds: the picture is scaled once to at most 1080 px wide and saved privately.
  It is decoded once per keyboard view and drawn centre-cropped (`CenterCropDrawable`), with a
  translucent black overlay.

### 7.5 Size, position, modes
- Height per orientation, key spacing, padding, number row and function row are layout
  parameters, recomputed only when settings change.
- One-handed (left/right) mode: the keyboard width is reduced and anchored to one side.
- Floating mode: the IME window becomes transparent, and `onComputeInsets` limits the
  touchable region to the floating panel so the app below stays usable. This is feasible
  but app-dependent, so it gets dedicated testing in Phase 6.
- **Built (Phase 6):** `KeyboardContainer.LayoutOptions` covers the height scale (separate for
  portrait and landscape), one-handed side and floating position. In floating mode the
  input view is the card plus 35 % of the screen height of travel. The card is placed by
  margins, so moving it re-runs layout and with it `onComputeInsets`.
  `contentTopInsets`/`visibleTopInsets` are set to the window height so the app isn't resized,
  and the touchable region is the card's bounds.
- Haptics and sounds: `KeyFeedback` (see the Sound & vibration screen). Custom vibration uses
  `VibrationEffect` only while the system touch-feedback setting is on. Sounds use
  `AudioManager.playSoundEffect` and only play in normal ringer mode with Do Not Disturb off.

### 7.6 Translation (optional)
A provider interface. The planned default is on-device ML Kit Translation, which lists
Bengali among its supported languages; this will be confirmed when it's built. Language
models are downloaded only after the user opts in. A configurable cloud provider is
possible later with the user's own key. Translations are always previewed before
insertion.

---

## 8. Privacy and security model

A field is **sensitive** when any of the following is true:
- the input type is a password variant: `TEXT_PASSWORD`, `TEXT_VISIBLE_PASSWORD`,
  `TEXT_WEB_PASSWORD`, or `NUMBER_PASSWORD`
- `IME_FLAG_NO_PERSONALIZED_LEARNING` is set (incognito tabs, banking apps)
- the field has `TYPE_TEXT_FLAG_NO_SUGGESTIONS` (suggestions only, learning still blocked)

In a sensitive field: no suggestions, no learning, no clipboard capture of what's typed,
no voice, and no toolbar network features. A small lock icon is shown in the strip.

Other rules:
- **No analytics SDK and no crash-reporting SDK.** Crashes are logged locally, with no
  typed text and no clipboard content in log messages. A lint check bans logging
  `CharSequence` values from the input path.
- The keyboard needs the `INTERNET` permission only for opt-in features (GIF, translation
  model download). The core typing path never touches the network.
- Settings and learned data stay on the device. Backup/restore is an explicit
  export/import file chosen by the user. Android Auto Backup excludes the learned
  dictionary and clipboard by default.
- Microphone: requested only from the settings app, with a screen explaining why.

---

## 9. Platform limitations (said up front, not hidden)

| Area | Limit | What we do |
|---|---|---|
| Clipboard | Works only while this keyboard is the selected default IME | Show capture status; no fake "background monitoring" |
| Clipboard | Other apps' image URIs expire | Copy bytes into private storage on capture |
| Image paste | The target app must declare accepted MIME types | Check `contentMimeTypes`; explain when not supported |
| Undo/redo | `InputConnection` has no undo API | Send Ctrl+Z / Ctrl+Shift+Z key events, which many apps (not all) support; label the button as "where supported" |
| Offline voice | Depends on device and recognition service; Bangla offline is not guaranteed | Runtime check; show the real status |
| GIF | No free official Tenor API after 2026-06-30 | GIPHY/Klipy adapters with a user-supplied key |
| Glide typing | Needs a large decoding model; Bangla glide has no standard | English glide first (Phase 6), Bangla glide marked "investigate" |
| Handwriting | Needs an ML model (e.g. ML Kit Digital Ink) | Investigate only |
| Cursor/selection | Some apps (e.g. games, terminals) report selection incorrectly | Fall back to key events for arrows/delete |
| Emoji | Older fonts lack new emoji | Hide what the device can't render |

---

## 10. Testing strategy

| Level | What | Where |
|---|---|---|
| Unit (JVM) | Avro rules + corpus, National composer, trie, autocorrect, ranking, sensitive-field classification | `bangla-engine`, `suggestion-engine`, `keyboard-core` tests; run on the PC |
| Room/DataStore | DAOs, migrations, retention job | Instrumented tests on the phone |
| IME integration | A test Activity with EditText fields of every input type; type via the real service | Instrumented tests on the phone |
| Manual matrix | WhatsApp, Messenger, Chrome, Gmail, Facebook, a banking app (password field), Google Docs | Checklist in ROADMAP per phase |
| Performance | Key-press-to-commit latency, memory with dictionaries loaded | Macrobenchmark + Android Studio profiler on the phone |

Every phase ends with a green build, passing tests, and the manual checklist done on the phone.

---

## 11. Toolchain decisions

| Choice | Version / note |
|---|---|
| Gradle | 9.6.1 |
| AGP | 9.4.1 with built-in Kotlin; Kotlin 2.3.20; KSP 2.3.13; Hilt 2.60.1 |
| DI | Hilt (via KSP). `InputMethodService` is a `Service`, so `@AndroidEntryPoint` works. |
| DB | Room (KSP) |
| Prefs | DataStore (Preferences for flags, Proto for structured settings) |
| UI | Compose + Material 3 (settings, panels); custom View (key grid) |
| Async | Coroutines + Flow; `Dispatchers.Default` for engines, `IO` for storage |
| Background | WorkManager only for clipboard retention and dictionary maintenance |
| Version catalogue | `gradle/libs.versions.toml`; exact versions pinned when the project is created in Phase 2 |

---

## Sources

- Android `SpeechRecognizer` reference: https://developer.android.com/reference/android/speech/SpeechRecognizer
- AGP 9.0 release notes (built-in Kotlin, API 36.1 ceiling): https://developer.android.com/build/releases/agp-9-0-0-release-notes
- Tenor API shutdown (2026-06-30): https://9to5google.com/2026/06/30/google-tenor-api-gif-updates/ , https://www.shacknews.com/article/149852/google-tenor-api-service-discontinued-twitter-discord-whatsapp-bluesky
- Avro licensing: https://linux.omicronlab.com/ (ibus-avro, MPL 1.1), https://github.com/OpenBangla/rupantor-rs (MPL 2.0)
