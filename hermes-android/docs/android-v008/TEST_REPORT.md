# Hermes Pocket v0.08 verification

Release: `dist/hermes-pocket-v0.08.apk`, 1,598,308 bytes; SHA-256 `a2899a711ba7a6fbcb0801b5de9fd53db0ed9d9935bd63bf90a5f17d18c2246b`.

## Host checks

- GUI: 69/69 PASS; `/tmp/hermes-v008-gui-final-clean.log`. Includes floating preference persistence/failure/busy state, portrait approval geometry, honest screenshot capability labels and manual capture selection.
- JVM: 67/67 PASS; `/tmp/hermes-v008-jvm-final.log`. Production API streaming/cancellation, policies and six vision cases: typed image delivery, no pixel persistence/replay, actual unsupported-input error, cancellation and image invalidation after a subsequent UI action.
- Additional transport/version tests: 15 PASS. Transport tests cover the retained old adapter, not the production native runtime.
- Host doubles do not prove native Android screenshot, accessibility or floating-window permissions.

## Native Android verification

Current v0.08 release verification is in progress; evidence is recorded under `android-v008/` with the APK hash for each run. Initial candidate capture failed because standard status/navigation bars were considered blockers. The release fixes that native capture guard only for narrowly identified normal edge chrome on Android 14+, while preserving keyboard/notification/unknown-overlay rejection and gesture guards.

The initial candidate's real native overlay passed: live status/stop, idle collapsed chip, drag changes in actual window bounds, no automatic IME, and follow-up submitted from native overlay saved into the same SQLite conversation. This candidate evidence is not proof that every native test passed on the final rebuilt APK.

## Limits and inherited verification

The previous v0.07 release had 20/20 ALL/AUTO accessibility checks on API35 and actual Shizuku UserService UID2000 settings/process tests. Full ASK-loop verification remained incomplete due a safe refusal for a remaining notification window. Historical results: `android-v007/TEST_REPORT.md`.

Screen images are transient, sent only to the configured model API, and require image input support. Editable regions are masked; visible password/authentication screens are refused entirely. Android 11–13 display-crop fallback is conservative and has not been validated in this release. Screenshots on Android 14+ capture the target app window, excluding our floating window.

Whole-app control is all/none with separate auto/ask approval. Lock, authentication, system permission confirmation and protected apps still require the owner. Root is not installed: the S24 is locked and has no working `su`. Shizuku Shell UID2000 is not Root UID0.

Production uses the Java API loop, not the complete upstream Python Hermes engine. General terminal, browser automation, cron and voice are not included. See `S24_ROOT_FEASIBILITY.md`, `HERMES_REFERENCE.md` and the retained `engine-spike/` experiments.
