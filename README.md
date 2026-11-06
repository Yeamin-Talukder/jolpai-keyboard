# Jolpai Keyboard (জলপাই)

_Formerly "Lipi Keyboard"; the code and package id (`com.yeamin.lipi`) still use that name._

A privacy-first Android keyboard with English (QWERTY), Avro phonetic Bangla and the National
(জাতীয়) Bangla layout, aiming for Gboard-level speed and features.

Package id: `com.yeamin.lipi`

**Status:** All seven phases are built and the unit tests pass,
but the keyboard has **not been tested on a phone yet**. See [the roadmap](docs/ROADMAP.md) for
exactly what works and what is pending.

## Documents
- [Architecture](docs/ARCHITECTURE.md): module structure, typing pipeline, Bangla engines, privacy model, Android limits
- [Roadmap](docs/ROADMAP.md): phases, feature checklist, open questions
- [Privacy policy](docs/PRIVACY.md)
- [`jatiya-keyboard-demo.html`](jatiya-keyboard-demo.html): browser demo of the National layout (reference for key positions)

## Project layout
| Module | What it holds |
|---|---|
| `bangla-engine` | Plain Kotlin (no Android). National-layout composer, Avro phonetic engine and its rule table |
| `suggestion-engine` | Plain Kotlin. English and Bangla word lists, completion, autocorrect, Avro dictionary candidates, learning model |
| `core-data` | Room database: learned words, word pairs, Avro picks, personal dictionary, clipboard history, snippets |
| `clipboard-manager` | Clipboard capture and history, private image copies, snippets, the 📋 panel shown in the keyboard |
| `emoji-engine` | Plain Kotlin. Unicode emoji with English and Bangla keywords, skin tones, kaomoji |
| `voice-input` | Voice typing with Android's speech service, spoken punctuation, offline status |
| `media` | GIF search (GIPHY, with your key) and your own stickers |
| `theme-engine` | Plain Kotlin. Theme model, 57 built-in themes, theme import/export |
| `keyboard-core` | Keyboard state machine, layouts, field detection, text editing over `InputConnection`, suggestion strip logic, editing commands, calculator, settings |
| `keyboard-ui` | The keyboard service (`LipiImeService`), the Canvas key grid, key popups, suggestion strip, swipe gestures, ✎ editing panel |
| `app-shell` | The app you open: setup, test fields, settings, personal dictionary, clipboard manager, snippets |
| `tools/prepare-dictionaries.pl` | Regenerates the word-list files from the upstream sources |

## Requirements
- Android Studio 2026.1 or newer, using its bundled JDK
- Android SDK Platform 37 and Build Tools 36.0.0
- A real Android phone (Android 8.0 or newer) with USB debugging turned on

## Build
From Android Studio: open this folder and run the `app-shell` configuration.

From a terminal (Git Bash), using Studio's JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

```bash
./gradlew :app-shell:assembleDebug
```

The APK is written to `app-shell/build/outputs/apk/debug/app-shell-debug.apk`.

Run the unit tests:

```bash
./gradlew test
```

### Release builds
Release build (shrunk with R8, not debuggable: other tools can't read its private files over
USB). To sign it for publishing, create `keystore.properties` in the project root (git-ignored)
with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Without it, the release APK is
signed with this PC's debug key, so it can update a test install in place and keep its data.
Switching a phone to a store-signed build later needs an uninstall (back up in the app first):

```bash
./gradlew :app-shell:assembleRelease
```

## Install and turn on the keyboard
1. On the phone, turn on **Developer options → USB debugging** and connect it by USB.
2. Install the app:
   ```bash
   "$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app-shell/build/outputs/apk/debug/app-shell-debug.apk
   ```
3. Open **Lipi Keyboard** on the phone and follow the two setup steps:
   switch it on in the keyboard list, then choose it as the current keyboard.
4. Use the **Try it** fields in the app to test each field type (text, email, password, number, phone, multi-line).

## Using the keyboard
- **Language button (top bar, right):** tap to switch between English, বাংলা (অভ্র) and বাংলা (জাতীয়). Hold it for Android's keyboard chooser. Choose which layouts are on in the app.
- **Space bar:** slide along it to move the cursor. Hold it still for a moment and the keyboard becomes a trackpad (like an iPhone): move your finger to place the cursor exactly, up and down to change line.
- **জাতীয়:** Ridmik's National layout. Hold a key to type the small letter in its corner (ু → উ, ত → ৎ). ্র is ্ then র (or hold র); reph is Shift + ্.
- **😊 key:** emoji, kaomoji, stickers and GIFs (hidden in password fields).
- **Avro:** type how a word sounds (`ami` → আমি). The underlined word is the best dictionary match; the strip above the keys shows the other candidates, with your English spelling last. Tap one to use it. Lipi remembers your pick for that spelling next time. Case matters (`t` ত, `T` ট); reph is `rr` (`korrm` → কর্ম).
- **Suggestions:** the strip shows what you typed, the best suggestion and one more. If the middle one is highlighted, space will autocorrect to it; press Backspace right after to undo. Tap your own word to keep it as typed, then "Add to dictionary" to stop it being corrected.
- **Next word:** after a space, the strip suggests words you often type next.
- **📋 Clipboard:** your recent copies (text and images), pinned items, folders and snippets. Tap to paste; long-press for more. Only copies made while Lipi is your keyboard are saved, and never from password fields or apps that mark a copy sensitive.
- **Snippets:** save text with a shortcut in the app (e.g. `@addr`); type the shortcut and press space to insert it.
- **Cut, copy, paste:** hold x to cut, c to copy, v to paste (English and Avro).
- **Number fields:** a calculator-style number pad; phone fields get a dial pad. Hold a digit for its Bangla form (hold 0 on the dial pad for +).
- **Panels:** tap a toolbar icon to open its panel and tap it again to go back to the keys.
- **Custom gestures:** swipe up on the space bar for emoji, down for the clipboard. Assign your own (swipes, holds, double-taps on Space, comma, full stop, Shift, Backspace or the top bar) in Lipi → Custom gestures, including inserting your own text such as @gmail.com.
- **Voice typing:** tap the mic and speak; the top bar shows the words as you talk and the keys stay usable (typing ends dictation). Hold the mic for the full voice panel. While dictating, swipe down on the voice bar (or tap ⌄) to hide the keys, up to bring them back, and left or right to turn it into a small floating pill. Drag the pill by any part; drop it against the left or right edge to make it tall, drag it back towards the middle to make it wide (or tap the turn button). ⌫ in the pill deletes a mistake, and ⤢ brings the keyboard back. The first time, Android asks for the microphone over your app.
- **Clipboard categories:** copies are sorted automatically into links, emails, codes, phone numbers, addresses and code; tap a category in the clipboard panel to filter. Snippets have categories and ready-made templates too.
- **✎ Editing tools:** select, copy, cut, paste, cursor keys, word selection and deletion, undo/redo (where the app supports it), date and time.
- **Backspace swipe:** slide left from ⌫ to select words, back right to select fewer, and let go to delete them (slide all the way back to cancel).
- **Predictive touch:** Lipi guesses the next letter as you type a word (lik → e) and gives likely keys a little more room near their edges; it also learns where you tap each key. The keys look the same, and a tap in the middle of a key always types it. Turn it off or reset what it learned in Typing & gestures.
- **Calculator:** type a sum like `250*4` and tap `= 1000` in the strip to replace it with the answer (or type `12*3+4=` to add the answer after "=").
- **😊 Emoji:** categories, recents, favourites, skin tones (long-press), search in English or Bangla (🔍, then type), kaomoji, your stickers and GIFs. Emoji are also suggested for the word you type.
- **🎤 Voice typing:** Bangla or English, tap to stop, switch language in the panel. Say "comma", "full stop", "new line" (or "কমা", "দাঁড়ি", "নতুন লাইন"). Allow the microphone in the app first. Whether it works offline depends on your phone; the Voice screen shows what it supports.
- **GIFs:** need a free GIPHY API key, entered in the app (Google closed Tenor's API in 2026).
- **Toolbar:** choose and order 😊 📋 🎤 ✎ in the app.
- **Themes:** 57 built-in themes (including Material You; soft pastels like Cloud, Peach, Sky, Pistachio and Sakura; bright ones like Tangerine, Lemonade, Bubblegum, Azure and Cotton candy; jewel-toned darks like Grape, Emerald, Sapphire, Ruby and Teal; Mango tango, Cosmic, Aurora, Neon and High contrast), or make your own with colours, a gradient or a picture. Pick one straight from the app's home page or the Themes screen; changes show on the keyboard immediately. Share a theme by copying it as text.
- **Feel:** keys sink, darken and spring back softly when you press them, and the letter bubble pops up and fades out.
- **Size & layout:** keyboard height (separately for portrait and landscape), number row, one-handed (left/right) and a floating keyboard you can drag around.
- **Sound & vibration:** vibration strength, key sounds and volume.
- **Shift:** tap for one capital letter; double-tap for caps lock.
- **Hold a key** for more characters (accents, digits, the shifted Bangla letter, ৎ ঃ ঁ). Set how long to hold (150–800 ms, presets Quick 200 / Normal 350 / Relaxed 500) in Typing & gestures → Hold for more characters.
- **Two spaces** type `. ` in English and `। ` in Bangla.
- **Hold backspace** to keep deleting.
- In password and private fields, a 🔒 label shows that nothing is saved.

## Privacy
Everything you type stays on the phone. Lipi has no account, analytics or ads. To improve
suggestions it remembers words you type (in a local database excluded from cloud backups),
except in password, private/incognito and no-suggestion fields. Turn learning off or clear it
in the app. The internet is used only for GIF search with your own GIPHY key; voice typing uses
the phone's speech service. Full details: [docs/PRIVACY.md](docs/PRIVACY.md).

**Backup & restore** in the app saves your settings, personal dictionary, snippets and custom
themes to a file you choose (learned words only if you tick the box).

## Third-party data
Each folder holds the licence text and a NOTICE with the exact source and commit.
- **Avro Phonetic rule table**: `bangla-engine/src/main/resources/avro/`, from
  [OpenBangla/rupantor-rs](https://github.com/OpenBangla/rupantor-rs) (MPL-2.0, unmodified).
- **English word list**: `suggestion-engine/src/main/resources/en/`, from AOSP LatinIME (Apache-2.0).
- **Bangla word list and Avro search patterns**: `suggestion-engine/src/main/resources/bn/`, from
  [OpenBangla/upodesh](https://github.com/OpenBangla/upodesh) (MIT).
- **Avro suffixes and Roman autocorrections**: same folder, from [OpenBangla/riti](https://github.com/OpenBangla/riti)
  (MPL-2.0, unmodified).

- **Emoji data**: `emoji-engine/src/main/resources/emoji/`, Unicode Emoji 17.0 and CLDR 49 annotations (Unicode License V3).

MPL applies to those files only; the rest of the app is not affected.

## License
Copyright 2026 Md Yeamin Talukder

Jolpai Keyboard is licensed under the [Apache License, Version 2.0](LICENSE). The third-party
data files listed above keep their own licences (MPL-2.0, MIT, Apache-2.0, Unicode License V3),
as stated in the NOTICE file in each folder.
