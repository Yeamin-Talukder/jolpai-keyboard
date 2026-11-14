<div align="center">

<img src="docs/images/banner.png" alt="Jolpai Keyboard (জলপাই কিবোর্ড): free AI-powered Bangla keyboard for Android with a clean design, Avro phonetic, National (Jatiya) and Probhat layouts" width="100%">

# Jolpai Keyboard (জলপাই কিবোর্ড): AI-Powered Bangla Keyboard for Android

**A free, open-source, AI-powered Bangla (Bengali) keyboard for Android with a clean, modern design: Avro phonetic typing,
the National (জাতীয়) and প্রভাত (Probhat) layouts, English QWERTY, on-device AI predictions, an offline spelling and grammar
check, offline English↔Bangla translation, voice typing, a clipboard manager and 64 themes. No ads, no tracking,
and nothing you type leaves your phone.**

[![Downloads](https://img.shields.io/github/downloads/Yeamin-Talukder/jolpai-keyboard/total?label=downloads&color=6E8A2C&logo=android&logoColor=white)](https://github.com/Yeamin-Talukder/jolpai-keyboard/releases)
[![Views](https://hits.sh/github.com/Yeamin-Talukder/jolpai-keyboard.svg?label=views&color=6E8A2C)](https://hits.sh/github.com/Yeamin-Talukder/jolpai-keyboard/)
[![Stars](https://img.shields.io/github/stars/Yeamin-Talukder/jolpai-keyboard?style=flat&color=B5C94A&logo=github)](https://github.com/Yeamin-Talukder/jolpai-keyboard/stargazers)
[![Version](https://img.shields.io/badge/version-0.11.0--beta-B5C94A)](docs/ROADMAP.md)
[![License: Apache-2.0](https://img.shields.io/github/license/Yeamin-Talukder/jolpai-keyboard?color=4E6120)](LICENSE)
<br>
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Tests](https://img.shields.io/badge/unit%20tests-270%2B-6E8A2C)](#testing)
[![Build](https://img.shields.io/github/actions/workflow/status/Yeamin-Talukder/jolpai-keyboard/ci.yml?branch=main&label=build&logo=githubactions&logoColor=white)](https://github.com/Yeamin-Talukder/jolpai-keyboard/actions/workflows/ci.yml)
[![Last commit](https://img.shields.io/github/last-commit/Yeamin-Talukder/jolpai-keyboard?color=4E6120)](https://github.com/Yeamin-Talukder/jolpai-keyboard/commits/main)
[![Made in Bangladesh](https://img.shields.io/badge/Made%20in-Bangladesh-006A4E)](#)

**[🌐 Website](https://yeamin-talukder.github.io/jolpai-keyboard/)** · [What's new](#-whats-new-in-0101) · [Demo](#-see-it-in-action) · [Features](#-features) · [Screenshots](#-screenshots) · [Download](#-download--install) · [Architecture](#-architecture) · [Build](#-build-from-source) · [FAQ](#-faq) · [বাংলা](#-বাংলায়-জলপাই-কিবোর্ড)

</div>

---

## What is Jolpai Keyboard?

**Jolpai Keyboard** (Bangla: **জলপাই কিবোর্ড**, "olive keyboard") is an open-source, AI-powered Bangla keyboard
app (IME) for Android, built for people who write in **Bangla (Bengali) and English**. You can type Bangla **phonetically
with Avro** (`ami` → আমি), use the fixed **National (জাতীয়)** or **প্রভাত (Probhat)** layout, or switch to **English QWERTY**
with on-device AI predictions,
all in one keyboard that works in every app: WhatsApp, Messenger, Facebook, Gmail, Chrome and more.

It aims for Gboard-level speed and features while keeping your data on your device:
**no account, no ads, no analytics, and no cloud.** Suggestions, learning, clipboard history and even
English↔Bangla translation run **offline**.

> **Also known as:** Jolpai Bangla Keyboard · Jalpai Keyboard · জলপাই বাংলা কিবোর্ড · Jolpai Avro Keyboard
> (package `com.yeamin.jolpai`; the project was first called *Lipi Keyboard*, package `com.yeamin.lipi` up to v0.8.0).

## 🆕 What's new in 0.11.0

- 🧠 **On-device AI for English**: a small neural model (4.7 MB, on your phone only) reads your sentence to suggest the next word, rank suggestions and autocorrect ("i want to takl" → talk), pick emoji by meaning (good night → 😴) and catch their/there in Check. Next word in the top 3: **0 % → 35 %** on held-out sentences; wrong autocorrections **12.5 % → 5.5 %**.
- 🇧🇩 **Bangla next words from the first word**, ranked from 91 million words of open Bangla text (কেমন → আছেন, আমি তোমাকে → ভালোবাসি).
- 📈 **Learns your phrases** (one and two words back); old habits fade so new ones take over.
- ⌨️ **প্রভাত (Probhat) layout**, and **clean vowel-sign keys** (no dotted circle).
- 🎉 Festival themes, snippet packs (everyday, Islamic greetings), date and Bangla calendar snippets, Unicode text styles.

Earlier, in 0.10.x: update right inside the app (About → **Update**, checked before it installs). In 0.9.0: Check offers the fix plus 2 other likely words, and the new app ID **`com.yeamin.jolpai`** (coming from 0.7.0 or 0.8.0, [move your data once](#-download--install)).

[Full release notes](docs/release-notes/v0.11.0.md) · [All releases](https://github.com/Yeamin-Talukder/jolpai-keyboard/releases)

## ✨ Features

### ⌨️ Bangla and English typing
| | |
|---|---|
| **Avro Phonetic (অভ্র)** | Type how a word sounds in English letters: `ami banglay likhi` → আমি বাংলায় লিখি. Dictionary candidates, remembers your picks, reph with `rr` (`korrm` → কর্ম). Checked against a 413-case Avro test corpus. |
| **National (জাতীয়) layout** | The Bangladesh national standard layout, arranged like Ridmik Keyboard. Hold a key for the letter in its corner (ু → উ, ত → ৎ); reph is Shift + ্. |
| **Probhat (প্রভাত) layout** | The fixed Bangla layout used on Linux computers, laid out 12 · 9 · 9 as on popular Bangla phone keyboards. The corner shows the shifted letter; hold for it, or for ঞ ৎ ্র র্. Turn it on in Languages & layouts. |
| **English QWERTY** | Autocorrect with undo on Backspace, word completion, accents on long-press. |
| **One-tap switching** | Switch English ↔ বাংলা (অভ্র) ↔ বাংলা (জাতীয়) ↔ বাংলা (প্রভাত) from the top bar; choose which layouts are on. |
| **Bangla digits & punctuation** | Hold a digit for ০–৯; two spaces type `।` in Bangla and `. ` in English. |

### 🧠 Smart suggestions (on-device)
- Word completion and autocorrect for **English and Bangla**. English autocorrect understands phone slips (a neighbouring key, swapped letters) and spelling-by-sound mistakes (doubled letters, vowels, c/k/s), and only corrects when one fix is clearly best; otherwise it offers the choices.
- **Next-word prediction** from a small **on-device neural model** that reads your whole sentence (English), mixed with what you usually type next. It also ranks completions and autocorrect by the sentence: "i want to takl" → talk.
- **Bangla next words from day one**: a ranked list counted from 91 million words of open Bangla text (কেমন → আছেন, আমি তোমাকে → ভালোবাসি), looking at the last one or two words.
- **Learns your phrases**: which word you type after one word and after two words, and old habits fade (half the weight every 30 days unused), so new ones take over.
- **Personal dictionary**: add words so they are never "corrected".
- **Emoji suggestions** for the word you type, in English and Bangla. In English the model also picks emoji by meaning ("so tired" → 😴, "my dog is sick" → 🐶).
- **Predictive touch**: likely next keys get a slightly larger tap area (invisible), and it learns where you tap.
- **Bangla word frequencies**: common Bangla words come first in completions, Avro candidates and spelling fixes (মানষ → মানুষ).
- **✨ AI proofreading** on phones with Google's on-device AI (Gemini Nano): an extra English check in the Check card, done on the phone.

### ✓ Spelling and grammar check (offline)
- **Check** in the top bar lists what may be wrong in the text you're writing: **spelling** (English and Bangla, including Bangla word endings and look-alike letters such as ি/ী, শ/ষ/স), **punctuation** (spaces around marks, দাঁড়ি in Bangla, a missing full stop) and common **English grammar** (a/an, "i" → "I", "could of", "better then", "he don't" …). With the on-device model it also catches **wrong word choices** ("their going" → they're, then/than, its/it's, your/you're) and ranks spelling fixes by the sentence.
- Fix one, **Fix all**, ignore, or add a word to your dictionary. Names, links, numbers and e-mail addresses are left alone.

### 🎨 Make it yours
- **Key fonts**: Hind Siliguri, Tiro Bangla, Galada or Atma on the keys.
- **Sound packs**: Soft tap, Typewriter, Bubble or Wooden key sounds, made on the phone.
- **Split keyboard** for landscape or tablets.
- **Typing stats**: words per day, typing speed (WPM, best, accuracy), English vs বাংলা, autocorrections and letters saved (counts only, never words).
- **Inline calculator**: type `250*4` and tap `= 1000`.

### 🌐 Offline translation, voice and more
- **English ↔ Bangla translation inside the keyboard.** Download a ~30 MB pack once; after that it works with **no internet**. Spelling fixes for what you type show as chips under the box.
- **Voice typing** in Bangla or English, with spoken punctuation ("comma", "দাঁড়ি", "নতুন লাইন"), a voice bar, and a floating mic pill.
- **Emoji, kaomoji, stickers and GIFs**: emoji search in English *and* Bangla, skin tones, recents, favourites, your own stickers, and GIF search with your own GIPHY key.

### 📋 Clipboard and productivity
- **Clipboard history** (text and images), with pins, folders and **automatic categories** (links, emails, OTP codes, phone numbers, addresses, code).
- **Snippets / text expansion**: type `@addr` + space to insert your address. Snippets can hold `{date}`, `{bangla_date}`, `{day}`, `{time}`, `{clipboard}` and `{cursor}`; ready-made packs of everyday messages and Islamic greetings (`@salam` → আসসালামু আলাইকুম).
- **Editing panel**: select, cut/copy/paste, arrow keys, word delete, undo/redo, date & time, and **বাংলা তারিখ** (today's Bangla calendar date, e.g. ২৩ কার্তিক ১৪৩৩).
- **Text styles**: 𝐁𝐨𝐥𝐝, 𝐼𝑡𝑎𝑙𝑖𝑐, 𝓢𝓬𝓻𝓲𝓹𝓽, 𝔊𝔬𝔱𝔥𝔦𝔠, 𝕆𝕦𝕥𝕝𝕚𝕟𝕖, 𝙼𝚘𝚗𝚘, Ⓒⓘⓡⓒⓛⓔ, Sᴍᴀʟʟ ᴄᴀᴘs, S̶t̶r̶i̶k̶e̶ and more for Facebook, WhatsApp and bios: restyle a selection, or type in a style.
- **Space-bar trackpad** (iPhone-style) to move the cursor, plus **Backspace swipe** to delete whole words, and optional **hold-Enter gestures** (emoji, or slide to select text).
- **Custom gestures**: assign swipes, holds and double-taps to actions, or to insert your own text.
- **Smart field handling**: calculator number pad, phone dial pad, and Send/Search/Go Enter keys.

### 🎨 Themes and customisation
- **64 built-in themes**: Material You (default; follows your wallpaper, light and dark), soft pastels, jewel tones, gradients, AMOLED black, High contrast, and soft dark themes like *Olive night*, *Misty blue* and *Moonlight*.
- **Theme editor**: your own colours, gradients or a background photo. Share themes as text.
- **Festival themes**: পহেলা বৈশাখ, একুশে ফেব্রুয়ারি, বিজয় দিবস, রমজান and ঈদ themes, used automatically on those days (you can turn it off).
- Keyboard **height** (portrait/landscape), **number row**, **one-handed** mode, **floating** keyboard.
- Key **sound and vibration** strength, long-press delay, and soft key-press animations.

### 🔒 Privacy and security
- **Everything stays on your phone**: no account, analytics, crash SDK or ads.
- Learned words and clipboard live in an **encrypted database** (SQLCipher, key in the Android Keystore), excluded from cloud backups.
- **Password and incognito fields**: no suggestions, no learning, no clipboard capture (🔒 shown).
- Internet is used **only** for features you turn on (GIF search, downloading the translation pack, checking for updates).
- Backup and restore to a file **you** choose. Read the [privacy policy](docs/PRIVACY.md).

## 🎬 See it in action

<div align="center">

<table>
<tr>
<td align="center" width="33%"><img src="docs/images/demo/demo-english.webp" width="240" alt="Jolpai Keyboard English typing on Android: word suggestions, next-word prediction and auto-capitals"><br><b>English typing</b></td>
<td align="center" width="33%"><img src="docs/images/demo/demo-avro.webp" width="240" alt="Avro phonetic Bangla typing in Jolpai Keyboard: ami becomes আমি with dictionary candidates"><br><b>অভ্র phonetic</b></td>
<td align="center" width="33%"><img src="docs/images/demo/demo-national.webp" width="240" alt="National (Jatiya) Bangla keyboard layout in Jolpai: every letter one tap away"><br><b>জাতীয় layout</b></td>
</tr>
<tr>
<td align="center"><img src="docs/images/demo/demo-spell-check.webp" width="240" alt="Offline spelling and grammar check for Bangla and English in Jolpai Keyboard"><br><b>Spelling &amp; grammar check</b></td>
<td align="center"><img src="docs/images/demo/demo-translator.webp" width="240" alt="Offline English to Bangla translator inside the Jolpai keyboard"><br><b>Offline translator</b></td>
<td align="center"><img src="docs/images/demo/demo-voice.webp" width="240" alt="Bangla and English voice typing with spoken punctuation in Jolpai Keyboard"><br><b>Voice typing</b></td>
</tr>
<tr>
<td align="center"><img src="docs/images/demo/demo-autocorrect.webp" width="240" alt="Smart autocorrect in Jolpai Keyboard fixes typos on space, backspace undoes it"><br><b>Smart autocorrect</b></td>
<td align="center"><img src="docs/images/demo/demo-predictive-touch.webp" width="240" alt="Predictive touch: likely next letters get a bigger tap target in Jolpai Keyboard"><br><b>Predictive touch</b></td>
<td align="center"><img src="docs/images/demo/demo-language-switch.webp" width="240" alt="One-tap switch between English, Avro Bangla and National Bangla in Jolpai Keyboard"><br><b>One-tap language switch</b></td>
</tr>
<tr>
<td align="center"><img src="docs/images/demo/demo-emoji.webp" width="240" alt="Emoji search in English and Bangla with skin tones in Jolpai Keyboard"><br><b>Emoji search (EN + বাংলা)</b></td>
<td align="center"><img src="docs/images/demo/demo-clipboard.webp" width="240" alt="Clipboard history with codes, links and notes in Jolpai Keyboard"><br><b>Clipboard</b></td>
<td align="center"><img src="docs/images/demo/demo-gestures.webp" width="240" alt="Gestures in Jolpai Keyboard: slide on the space bar to move the cursor"><br><b>Gestures</b></td>
</tr>
</table>

*Recorded from the live demo on the [website](https://yeamin-talukder.github.io/jolpai-keyboard/), which uses the app's real layouts, icons and the Olive night theme.*

</div>

## 📸 Screenshots

<div align="center">

<table>
<tr>
<td align="center" width="25%"><img src="docs/images/screenshots/screenshot-english.webp" width="200" alt="Jolpai Keyboard on Android: English typing with auto-capitals, word suggestions and an emoji suggestion"><br><sub><b>English</b> with suggestions</sub></td>
<td align="center" width="25%"><img src="docs/images/screenshots/screenshot-national.webp" width="200" alt="Jolpai Keyboard National (Jatiya) Bangla layout on Android typing জলপাই কিবোর্ড"><br><sub><b>জাতীয়</b> (National) layout</sub></td>
<td align="center" width="25%"><img src="docs/images/screenshots/screenshot-check.webp" width="200" alt="Jolpai Keyboard spelling check card: takl corrected to talk, with tail and tall as other choices"><br><sub><b>✓ Check</b>: takl → talk · tail · tall</sub></td>
<td align="center" width="25%"><img src="docs/images/screenshots/screenshot-translate.webp" width="200" alt="Jolpai Keyboard offline translator: good morning, how are you translated to শুভ সকাল, তুমি কেমন আছো"><br><sub><b>Offline translator</b> EN → বাংলা</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/images/screenshots/screenshot-emoji.webp" width="200" alt="Jolpai Keyboard emoji panel with search in English and Bangla"><br><sub><b>Emoji</b>, search in EN + বাংলা</sub></td>
<td align="center"><img src="docs/images/screenshots/screenshot-home.webp" width="200" alt="Jolpai Keyboard app home screen with setup card and test field"><br><sub><b>App home</b> and test field</sub></td>
<td align="center"><img src="docs/images/screenshots/screenshot-themes.webp" width="200" alt="Jolpai Keyboard themes screen with live keyboard preview in the Olive night theme"><br><sub><b>Themes</b> with live preview</sub></td>
<td align="center"><img src="docs/images/screenshots/screenshot-about.webp" width="200" alt="Jolpai Keyboard About page with in-app update check, what's new, report a problem and share"><br><sub><b>About</b>: in-app updates</sub></td>
</tr>
</table>

*Real screenshots from Jolpai 0.10.1 on a Pixel 7 Pro (Android 17), Olive night theme.*

### Layouts and themes

<img src="docs/images/layouts.png" alt="Jolpai Keyboard layouts: English QWERTY, Avro phonetic Bangla and National (Jatiya) Bangla keyboard on Android" width="100%">

*English QWERTY · Avro phonetic (ami → আমি) · National (জাতীয়) layout, shown in the Olive night theme*

<img src="docs/images/themes.png" alt="Jolpai Keyboard themes: Material You, light pastel themes and soft dark themes for the Android Bangla keyboard" width="100%">

*20 of the 64 built-in themes. Material You follows your wallpaper colours.*

</div>

## 📥 Download & install

> **Status: beta (v0.11.0).** All seven development phases are built and 350+ unit tests pass. The app runs
> daily on a Pixel 7 Pro (Android 17); testing on more phones is ongoing. See the [roadmap](docs/ROADMAP.md)
> for exactly what works and what is pending.

**[⬇️ Download the latest APK](https://github.com/Yeamin-Talukder/jolpai-keyboard/releases/latest)** from the Releases page, or visit the website: **[yeamin-talukder.github.io/jolpai-keyboard](https://yeamin-talukder.github.io/jolpai-keyboard/)**.
Most phones need the **`arm64-v8a`** file; older 32-bit phones need `armeabi-v7a`. Android 8.0 or newer.
Or [build it from source](#-build-from-source) (about 5 minutes with Android Studio).

> **Coming from 0.7.0 or 0.8.0?** From 0.9.0 the app ID is `com.yeamin.jolpai` (it was
> `com.yeamin.lipi`), so Android installs it as a new app instead of an update. To keep your words,
> settings and themes: in the old app open **Backup & restore → Save backup**, install the latest version,
> restore the file there, then uninstall the old one. Later versions update it normally, right inside the app.

Releases are built and signed by GitHub Actions from the tagged source, with SHA-256 checksums in
`SHA256SUMS.txt`. Every release uses the same signing key, so a new version **installs as an update**
and keeps your words and settings. The next release adds **About → Check for updates**, which asks
GitHub for a newer version only when you tap it.

**Turn it on after installing:**
1. Open **Jolpai Keyboard**.
2. Step 1: switch on **Jolpai Keyboard** in Android's keyboard list. Android warns that keyboards can see what you type; Jolpai keeps everything on your phone.
3. Step 2: choose **Jolpai Keyboard** as your current keyboard.
4. Try it in the app's test fields (text, email, password, number, phone, multi-line).

## 🏗️ Architecture

Jolpai is a native Android **`InputMethodService`** written in **Kotlin**, split into 11 Gradle modules.
The hardest logic (the Bangla engines and suggestions) is **plain Kotlin with no Android dependency**,
so it is fast to unit-test on a PC.

```mermaid
flowchart TD
    APP["app-shell<br/>Settings app (Compose), setup, backup"]
    UI["keyboard-ui<br/>JolpaiImeService · Canvas key grid · panels"]
    CORE["keyboard-core<br/>KeyboardController state machine · layouts · EditorSession"]
    BN["bangla-engine (JVM)<br/>Avro phonetic · National composer"]
    SG["suggestion-engine (JVM)<br/>dictionaries · autocorrect · next-word"]
    EM["emoji-engine (JVM)<br/>emoji + EN/BN keywords"]
    TH["theme-engine (JVM)<br/>64 themes · import/export"]
    CB["clipboard-manager<br/>history · snippets · categories"]
    VO["voice-input<br/>speech · spoken punctuation"]
    ME["media<br/>GIFs · stickers"]
    DATA["core-data<br/>Room + SQLCipher · DataStore"]

    APP --> UI
    APP --> DATA
    UI --> CORE
    UI --> CB
    UI --> VO
    UI --> ME
    UI --> EM
    CORE --> BN
    CORE --> SG
    CORE --> TH
    CB --> DATA
    SG -.learning.-> DATA
```

**The typing path** is built for speed: a key press reaches the app in well under one frame (16 ms).

```mermaid
flowchart LR
    T["Touch"] --> KV["KeyboardView<br/>(Canvas hit-test)"]
    KV --> KA["KeyAction"]
    KA --> P{"Input processor"}
    P -->|English| E["EnglishProcessor"]
    P -->|অভ্র| A["AvroProcessor"]
    P -->|জাতীয়| N["NationalProcessor"]
    P -->|প্রভাত| N
    E & A & N --> ES["EditorSession<br/>(InputConnection, batched)"]
    ES --> APPX["Your app's text field"]
    ES -. async, cancelled per key .-> S["SuggestionEngine"]
    S --> STRIP["Suggestion strip"]
```

| Area | Choice and why |
|---|---|
| **Key grid** | A custom `View` drawn on a `Canvas` (not Compose) for the lowest per-key latency and full multi-touch control |
| **Panels and settings** | Jetpack Compose + Material 3 (emoji, clipboard, voice, editing, translate, settings app) |
| **Text editing** | `EditorSession` wraps `InputConnection`, batches edits and tracks the cursor itself (fast even in WebView) |
| **Bangla engines** | Avro phonetic transliterator and the National / Probhat composer in pure Kotlin |
| **Suggestions** | Sorted word lists with frequencies, typo-aware correction search (weighted edit costs tuned on public misspelling lists), an on-device neural next-word model for English (LSTM, 4.7 MB, read in plain Kotlin), Bangla next-word lists, learned pairs and triples with fading, Avro dictionary candidates, on-device learning |
| **Writing check** | Rule-based spelling, punctuation and grammar checker in plain Kotlin (English and Bangla); optional ML Kit GenAI proofreading where the phone has Gemini Nano |
| **Storage** | Room on **SQLCipher** (encrypted), key in Android Keystore; DataStore for settings |
| **DI and async** | Hilt, Kotlin coroutines and Flow; suggestions run off the main thread and are cancelled per keystroke |
| **Translation** | Google ML Kit on-device translation (English ↔ Bangla), pack downloaded on request |
| **Voice** | Android `SpeechRecognizer` (on-device where the phone supports it) |
| **Build** | AGP 9.4, Gradle 9.6, Kotlin 2.3, KSP, compile/target SDK 37, min SDK 26, R8 for release; GitHub Actions for tests, lint and signed releases |

Full design notes, platform limits and the privacy model: **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

<details>
<summary><b>📁 Project layout (modules)</b></summary>

| Module | What it holds |
|---|---|
| `bangla-engine` | Plain Kotlin (no Android). National-layout composer, Avro phonetic engine and its rule table |
| `suggestion-engine` | Plain Kotlin. English and Bangla word lists and frequencies, completion, autocorrect, the spelling/punctuation/grammar checker, Avro dictionary candidates, learning model, the next-word model reader (`NextWordModel`, `LanguageModel`) and emoji by meaning |
| `lm-trainer` | PC-only Gradle tool, not in the app: prepares Bangla text and trains/exports next-word models in the format the app reads |
| `core-data` | Encrypted Room database: learned words, word pairs, Avro picks, personal dictionary, clipboard history, snippets |
| `clipboard-manager` | Clipboard capture and history, private image copies, snippets, the 📋 panel shown in the keyboard |
| `emoji-engine` | Plain Kotlin. Unicode emoji with English and Bangla keywords, skin tones, kaomoji |
| `voice-input` | Voice typing with Android's speech service, spoken punctuation, offline status |
| `media` | GIF search (GIPHY, with your key) and your own stickers |
| `theme-engine` | Plain Kotlin. Theme model, 64 built-in themes, theme import/export |
| `keyboard-core` | Keyboard state machine, layouts, field detection, text editing over `InputConnection`, suggestion strip logic, editing commands, calculator, settings |
| `keyboard-ui` | The keyboard service (`JolpaiImeService`), the Canvas key grid, key popups, suggestion strip, gestures, panels, translation |
| `app-shell` | The app you open: setup, test fields, settings, personal dictionary, clipboard manager, snippets, themes, backup |
| `tools/` | Perl scripts that regenerate dictionaries, emoji data and icons from upstream sources |

</details>

## 🛠️ Build from source

### Requirements
- Android Studio 2026.1 or newer, using its bundled JDK
- Android SDK Platform 37 and Build Tools 36.0.0
- An Android phone with **Android 8.0 (API 26) or newer** and USB debugging on

### Build and install
Open the folder in Android Studio and run the `app-shell` configuration, or use a terminal (Git Bash on Windows):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

```bash
./gradlew :app-shell:assembleDebug
```

The build makes one APK per processor type (the offline translator's native library is large).
Most phones need `arm64-v8a`; older ones `armeabi-v7a`:

```bash
adb install -r app-shell/build/outputs/apk/debug/app-shell-arm64-v8a-debug.apk
```

### Testing
```bash
./gradlew test
```
270+ JVM unit tests cover the Avro engine (including a 413-case reference corpus), the National
composer, suggestions and autocorrect, the writing check, translate and check modes, typing stats,
gestures, themes (including label contrast checks), clipboard, update checks and more. GitHub Actions
runs them, a release build and lint on every push.

English correction accuracy is measured by `SpellingEvalTest` against Wikipedia's list of common
misspellings, Peter Norvig's spelling test sets and synthetic phone slips. The lists aren't bundled;
point `SPELL_EVAL_DIR` at a folder holding them to run it. Latest results are in the
[roadmap](docs/ROADMAP.md).

<details>
<summary><b>Release builds and signing</b></summary>

The release build is shrunk with R8 and not debuggable. By default it is signed with your PC's
debug key, so it can update a test install in place and keep its data:

```bash
./gradlew :app-shell:assembleRelease
```

To sign with your own store key (for publishing), create `keystore.properties` in the project root
(git-ignored) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, and pass `-PstoreKey`:

```bash
./gradlew :app-shell:assembleRelease -PstoreKey
```

Android only installs an update signed with the same key as the installed app. Moving a phone from
a debug-signed build to a store-signed one needs an uninstall (back up in the app first).

**Publishing a release:** the store key lives in four repository secrets (`KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`; `tools/set-release-secrets.sh` sets them from
`keystore.properties` with the GitHub CLI). Raise `versionCode`/`versionName`, add
`docs/release-notes/vX.Y.Z.md`, then push a `vX.Y.Z` tag: the Release workflow tests, builds the
signed APKs, and publishes them with the notes and checksums.

</details>

## 📖 How to use

<details>
<summary><b>Typing, layouts and suggestions</b></summary>

- **Language button (top bar, right):** tap to switch between English, বাংলা (অভ্র), বাংলা (জাতীয়) and বাংলা (প্রভাত). Hold it for Android's keyboard chooser. Choose which layouts are on in the app.
- **Avro:** type how a word sounds (`ami` → আমি). The underlined word is the best dictionary match; the strip shows the other candidates, with your English spelling last. Tap one to use it, and Jolpai remembers your pick for that spelling. Case matters (`t` ত, `T` ট); reph is `rr` (`korrm` → কর্ম).
- **জাতীয়:** Ridmik-style National layout. Hold a key to type the small letter in its corner (ু → উ, ত → ৎ). ্র is ্ then র (or hold র); reph is Shift + ্.
- **প্রভাত:** Probhat, laid out 12 · 9 · 9 like popular Bangla phone keyboards. The corner shows the shifted letter (য়: য-ফলা, ্: ঁ); hold a key for it. ৃ and ্ sit beside ম (shifted: ৃ and ঁ); ঞ is on জ, ৎ on ত. Off until you turn it on in Languages & layouts.
- **Suggestions:** the strip shows what you typed, the best suggestion and one more. With Autocorrect on (Suggestions settings; off by default), a highlighted middle suggestion is what space turns your word into; press Backspace right after to undo. Tap your own word to keep it, then "Add to dictionary".
- **Next word:** after a space, the strip suggests words you often type next (after the last one or two words; recent habits count more). In Bangla it also uses a ranked list from open text, so it works from the first word.
- **Shift:** tap for one capital; double-tap for caps lock. **Two spaces** type `. ` (English) or `। ` (Bangla).
- **Hold a key** for more characters (accents, digits, the shifted Bangla letter, ৎ ঃ ঁ). Set the hold time (150–800 ms, 200 ms by default) in Typing & gestures.
- **Number fields** get a calculator-style pad, phone fields a dial pad. Hold a digit for its Bangla form (hold 0 on the dial pad for +).
- **Calculator:** type `250*4` and tap `= 1000` in the strip (or type `12*3+4=` to add the answer after "=").
- **Predictive touch:** likely next keys get a little more room near their edges; a tap in the middle of a key always types it. Turn it off or reset it in Typing & gestures.
- In password and private fields, a 🔒 label shows that nothing is saved.

</details>

<details>
<summary><b>Cursor, editing and gestures</b></summary>

- **Space bar:** slide along it to move the cursor. Hold it still and it becomes a trackpad: move to place the cursor exactly, up and down to change line.
- **Backspace swipe:** slide left from ⌫ to select words, back right to select fewer, and let go to delete (slide all the way back to cancel). Hold ⌫ to keep deleting.
- **Hold Enter (optional, off by default):** turn on *Hold Enter for emoji* and/or *Hold Enter and slide to select* in Typing & gestures. Hold Enter to open emoji; hold and slide sideways to select text a letter at a time, up or down for whole lines, and let go to keep the selection for copy or cut. With both on, hold still and let go for emoji, hold and slide to select. A tap on Enter still sends or starts a new line, and neither gesture works in password fields.
- **Cut, copy, paste:** hold x, c or v (English and Avro).
- **✎ Editing tools:** select, copy, cut, paste, cursor keys, word selection and deletion, undo/redo (where the app supports it), date and time.
- **Custom gestures:** swipe up on space for the clipboard, down for emoji; swipe the top bar left to undo, right to redo. Assign your own swipes, holds and double-taps on Space, comma, full stop, Shift, Backspace or the top bar, including inserting your own text such as `@gmail.com`.
- **Toolbar:** choose and order 😊 📋 🎤 ✎ 🌐 ✓ in the app. Tap a toolbar icon to open its panel, and tap it again to go back to the keys. Emoji and clipboard open above the keyboard (the keys stay) or, if you prefer, in place of the keys: choose in Toolbar & panels.
- **Swipe down to close:** Translate, Check, emoji and clipboard open above the top bar; swipe down on them to close (scroll a list to the top first).

</details>

<details>
<summary><b>Clipboard and snippets</b></summary>

- **📋 Clipboard:** your recent copies (text and images), pinned items, folders and snippets. Tap to paste; long-press for more. Only copies made while Jolpai is your keyboard are saved, never from password fields or copies an app marks sensitive.
- **Categories:** copies are sorted automatically into links, emails, codes, phone numbers, addresses and code; tap a category to filter.
- **Snippets:** save text with a shortcut (e.g. `@addr`), then type the shortcut and press space to insert it. Snippets have categories and ready-made templates.

</details>

<details>
<summary><b>Translate, voice, emoji and GIFs</b></summary>

- **✓ Check (offline):** tap Check in the top bar and a card opens above the keys with what may be wrong in the text field: spelling (English and Bangla, from the word lists and your own words), punctuation (spaces around marks, doubled marks, দাঁড়ি instead of "." in Bangla, a missing full stop) and common English grammar (a/an, "i" → "I", sentence capitals, repeated words, "could of", "better then", "he don't", "its a", alot …). Each card shows the mistake → the fix and why; tap Fix, Ignore, or Fix all; ↻ checks again after you type more. Links, e-mail addresses, numbers and names are left alone. Nothing leaves the phone.
- **Emoji and clipboard above the keys:** they open in a section above the top bar, so the keys stay where they are; press any key (or swipe the section down) to close it and keep typing.
- **🌐 Translate (offline):** tap Translate in the top bar and a box opens above the keys. Type English and the Bangla translation appears as you type; tap ➤ or Enter to insert it. Spelling and grammar fixes show as chips under the box (tap to fix, nothing changes on its own). ⇄ turns it round (Bangla → English). Download the Bangla pack once (about 30 MB); after that it works without internet and the text never leaves the phone.
- **🎤 Voice typing:** tap the mic and speak; words appear in the top bar and the keys stay usable. Hold the mic for the full voice panel. Swipe the voice bar down to hide the keys, or left/right to turn it into a floating pill you can drag. Say "comma", "full stop", "new line" (or "কমা", "দাঁড়ি", "নতুন লাইন"). Offline support depends on your phone; the Voice screen shows what it supports.
- **😊 Emoji:** categories, recents, favourites, skin tones (long-press), search in English or Bangla, kaomoji, your stickers and GIFs.
- **GIFs** need a free GIPHY API key entered in the app (Google closed Tenor's API in 2026).

</details>

<details>
<summary><b>Themes, size and feel</b></summary>

- **Themes:** 64 built-in themes or your own (colours, gradient or a picture). Pick one from the app's home page or the Themes screen; changes show immediately. Share a theme by copying it as text.
- **Size & layout:** keyboard height (portrait and landscape separately), number row, one-handed (left/right) and a floating keyboard you can drag.
- **Sound & vibration:** vibration strength, key sounds and volume.
- **Feel:** keys sink and spring back softly, and the letter bubble pops up and fades.
- **Key font, sound pack, split keyboard:** pick a Bangla-friendly key font with a live preview in Size & layout; Soft tap, Typewriter, Bubble or Wooden sounds in Sound & vibration; split the keys in landscape or always.
- **Typing stats** (home page): words today and per day, typing speed (words per minute counted only while you type, best burst, accuracy), English vs বাংলা, and what autocorrect and suggestions saved you. Only numbers are kept, never words, and nothing from password fields.
- **Backup & restore:** save settings, personal dictionary, snippets and custom themes to a file you choose (learned words only if you tick the box).

</details>

## ❓ FAQ

**What is the best Bangla keyboard for Android?**
It depends on what you need. Jolpai is a strong choice if you want everything in one free app: Avro
phonetic typing, the National (জাতীয়) and প্রভাত layouts, on-device AI predictions, an offline Bangla and
English spelling check, offline translation and 64 themes, with no ads and no tracking. It is open
source, so anyone can check what it does with your typing.

**Is there an AI-powered Bangla keyboard?**
Yes. Jolpai runs a small neural model on your phone: it reads your sentence to suggest the next word,
rank suggestions and fix typos, and picks emoji by meaning. Bangla next words come from a list counted
from 91 million words of open Bangla text, plus what you type. Nothing is sent to a server.

**Which Bangla keyboard has the best design?**
Jolpai is built for a clean, modern look: centred Bangla vowel signs without dotted circles, Material You
colours, 64 themes (light, dark, gradients and your own photo), rounded keys, key fonts and smooth
animations. See the [screenshots](#-screenshots) and the [live demo](https://yeamin-talukder.github.io/jolpai-keyboard/).

**How do I type Bangla on Android with Jolpai Keyboard?**
Install Jolpai, turn it on in the app's two setup steps, then tap the 🌐 language button to choose
বাংলা (অভ্র) for phonetic typing (`ami` → আমি) or বাংলা (জাতীয়) for the National layout, or বাংলা (প্রভাত) for Probhat (turn it on in Languages & layouts first).

**Does Jolpai Keyboard support Avro phonetic typing?**
Yes. It has a built-in Avro phonetic engine using the open Avro rule table from OpenBangla, with
dictionary candidates and learning of your choices. It works fully offline.

**Does it have the National (জাতীয়) / Jatiya layout? What about Probhat or Bijoy?**
Yes, the Bangladesh National layout is built in, arranged like Ridmik Keyboard. The Probhat (প্রভাত)
layout is built in too (turn it on in Languages & layouts). The Bijoy layout is not included.

**Is Jolpai Keyboard safe? Does it collect what I type?**
No. It has no account, analytics, ads or crash-reporting SDK, and stores learned words and clipboard
history only on your phone, in an encrypted database. Password and incognito fields are never learned.
The code is open source, so you can check this yourself.

**Does Jolpai Keyboard work offline?**
Yes. Typing, suggestions, Avro, the spelling and grammar check, the clipboard and themes need no
internet. Translation works offline after a one-time pack download. Only GIF search needs the internet
(and checking for updates, when you tap it).

**Can I translate English to Bangla inside the keyboard?**
Yes. The 🌐 Translate box translates English → Bangla (and back) as you type, on your device.

**Does it check Bangla spelling and grammar?**
Yes. ✓ Check finds Bangla spelling mistakes (look-alike letters, vowel signs, word endings) and
punctuation such as দাঁড়ি, plus English spelling, punctuation and common grammar, all offline. It is
rule-based, so it won't judge meaning or style.

**Which Android versions are supported?**
Android 8.0 (Oreo, API 26) and newer. Material You colours need Android 12 or newer.

**Is it free?**
Yes. It is free and open source under the Apache License 2.0, with no ads and no in-app purchases.

**How is it different from Gboard or Ridmik Keyboard?**
Jolpai combines Avro phonetic, the National layout and English in one open-source keyboard that keeps
everything on your device, with an offline Bangla and English writing check, offline translation, a
full clipboard manager and 64 themes.

## 🇧🇩 বাংলায়: জলপাই কিবোর্ড

**জলপাই কিবোর্ড** অ্যান্ড্রয়েডের জন্য একটি ফ্রি, ওপেন-সোর্স ও এআই-চালিত **বাংলা কিবোর্ড**।
এতে আছে **অভ্র ফোনেটিক** (`ami` → আমি), **জাতীয় কিবোর্ড লেআউট**, ইংরেজি QWERTY, স্মার্ট সাজেশন,
**অফলাইন বানান ও ব্যাকরণ চেক**, **অফলাইন ইংরেজি↔বাংলা অনুবাদ**, ভয়েস টাইপিং, ক্লিপবোর্ড ম্যানেজার এবং ৬৪টি থিম।

- 🔒 **প্রাইভেসি আগে:** কোনো অ্যাকাউন্ট, বিজ্ঞাপন বা ট্র্যাকিং নেই। আপনি যা লেখেন তা আপনার ফোনেই থাকে, এনক্রিপ্টেড অবস্থায়।
- ⚡ **দ্রুত:** প্রতিটি কী-চাপ এক ফ্রেমেরও কম সময়ে লেখা হয়।
- 📴 **অফলাইন:** টাইপিং, সাজেশন, বানান চেক ও অনুবাদ ইন্টারনেট ছাড়াই চলে।
- ✓ **বানান চেক:** বাংলা ও ইংরেজি লেখার বানান, যতিচিহ্ন ও ব্যাকরণের ভুল দেখায়, এক ট্যাপে ঠিক করা যায়।
- 📊 **টাইপিং পরিসংখ্যান:** প্রতিদিন কত শব্দ লিখলেন, টাইপিং স্পিড (WPM) ও নির্ভুলতা।
- 🎨 **Material You:** আপনার ওয়ালপেপারের রঙে কিবোর্ড সাজে, সাথে সফট ডার্ক থিম।

## 🤝 Contributing

Bug reports, Bangla word-list fixes, new themes and pull requests are welcome.
Please open an [issue](https://github.com/Yeamin-Talukder/jolpai-keyboard/issues) first for larger changes.
If Jolpai is useful to you, **⭐ star the repo** so more Bangla users can find it.

## 📚 Documents
- [Architecture](docs/ARCHITECTURE.md): modules, typing pipeline, Bangla engines, privacy model, Android limits
- [Roadmap](docs/ROADMAP.md): phases, feature checklist, what is pending
- [Privacy policy](docs/PRIVACY.md)
- [Release notes](docs/release-notes/): what changed in each version
- [`jatiya-keyboard-demo.html`](jatiya-keyboard-demo.html): browser demo of the National layout

## 📜 Third-party data
Each folder holds the licence text and a NOTICE with the exact source and commit.
- **Avro Phonetic rule table**: `bangla-engine/src/main/resources/avro/`, from [OpenBangla/rupantor-rs](https://github.com/OpenBangla/rupantor-rs) (MPL-2.0, unmodified).
- **English word list**: `suggestion-engine/src/main/resources/en/`, from AOSP LatinIME (Apache-2.0).
- **Bangla word list and Avro search patterns**: `suggestion-engine/src/main/resources/bn/`, from [OpenBangla/upodesh](https://github.com/OpenBangla/upodesh) (MIT).
- **Bangla word frequencies**: same folder, `bangla-frequency.tsv`, from [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords) (code MIT; counts from OpenSubtitles 2018, CC BY-SA 4.0; this file is shared under CC BY-SA 4.0).
- **Bangla next-word lists**: `suggestion-engine/src/main/resources/bn/bangla-nextwords.tsv` (CC BY-SA 4.0) and `bangla-trigrams.tsv` (CC BY 4.0), counted by `tools/prepare-bangla-nextwords.pl` from [Bangla Wikipedia](https://bn.wikipedia.org) (pairs only, CC BY-SA 4.0), the [Leipzig Corpora Collection](https://wortschatz.uni-leipzig.de/en/download/ben) (ben-bd_web_2017_1M, ben_newscrawl_2017_1M; CC BY) and [Tatoeba](https://tatoeba.org) (CC BY 2.0 FR). Details in `bn/NOTICE.txt`.
- **English next-word model**: `suggestion-engine/src/main/resources/lm/en.nwp.bin`, [youcoded/next-word-english](https://huggingface.co/youcoded/next-word-english), models trained and released by Destin Moss via youcoded.ai, 2026 (ODC-By 1.0 and CC BY 2.0 FR; built from FineWeb by Hugging Face and [Tatoeba](https://tatoeba.org) sentences). Notices in the same folder.
- **Key fonts**: `keyboard-ui/src/main/res/font/` (Hind Siliguri, Tiro Bangla, Galada, Atma) from Google Fonts, SIL Open Font License 1.1; notice in `keyboard-ui/src/main/resources/fonts/`.
- **Avro suffixes and Roman autocorrections**: same folder, from [OpenBangla/riti](https://github.com/OpenBangla/riti) (MPL-2.0, unmodified).
- **Emoji data**: `emoji-engine/src/main/resources/emoji/`, Unicode Emoji 17.0 and CLDR 49 annotations (Unicode License V3).
- **Icons**: Material Symbols (Apache-2.0).

MPL, CC BY-SA and OFL apply to those files only; the rest of the app is not affected.

## 📄 License
Copyright 2026 Md Yeamin Talukder

Jolpai Keyboard is licensed under the [Apache License, Version 2.0](LICENSE). The third-party data
files listed above keep their own licences (MPL-2.0, MIT, Apache-2.0, Unicode License V3), as stated
in the NOTICE file in each folder.

<div align="center">
<img src="docs/images/icon-512.png" alt="Jolpai Keyboard app icon: a green olive" width="72">
<br>
<sub><b>Jolpai Keyboard</b> · জলপাই কিবোর্ড · open-source Bangla keyboard for Android · Made in Bangladesh 🇧🇩</sub>
</div>
