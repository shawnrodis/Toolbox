# Toolbox

Toolbox is a personal utility suite that brings a collection of everyday tools together in one place: converters, trackers, calculators, document tools, a photo tool, and a customizable home screen with an optional Desktop Mode. It comes in two editions of the same app — a native Android app and a single-file web app — so the same Toolbox can be used on a phone or on a desktop or laptop computer.

The **web edition is a web app that can be used on Windows, Mac, and Linux**: it is one HTML file (`web/Toolbox.html`) that runs in any modern browser, with no install and no build step. It works opened directly from disk (`file://`) or hosted on any static web host. The **Android edition** is a true native app built with Kotlin and Jetpack Compose.

## Tools

| Tool | What it does |
|---|---|
| Currency Converter | Convert any world currency with live and offline rates. |
| Time Converter | Convert 12/24-hour time across world time zones. |
| Shopping Assistant | Compare prices and unit costs across product options, and check off your shopping list. |
| Unit Converter | Convert weight, temperature, length, volume, and area units. |
| PTO Tracker | Track paid leave balances, carryover, and logged days. |
| Split Gas & Parking | Share trip gas and parking costs evenly. |
| Tip Calculator | Calculate tips and split restaurant bills fast. |
| Tally Tool | Keep tally counts, manage lists, and export them. |
| Notes | Bullet-point notes, newest modified first. |
| Flight Tracker | Track live flights with Aviationstack telemetry. |
| SpongeBob Meme Generator | Convert text to aLtErNaTiNg SpOnGeBoB mEmE case. |
| PDF Tools | Merge, split, rotate, convert, and OCR PDF documents. |
| Square Pic | Square any photo with a blur, color, or gradient fill, with lossless PNG export. |
| Settings | Customize the theme and custom accent color, home-screen style, Desktop Mode, UI scale, sounds, API keys, and optional sync. |

Most tools can share their results or export them as JPG or PDF from the buttons in the tool's header.

## Download and install

### Android

1. Open this repository's **Releases** page.
2. Download the `Toolbox_vX.Y.Z.apk` file from the latest release.
3. Open the downloaded APK on your Android device.
4. If Android asks, allow installs from that source (your browser or file manager), then install.

The Android app requires Android 8.0 (API 26) or newer.

### Web (Windows, Mac, and Linux)

1. **Download [Toolbox_v1.2.1.html](https://github.com/shawnrodis/toolbox/releases/download/v1.2.1/Toolbox_v1.2.1.html)** — the direct download from the latest release. (The same file also lives in this repository at `web/Toolbox.html`.)
2. Open `Toolbox.html` in any modern browser — double-clicking the file works.

That's all: the web app is a single self-contained HTML file with no install and no build step. It can also be placed on any static hosting service.

## Versioning

The Android app and the web app are two editions of the same app and **share one version number**: a release bumps both together, so `Toolbox_v1.2.1.apk` and `Toolbox_v1.2.1.html` are the same version of Toolbox on each platform. The web edition shows its version at the bottom of its **Settings** page (and in a `version` meta tag in the file); the Android edition shows "Android Edition · Version …" at the bottom of its **Settings** page too, as well as in Android's **Settings → Apps → Toolbox**.

## Updates

Both editions check for new versions themselves. On launch — at most once a day — the app asks this repository's GitHub Releases for the latest version (a single unauthenticated request to `api.github.com`; nothing about you or your data is sent, and there is no analytics). You can also check any time in **Settings → App updates**, where the current and latest versions are shown.

- **Android:** when an update exists, tap **Download & install** — the app downloads the new APK itself and opens Android's installer for you to confirm (Android does not allow sideloaded apps to install silently). Your data carries over, as with any reinstall-over.
- **Web:** when an update exists, the home tab shows a small notice and Settings offers **Download the new version** — a web page can't replace the file it came from, so save the new file and open it; your saved data stays in your browser.

## Add your own API keys

Most of Toolbox works with no keys at all. Two features use a free third-party API key that **you** supply — no keys are built into either edition.

### Flight Tracker — Aviationstack key

1. Sign up for a free API key at [aviationstack.com](https://aviationstack.com/).
2. Open Toolbox and go to **Settings → API Keys**.
3. Paste your key into the **Aviationstack** field and save.

### PDF Tools cloud OCR — Google Gemini keys

1. Get a free API key from [Google AI Studio](https://aistudio.google.com/apikey).
2. Open Toolbox and go to **Settings → API Keys**.
3. Paste the key into **Gemini API Key 1**. If you have a second key, add it under **Gemini API Key 2** — cloud OCR tries Key 1 first and automatically falls over to Key 2.

PDF Tools' offline OCR needs **no key**: the Android edition uses on-device text recognition, and the web edition uses in-browser OCR. Your keys are kept in the app's local storage on your device (or in your browser's local storage for the web edition); they are never built into the app. If you choose to enable Sync, Toolbox can also carry your saved key settings to *your own* sync server — see the privacy notes below.

## Sync across devices (optional)

Toolbox can optionally keep its settings and tool data in step between your devices, with the newest change winning. Pick whichever backend suits you in **Settings → Sync Across Devices**:

- **Own server** — a small sync server that **you** run yourself: you enter your own server address and a sync token that you choose. No sync server is provided with Toolbox and no server address is built into the app. The complete how-to, including a ready-to-run reference server, is in [SYNC_SERVER.md](SYNC_SERVER.md).
- **Firebase** — no server to run: create a free Firebase project, paste its config into Toolbox, and sign in with an email + password. Your data lives in your own project, readable only by your account. Step-by-step setup is in [FIREBASE_SYNC.md](FIREBASE_SYNC.md).

## Building from source

### Android

You need JDK 17 and the Android SDK (Android Studio provides both):

```sh
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Web

Nothing to build — open `web/Toolbox.html` in a browser.

Development history and internal build notes live in [DEV_NOTES.md](DEV_NOTES.md).

## Privacy notes

- Toolbox stores its data locally: on-device storage for the Android app, and your browser's local storage for the web app.
- Toolbox has no hosted backend and no built-in sync address.
- The only network calls Toolbox makes are to services implied by the features you use: currency-rate lookups, Aviationstack (with your key) for flight lookups, Google Gemini (with your key) for cloud OCR — and your own sync backend (your server, or your Firebase project), only if you set one up.
- API keys you enter are not built into the app. Note that if you enable Sync, the synced settings include your saved API-key fields, sent only to the sync backend you configured — so run your sync server somewhere you trust, and protect it as described in [SYNC_SERVER.md](SYNC_SERVER.md), or use your own Firebase project as described in [FIREBASE_SYNC.md](FIREBASE_SYNC.md).

## License

Copyright (C) 2026 Shawn Rodis

Toolbox is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License v3.0** as published by the Free Software Foundation (see the [LICENSE](LICENSE) file in this repository). It is distributed in the hope that it will be useful, but **without any warranty**. Under the GPL, anyone who distributes a modified version of Toolbox must share their changes under the same license — so Toolbox and everything built on it stay open.
