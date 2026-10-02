# Business Card Scanner

Personal Android prototype (sideload only) that turns a business card into a ready-to-send
email draft: capture the card → on-device OCR / QR decode → review screen with editable
fields and a rendered email preview → **Send** opens your own email app (Outlook, Gmail, …)
with recipient, subject and body prefilled. You hit Send there, so every email lands in your
real Sent folder.

No accounts, no server, no LLM, and no `INTERNET` permission — everything runs on the phone.
The email body is a template with variable substitution, so every draft is deterministic.

## Layout

| Folder | Stack | Purpose |
|--------|-------|---------|
| [`card-scanner-android/`](card-scanner-android/) | Kotlin · Jetpack Compose · CameraX · ML Kit · Room · zxing | The phone app |

## Flow

```
[Card / QR] → [capture → OCR + barcode on the same frame → review]
                    │
                    ├─(Send)→ mailto: draft in the user's email app
                    └───────→ Room history (fields + rendered draft, reopenable later)
```

## Features

- **Unified scan** — one shutter runs ML Kit OCR and barcode detection together.
  - vCard / MECARD QR codes are exact and override OCR.
  - LinkedIn / URL QR codes only fill gaps; with no email the scan becomes *Save contact*
    with a context note and an *Open LinkedIn profile* action.
- **Sideways capture** — the card is held rotated 90°; OCR runs at both rotations and keeps the
  better read.
- **Email-driven field parsing** — name and company are derived from the email address /
  LinkedIn URL and matched back against OCR lines (fuzzy, diacritic-aware), instead of trusting
  layout heuristics that tend to swap name and company.
- **Templates** — subject and body support `{first_name}`, `{name}`, `{role}`, `{company}`.
- **History** — every scan is stored locally; reopen the original draft in the email app at any time.
- **CSV export** — export history for import into a CRM, shared as an attachment.
- **My card** — store your own details and show them as a vCard QR that any phone camera can
  add to contacts (full brightness, black-on-white).
- **Help** screen in-app.

## Build & install

Enable USB debugging on the phone, plug it in, accept the RSA prompt, then:

```
cd card-scanner-android
.\gradlew installDebug
```

Requires `adb` on `PATH` (`%LOCALAPPDATA%\Android\Sdk\platform-tools`). If Gradle can't find
Java, set `JAVA_HOME` to Android Studio's bundled JBR. Unit tests: `.\gradlew test`.

Owner: Erik Silver, House of Silver AB. Licensed under the [MIT License](LICENSE).
