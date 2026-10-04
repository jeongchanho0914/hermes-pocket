# v0.01 Android verification

Verified on the official Android 15 / API 35 default x86_64 system image, revision 2, using official Android Emulator 37.2.12 with KVM. The installed app used Android System WebView 124.0.6367.219. No physical phone was connected or modified.

Final APK tested: `dist/hermes-pocket-v0.01.apk`, SHA-256 `ed0f0c5837310319b9cf6512d2eb7bdbcc45d72076bd90f6c97645f961e2d1d9`; installed version name `0.01`, code `1`, min SDK 26, target SDK 35.

The model endpoint was a local HTTP fixture through `adb reverse`, exercising the installed APK's real native networking, agent loop, Android tool calls, foreground service, SQLite and Android Keystore. This does **not** establish compatibility with every commercial provider or constitute a live commercial model test.

| Check | Result / evidence |
| --- | --- |
| Install, start and signed upgrade preserving data | Passed; API endpoint/model and encrypted credential retained across rebuild installs |
| Save API key using actual settings UI | Passed; preferences contain AES-GCM ciphertext and no plaintext fixture credential |
| API connection test | Passed; actual GET models + non-stream model inference request and native UI confirmation |
| Fast streamed chat | Three consecutive fast chats on corrected runtime; further fast chats after Android UI updates; final APK `FinalApkCheck` completed and remained alive beyond the previous crash timeout |
| Native tool loop | Fixture requested `get_device_state`; APK read actual emulator battery, memory, Android API, volume and returned its tool result in the next API request |
| Stop during stream | Partial response retained with incomplete marker; cancellation note saved; delayed tail absent; agent foreground service torn down |
| SQLite lifecycle | Force-stop/relaunch, open drawer and reopen saved conversation passed; final store contains five sessions and nineteen messages |
| Native approval deny | Media volume stayed 5/15 after rejecting the 49% change dialog |
| Native approval allow | Once-only approval for 50% changed actual AudioManager media volume from 5/15 to 8/15 (integer rounding) |
| System bars, themes and keyboard | Light Paper and dark Midnight use matching top background; Paper status icons have correct dark appearance; composer remains above real soft keyboard with header visible |
| Runtime crash log | Final APK fast-chat check produced no AndroidRuntime crash entries |

An initial installed build exposed a real `ForegroundServiceDidNotStartInTimeException` after fast model completion. The service now enters foreground immediately and the worker waits for the foreground readiness handshake before inference. Repeated emulator runs verified the correction. The initial crash log is retained as diagnostic evidence, not as a failure of the final APK.

Machine-readable results: [release-report.json](android-verification/release-report.json). Important screenshots: [final chat](android-verification/final-release-chat.png), [Paper keyboard](android-verification/insets-paper-keyboard.png), [Midnight](android-verification/insets-midnight.png), [native approval](android-verification/release-volume-approval.png), [cancelled partial](android-verification/final-cancelled.png), [restored history](android-verification/final-history-restored.png), [API connection](android-verification/connection-success.png).

Limitations: one Android 15 emulator, no physical-phone validation, no real commercial model request, and no full Root/accessibility/manufacturer-specific permissions test. All thirteen tool schemas being available does not mean every privileged tool was exercised on hardware.

Reproduction helpers: `tests/android_fixture.py` (local model API fixture), `tests/android_ui.py` (ADB helper restricted to `emulator-` serials). Emulator packages and AVD are isolated in `.toolchain/android-test`; SHA-1 checks matched official Google repository metadata.
