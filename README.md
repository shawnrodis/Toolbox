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

1. Download `web/Toolbox.html` from this repository, or clone/download the whole repository.
2. Open `Toolbox.html` in any modern browser — double-clicking the file works.

That's all: the web app is a single self-contained HTML file with no install and no build step. It can also be placed on any static hosting service.

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

Toolbox can optionally keep its settings and tool data in step between your devices using a small sync server that **you** run yourself: the app pushes your changes to your server and pulls newer state back, with the newest change winning. No sync server is provided with Toolbox and no server address is built into the app — in **Settings → Sync Across Devices** you enter your own server address and a sync token that you choose. The complete how-to, including a ready-to-run reference server, is in [SYNC_SERVER.md](SYNC_SERVER.md).

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
- The only network calls Toolbox makes are to services implied by the features you use: currency-rate lookups, Aviationstack (with your key) for flight lookups, Google Gemini (with your key) for cloud OCR — and your own sync server, only if you set one up.
- API keys you enter are not built into the app. Note that if you enable Sync, the synced settings include your saved API-key fields, sent only to the sync server address you entered — so run your sync server somewhere you trust, and protect it as described in [SYNC_SERVER.md](SYNC_SERVER.md).
