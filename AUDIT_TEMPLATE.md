# Audit Template — Toolbox HTML → True-Native Android

Use one copy of the checklist below **per HTML feature/tool**.
Source HTML: `~/workspace/user/files/Toolbox__Shawn_Rodis__0_c2n6.html`
Summary audit (already done): `~/workspace/native-android/Toolbox_Native_Audit_2026-10-22.md`

> **Security rule for every section:** if a feature touches an API key, record
> only *where the key comes from* (native Settings, on-device store). Never
> paste a key value into this document or into the Android project, and never
> hardcode a key in the APK.

## Feature index (fill one checklist per row)

| # | Feature (from audit) | Audit done | Native equivalent chosen | Complexity (S/M/L/XL) | Phase |
|---|---|---|---|---|---|
| 1 | Currency Converter — live rates, open.er-api.com, saved state | ☐ | | | 1 |
| 2 | Time Converter — 12/24h, time zones, tracked times list | ☐ | | | 1 |
| 3 | Shopping Assistant — unit-price Compare Options + Shopping List | ☐ | | | 1 |
| 4 | Unit Converter — mass, temperature, length, volume, area, speed | ☐ | | | 1 |
| 5 | PTO Tracker — leave settings + logged entries, modal add form | ☐ | | | 1 |
| 6 | Split Gas & Parking — split, copy summary, export JPG/PDF | ☐ | | | 1 (exports: 3) |
| 7 | Tip & Bill Split — tip slider, breakdown, copy, export JPG/PDF | ☐ | | | 1 (exports: 3) |
| 8 | Tally Counter — multiple tallies, export data / JPG / PDF, sound | ☐ | | | 1 (exports: 3) |
| 9 | Notes — list + editor, bullets, timestamps | ☐ | | | 1 |
| 10 | Flight Tracker / SkyTrack — Aviationstack, saved flights, search history | ☐ | | | 2 |
| 11 | SpongeBob Meme Generator — alternating-case transform, copy | ☐ | | | 1 |
| 12 | PDF Tools — Merge, Split/Extract, Rotate, PDF→Images, Images→PDF, Gemini OCR (+ failover), Tesseract OCR, searchable OCR | ☐ | | | 3 |
| 13 | Square Pic — 2048×2048 PNG, blur/solid/gradient bg, fit vs fill/crop, pan | ☐ | | | 3 |
| 14 | Translator — Gemini Gem logic (known item from the original Toolbox, 2026-10-07; confirm presence/scope in the uploaded file during the detailed pass) | ☐ | | | TBD |
| 15 | Settings — 13 accent presets + custom color, home view (List/iPhone/Android), Desktop Mode, Light/Dark/Black, UI scale, sound, API keys (Aviationstack, Gemini 1 & 2), Sync Across Devices (custom URL + token, interval), Reset Everything | ☐ | | | 1 (Sync: 3) |
| 16 | Desktop / Home launcher — icon grid, search/filter, taskbar pins, window-manager mode (no 1:1 Android equivalent; native home grid + navigation by default) | ☐ | | | 1 |

Known storage keys to account for across features (from the summary audit):
`toolbox_notes`, `toolbox_tally_items`, `toolbox_shop_items`,
`toolbox_comparison_options`, `toolbox_pto_logs`, `toolbox_pto_settings`,
`toolbox_tracked_times`, `toolbox_currency_state`, `toolbox_time_state`,
`toolbox_split_state`, `toolbox_tip_state`, `toolbox_uc_state`,
`toolbox_sbm_state`, `toolbox_tools_config`, `toolbox_accent`, `toolbox_dark`,
`toolbox_custom_accent`, `toolbox_desktop`, `toolbox_ui_scale`,
`toolbox_home_view`, `toolbox_sound`, `toolbox_taskbar_pins`,
`toolbox_aviationstack_key`, `toolbox_gemini_key_1/2`, sync settings/pending
keys, `pdf_suite_preview_scale`, `skytrack_mobile_state`.
No IndexedDB use was found in the summary audit (104 `localStorage` uses).

---

## Per-feature checklist (copy this section per feature)

### Feature: _name_

- **HTML location:** _section / element ids / line range in the source file_
- **JS functions:** _names of the ~functions implementing it, and what each does_
- **UI elements:**
  - [ ] Inputs (fields, sliders, toggles, pickers) — list each + validation/defaults
  - [ ] Outputs / lists / modals / toasts — list each
  - [ ] Gestures / drag / pan / crop interactions — list each
- **JS logic:** _core calculations/transforms, edge cases, formatting rules worth preserving exactly_
- **localStorage / IndexedDB use:**
  - Keys read/written: _list_ · Shape of stored value: _JSON shape / plain string_
  - Migration note: _does existing web data need importing, or start fresh natively?_
- **Network / API calls:**
  - Endpoint(s): _URL + method, no key values_
  - Key source: _native Settings on-device store / none_
  - Offline / failure behaviour in web version: _describe_
- **File / browser APIs used:** _clipboard, file picker, download, canvas,
  html2canvas/html2pdf, Web Audio/sound, notifications, etc. — or "none"_
- **External JS libraries:** _pdf-lib, pdf.js, Tesseract.js, Tailwind, etc. — or "none"_
- **Native equivalent (Kotlin / Compose):**
  - Screen / route: _e.g. `Routes.XXX` + composable name_
  - State: _ViewModel / state holder design_
  - Storage: _DataStore (simple state) or Room (lists) — which, and schema_
  - Network: _Ktor/Retrofit client, caching_
  - Platform replacement: _e.g. clipboard → ClipboardManager, OCR → ML Kit /
    Gemini, PDF → PdfRenderer + Kotlin PDF library, export → native PDF/image generation_
  - Permissions needed: _none, or list each + why_
- **Accent theme / Settings interplay:** _does it follow accent, dark/black,
  UI scale, sound settings?_
- **Complexity:** _S / M / L / XL + one-line reason_
- **Open questions for Shawn:** _anything ambiguous in the web behaviour_
- **Port status:** ☐ Not started ☐ In progress ☐ Built in debug APK ☐ Verified
