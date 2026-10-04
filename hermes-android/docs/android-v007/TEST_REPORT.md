# Hermes Pocket v0.07 verification

APK: `dist/hermes-pocket-v0.07.apk`, 1,577,828 bytes. SHA-256:
`7354d0a5cb5ff2d4dfaa837b0f8e92d1825cbd83a9495d5154ea7bef7e2e8d9b`.

## Verified build and host checks

- Final GUI suite: 65/65 PASS, including real pointer long-press and both swipe directions, cancellation, native failure restoration and keyboard deletion.
- Final JVM suite: 61/61 PASS; production HTTP contracts, cancellation, scope/approval policy, typed privileged arguments and file path rules.
- Browser/native bridge test doubles do not prove actual Android permissions or Binder operation.
- Independent APK inspection: original signing certificate, v1/v2/v3 signatures, alignment, uncompressed resources, source and packaged assets match. Shizuku classes/provider/permission/license present; no embedded Python, native ELF or test probe.
- Evidence: `dist/independent-v007-verification.json`.

## Galaxy S24 Ultra / Android 16

- Exact release APK installed by ADB with `Success`, preserving the configured model and accessibility permission.
- Main accessibility service remains bound with capability 33 (window retrieval and gesture dispatch).
- Standalone Android network probe: Mwmbl API HTTP 200, 79 results, no authorization sent. This is a shell network check, not a production model tool-loop proof: `android-v007/s24-keyless-api.json`.
- Official Shizuku manager 13.6 installed and server actually started as shell. App UI confirmed connected Shell UID2000; actual MiMo chat privileged_status → root_processes returned a bounded real process list and distinguished Root=false. See android-v007/s24-shizuku-chat-observed.json. After the diagnostic reboot, the official local server was restarted.
- Actual `su -c id -u`: exit 127; bootloader locked and Verified Boot green. No Root was installed or claimed. `android-v007/s24-root-preflight.json`; `S24_ROOT_FEASIBILITY.md`.
- Physical app interaction paused when the owner switched to another application; no unrelated screen was captured or operated.

## Product scope

Whole-app control uses global all/none scope; automatic or requested approval is independently selected. Real Android permissions, protected authentication screens and privileged service state still apply. Mwmbl provides a small keyless API search index; Tavily remains optional for supported full-page extraction.

Production uses the local Java API tool loop. Original Hermes Python engine, general terminal, browser automation, cron and voice are not included in this APK. The original engine proof and its unresolved 16KB native library audit remain under `engine-spike/` and `ANDROID_NATIVE_16K_AUDIT.md`.

API35 final frozen APK ALL/AUTO accessibility loop: actual20/20PASS (read, click, type, tap, swipe, back, home and password/stale/scope refusals); standalone fixture XML also confirms typed text. Actual Shizuku UserService UID2000 settings read/same-value write/readback and process list passed. Evidence: android-v007/accessibility-auto-loop.json and emulator-shizuku-functional.json. ASK mode full-loop validation remains pending.

Physical production MiMo web_search returned actual source links using Mwmbl; observed phone response: android-v007/s24-web-chat-observed.json. Calculator launch was observed, but a complete2+3 result was not verified because the owner changed applications/submitted other chats during the test.
