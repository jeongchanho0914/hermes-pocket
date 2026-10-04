# Hermes Pocket v0.02 actual Android verification

Tested release SHA256: `bb94a35d534cec262359d34e75374668e9f56cd7c3566313318909addb47a4c0` (1,491,735 bytes). Final APK installed successfully on Android 16 / API 36 and Android 15 / API 35 official AOSP x86_64 emulators. API 35 installation upgraded existing v0.01 data. No physical phone was modified.

## Native functional results — Android 15 / API 35

The actual release WebView/native bridge and installed agent were invoked by a separate, same-signed, emulator-only instrumentation APK. Production debugging remained disabled; the probe is not packaged in the release. A labeled local HTTP model fixture served real requests through the installed app's native network implementation. This is not evidence of authentication to a paid live model provider.

All checks passed:

- API key saved encrypted using Android Keystore: preferences contain a 77-character encrypted value and no plaintext fixture key; real model requests include Bearer authentication.
- GET model catalog returns 16 selectable models; nonstream connection probe and ordinary streaming chat complete.
- Selected high reasoning effort appears in outgoing model requests; coding skill instructions appear in the real system prompt.
- Disabled device plugin removes device tools from outgoing schemas. A fixture-forced device tool call receives an explicit `ok:false` refusal and does not execute.
- Cancellation preserves the incomplete streamed reply and excludes the delayed tail.
- Switching provider with a blank key clears the old credential, model, and effort. Built-in OpenAI endpoint ignores a custom endpoint override.
- Force-stop and fresh process restore default model, high effort, coding skill, selected plugins, advanced settings, encrypted credential, and SQLite conversation history.
- Final application crash buffer is empty.

## Android 16 installation scope

The exact previously delivered v0.01 APK (`ed0f0c5837310319b9cf6512d2eb7bdbcc45d72076bd90f6c97645f961e2d1d9`) and final v0.02 APK both installed successfully on API 36. The Samsung S24 Ultra installation error was not reproduced. ADB installation does not cover Samsung's unknown-source policies or every Files/package-installer path.

The API 36 emulator displayed an OS **System UI isn't responding** dialog with Hermes uninstalled and Files open, even with 4 GB RAM. Android 16 evidence is therefore limited to successful APK installation; new native feature tests ran on API 35. API 35 briefly showed the same OS dialog; it was dismissed before final clean model/skills popup screenshots. This is not counted as a Hermes crash or hidden as a passing OS reliability test.

## Evidence

- [Machine-readable final report](android-v002/release-report.json)
- [Native checks](android-v002/native-checks.json), [restart snapshot](android-v002/restart-snapshot.json)
- [Actual HTTP fixture requests](android-v002/api-fixture-requests.jsonl) (credential values omitted)
- [API 36 final install](android-v002/final-api36-install.txt), [API 35 final install](android-v002/final-api35-install.txt)
- [Settings home](android-v002/settings-home.png), [provider/API settings](android-v002/provider-first.png)
- [Actual model popup](android-v002/models-popup.png), [actual skills popup](android-v002/skills-popup.png)
- [Composer focus](android-v002/keyboard-composer.png)

Screenshots are captured from the installed Android application; fixture names are intentional test data. Composer focus screenshot did not display the IME; keyboard geometry was not revalidated in v0.02. Model/skills popups and settings screenshots are clean after dismissing the OS dialog. No horizontal overflow was detected at the actual 412 CSS-pixel viewport.
