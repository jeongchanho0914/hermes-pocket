# Actual Android v0.06 verification

Release APK: `dist/hermes-pocket-v0.06.apk`, 1,528,599 bytes; SHA256 `0abca6c515bb0a7fa82f296ae9b7d8046672ecd050c90e49840c99261792a06b`.

Tested on Android 15/API35 x86_64 emulator `emulator-5554`. All helpers were separate test-only APKs, absent from the release; public fixture text only. No physical phone commands were performed by this verifier.

## Passed

- Actual input tap opened the Android keyboard; new chat hid it, cleared the composer and left input unfocused.
- Actual Android main-thread queued callback was canceled before delivery after caller interruption and after runtime stop; counter remained zero. A subsequent real read-only local tool settled.
- Actual DocumentsUI selected a subfolder and granted persisted READ/WRITE access. File plugin enabled after selection; native tool registry became 27.
- Actual native approval denial created no file. Approved UTF-8 creation, readback and explicit overwrite verified content bytes. Existing-file overwrite without the flag, traversal, absolute path, content URI, backslash and binary extension were refused.
- Cold process restart retained the folder grant and Korean UTF-8 content. Native revoke removed usable permission and further read was explicitly refused.
- Actual accessibility service bound with capabilities 33. Independent allowlisted fixture app launched; real snapshot and gesture availability were reported, password subtree was redacted, element click incremented the visible counter, reuse of stale snapshot was refused, and normal text was actually typed and externally observed.

## Limits and unfinished checks

The complete coordinate tap/swipe/back/home sequence has not passed. Background approval uses a notification action, and the notification shade remains expanded after approval. A subsequent screen read can race the tester collapsing it and correctly refuses protected SystemUI. This concrete result is retained in `accessibility-loop.json`; it is not recorded as full phone-control success.

Stock `uiautomator dump` temporarily suppresses other accessibility services. The test harness was changed to a persistent `UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES` dump; this resolved the earlier false service-loss test failure without production changes.

ExternalStorageProvider selected text/plain MIME and returned `hello-006.md.txt`; tests followed the returned path. Read-only providers, duplicate names and concurrent overwrite conflicts were not tested. Live paid API/Tavily calls were not tested.

Machine-readable scope and artifact identity are in [android-v006/release-report.json](android-v006/release-report.json). Detailed native results and actual approval decisions are stored alongside it.
