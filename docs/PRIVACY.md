# Lipi Keyboard — Privacy policy

Last updated: 2026-11-06

Lipi is a keyboard. A keyboard can see everything you type, so Lipi is built to keep that
on your phone. This page explains exactly what it stores, what it sends, and how to control it.

## What Lipi stores on your phone
| Data | Why | Where | Your control |
|---|---|---|---|
| Words you type and which word usually follows which | Better suggestions and next-word predictions | App database on the phone | Settings → Privacy: turn off "Learn from my typing" or "Clear learned data" |
| Your Avro choices (e.g. you picked ভালোবাসি for "bhalobashi") | Show your choice first next time | App database | Cleared with learned data |
| One average tap position per key (how far from each key's centre you usually touch it) | Predictive touch: adjust key edges to how you type | Private preferences file, left out of cloud backup and device transfer | Typing & gestures: turn off "Learn my touch" or "Reset learned touch". Never learned in password or private fields; the letters you type are not stored |
| Personal dictionary | Words you added on purpose | App database | Edit in Personal dictionary |
| Clipboard history (text, and copies of pictures) | The 📋 clipboard panel | App database and private files | Settings → Clipboard: turn off, set how long items are kept, clear |
| Snippets, custom themes, stickers, settings | Features you set up | App database, settings file, private files | Edit or delete in the app |
| Emoji you use | Recent and favourite emoji | App database | — |

**Never stored:** anything typed in password fields; anything typed in fields that ask
keyboards not to learn (for example incognito browser tabs); copies made in a password field
or marked sensitive by the app (password managers, banking apps on Android 13+). Numbers you type
(card, account, phone numbers) are never learned: only words made of letters are.

**Encrypted at rest:** the app database (learned words, clipboard text, snippets, dictionary,
themes) is encrypted with SQLCipher (AES-256), and copied clipboard pictures with AES-GCM. The key
is kept wrapped by a key in the Android Keystore (the phone's secure hardware where available),
which no app or person can read out, so copying Lipi's files off the phone yields nothing
readable. The key never leaves the phone and is not backed up; uninstalling Lipi destroys it.

**One-time codes:** a copied 4–8 digit code (an OTP) is deleted from clipboard history one
minute after it was copied, unless you pin it.

## What Lipi sends over the internet
- **Nothing by default.** Lipi has no account, no analytics, no crash reporting service and no ads.
- **GIF search**, only if you add your own GIPHY API key: the words you type into the GIF
  search box are sent to GIPHY to find GIFs, and the GIFs you pick are downloaded. GIPHY's
  privacy policy applies to those requests. Nothing you type in other apps is sent.
- **Voice typing** uses your phone's speech recognition service (usually Google's). While the
  🎤 panel is listening, your voice goes to that service, which may process it online
  depending on your phone and language. Lipi does not record or keep audio.
- **Offline translation** (🌐 Translate in the top bar) runs on your phone with Google's ML Kit
  translator: the text you translate never leaves the phone. The Bangla language pack (about
  30 MB) is downloaded from Google once, only when you tap Download (or in Languages & layouts),
  and can be deleted there. When translation is used, the ML Kit library may send Google
  diagnostic data about the library itself (app and phone model, Android version, how long
  translation took, a per-install identifier); it does not send the text.
- **Check for updates** (About screen), only when you tap it: the app asks GitHub's public
  API for the latest Jolpai release (`api.github.com`). The request carries the app version
  in its User-Agent and, like any web request, your IP address; nothing you type is sent.
  **Report a problem** opens a GitHub issue page in your browser with the app version,
  Android version, phone model and processor type filled in; nothing is sent until you
  submit it yourself.
- **✨ AI proofreading** (Check card, only on phones with Google's on-device AI, Gemini Nano):
  the text is proofread on the phone by Android's AICore system app; it is not sent to a server.
  The AI model is managed and downloaded by AICore when you first use it. The button only
  appears on phones that support it, and only for English text.
- **Typing stats** are counts only (how many words, corrections, emoji), kept on the phone,
  never counted in password or private fields; reset them under Typing stats.

## Backups
- Android's automatic cloud backup and phone-to-phone transfer **exclude** Lipi's database
  (typing history, clipboard, personal dictionary, themes) and its clipboard pictures and stickers.
  Settings may be included. They contain no typed text.
- "Backup & restore" in the app writes a file **you** choose where to save. It contains settings,
  personal dictionary, snippets and custom themes, and learned words only if you tick that box.
  It never contains clipboard history, pictures or your GIPHY key.

## Permissions
- **Microphone:** only for voice typing; asked for in the app with an explanation.
- **Internet:** only for GIF search with your own key, downloading the offline translation pack, and checking for updates, each only when you ask.
- **Vibration:** for the key-press vibration strength setting.

## Deleting everything
Clear learned data and clipboard history in the app, or uninstall Lipi: Android deletes all
of its data.

## Contact
This is a personal project. Questions about privacy can be raised with the developer.
