# Kosher Switch 1.1

One phone, two modes. A swipe-down tile (**כשר**) switches between **open mode** (a normal
phone) and **kosher mode** (a kosher phone with its own home screen, apps and look).
Entering is one tap; leaving needs the code. Works for an adult or a parent preparing a
child's phone.

## Kosher mode
| | Open mode | Kosher mode |
|---|---|---|
| Apps | all | only the allowed apps; the rest are suspended (they keep their home-screen spots) |
| Internet | all apps | only apps marked "Internet" (plus Google's background service they need), through an always-on blocking VPN |
| Home screen | your normal launcher | kosher home: clock, Hebrew date, widget, 3-app dock, swipe-up glass drawer |
| Settings | full | kosher Settings only (Wi-Fi, Bluetooth, flashlight, sound, display, look, language) |
| Locks | none | no installing/uninstalling, accounts, VPN/DNS, tethering, network reset, factory reset from Settings, safe mode, user switching, Bluetooth file sharing |
| Notifications | all | only allowed apps and core phone services |
| Survives restart | – | yes (tested) |

## Built-in apps
- **סידור**: Ashkenaz, Sefard and Edot HaMizrach; whole prayers as one text with a parts menu,
  search, a "now" suggestion, and Tehillim (daily, weekly, all 150 chapters). Offline.
- **לוח וזמנים**: Hebrew date, parsha, holidays, month view, and zmanim from the phone's location.
- **פתקים**: notes with bold/italic/underline, headings, bullet lists, tap-to-tick checklists,
  drawings (colors, pen sizes, eraser, undo), pinning and search.
- **עוזר (Assistant)**: understands Hebrew and English commands: calls, messages (opens
  Messages with the text ready), alarms, timers, notes, opening apps, changing the dock, order,
  icons, background and dark/light mode. Anything else is answered by an offline AI model
  (Qwen 2.5 1.5B, via Google MediaPipe) running on the phone. Nothing leaves the phone.

## Look
Light / dark / auto, five backgrounds, adjustable glass effect, three icon styles (Kosher,
Glass, Original), drag-to-arrange drawer, haptics, Hebrew or English.

## Setup (once; the app's built-in guide explains it step by step)
Kosher Switch must be the phone's *device owner*. Two ways:

**A. QR code, no computer (the phone is reset).** Factory reset, tap the first Welcome screen
6 times, scan the QR code on the download page (`docs/index.html`, published with GitHub Pages).
Android downloads the app and makes it device owner by itself.

**B. Computer, nothing erased.** Remove all accounts (Settings → Accounts), install the app, run
`adb shell dpm set-device-owner dev.kosherswitch/.KosherAdmin`, then add the accounts back.

Then open Kosher Switch: turn on the notification filter (a button takes you there), set the
code, choose apps, and optionally download the assistant.
   The assistant model can also be copied from a computer to
   `/sdcard/Android/data/dev.kosherswitch/files/assistant.task`.

## Building and updating
JDK 17 and the Android SDK (platform 34).
- Final version: `./gradlew assembleRelease`, then (in open mode, with USB debugging on)
  `adb install -r app/build/outputs/apk/release/app-release.apk`.
- **Both builds are signed with `keystore/kosher-switch.keystore`. Keep it backed up.** Updates
  must use this key; without it the installed app can't be updated, and as device owner it can
  only be removed by a factory reset.
- The final version blocks USB debugging while in kosher mode. After leaving kosher mode, turn
  USB debugging back on (Settings → Developer options) before connecting a computer.
- Debug builds (`assembleDebug`, install with `-t`) are test-only and keep USB debugging on.

## Known limits
- Factory reset from the recovery menu (hardware buttons) can't be blocked by an app.
- Android shows "This device belongs to your organization" on the lock screen.
- Links inside Waze's own web view aren't blocked.
- Other apps' dark mode follows Samsung's Dark mode button, not the kosher Settings.
These are what a custom ROM would solve.

## Credits
Prayer texts from Sefaria.org (see `app/src/main/assets/texts/SOURCES.md`); KosherJava
Zmanim (LGPL 2.1); Google MediaPipe (Apache 2.0); Qwen 2.5 (Apache 2.0); Material icon
shapes (Apache 2.0).

## Publishing
`docs/` is a ready download page: `index.html` (with the setup QR code, generated in the page)
and `kosher-switch.apk`. Push this repository to GitHub and enable Pages from the `docs` folder.
After each release build, copy `app/build/outputs/apk/release/app-release.apk` to
`docs/kosher-switch.apk`. The QR code's checksum is the SHA-256 of the signing certificate
(base64url); it only changes if the signing key changes.
