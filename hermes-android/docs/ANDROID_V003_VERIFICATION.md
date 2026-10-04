# Hermes Pocket v0.03 targeted actual Android verification

Release SHA256: `b079bab979b6a3fa18e19151b6a53c575ad1ec2c25550a1618e13d8c6cc02c5e`. Final APK installed as an upgrade on emulator-5554, Android 15 / API 35. Existing SQLite history remained present. This agent never touched the connected physical phone.

All targeted UX checks passed through the installed release WebView and actual native bridge:

- Unconfigured startup automatically opens the centered model setup guide; model and thought composer buttons are disabled. Guide CTA opens model settings.
- Header brand is plain text, with no old brand button or current-chat navigation entry.
- Model button opens only the model picker: 16 actual HTTP fixture models, no manual text field or thought controls. List height 258 CSS pixels / row height 52 pixels gives approximately five visible rows and scrolling.
- Clicking fixture-model-14 saves the model and preserves high effort.
- Thought button opens only nine thought choices, with no model controls. Clicking low saves effort and preserves fixture-model-14.
- Fresh process restores fixture-model-14 / low and retained earlier SQLite conversations.
- Actual viewport/body width both 412 CSS pixels: no horizontal overflow.

The first setup test initially failed because external instrumentation ended its process immediately after SharedPreferences.apply(), discarding pending test setup writes. The harness now waits before termination; settled cold startup and picker persistence both pass. No production change was made for this test artifact.

Evidence: [machine report](android-v003/release-report.json), [startup checks](android-v003/first-launch-checks.json), [picker checks](android-v003/picker-checks.json), [cold defaults](android-v003/cold-defaults.json), [setup screenshot](android-v003/setup-guide.png). Model API uses labeled local fixture data, not paid provider credentials.

Root owns S24 Ultra / Android 16 verification and observed original downloaded files were 273-byte Gmail security-block HTML, not APKs. Root reported direct USB installation of this v0.03 hash succeeded. This document does not claim physical testing performed by this child agent.


## Actual device boundaries (v0.03, API 35)

[Native results and real approval dialog decisions](android-v003/device-boundaries.json) cover all 13 registered tools. Device state and app listing succeed with the device plugin enabled. A disabled plugin refuses execution. Eleven mutating/permission-dependent calls return explicit denials or missing-permission errors: launch/settings/volume denied through the native dialog; brightness lacks WRITE_SETTINGS; root process/Wi-Fi lack verified root; force-stop lacks allowlist; screen read lacks approval; click/type/scroll lack accessibility. This is evidence that unavailable operations do not falsely succeed, not a claim that all 13 tools execute on an unprivileged phone. Root, accessibility and WRITE_SETTINGS remain false in actual Android state.
