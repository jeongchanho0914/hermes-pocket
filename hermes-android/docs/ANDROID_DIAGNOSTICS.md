# Android diagnostics and release review

Hermes Pocket collects failure **metadata** in the app's private storage. This supports a developer's next-release fix cycle; the installed APK cannot rewrite its own code or guarantee an automatic fix.

## Native capture

`PocketApplication.attachBaseContext` installs `Diagnostics.initialize` before activities/services initialize. The manifest must use `android:name=".PocketApplication"`. The existing default uncaught exception handler is always delegated after the best-effort save; diagnostics never swallows the original crash. Concurrent crash collection is guarded against recursion, and waiting for the storage lock is limited to 150 ms. Writes contain at most 256 KiB. Android or filesystem failure can still prevent persistence; this is not a guarantee that every crash is recorded.

Caught errors call `Diagnostics.record(context, phase, throwable)`. Prefer existing worker threads for caught failures because durable writes are synchronous. Approved phases: `startup`, `runtime`, `model_connection`, `model_stream`, `tool`, `bridge`, `ui`, `overlay`, `accessibility`, `screen_capture`, `terminal`, `skills`, `storage`, `other`. Unknown phases become `other`; free-form phase text is not saved.

The stable developer collector path is:

```
files/diagnostics/events.jsonl
```

Persistence uses Android `AtomicFile` under `Context.getFilesDir()`. Readers recover the previous atomic backup if a write was interrupted. Locking protects threads in the primary app process; concurrent external process writers are not supported. No service or tool may write this file directly.

## Record contract and privacy

Each JSONL record contains only:

```
schema, id, versionCode, versionName, phase, type,
firstSeen, lastSeen, count,
exceptions: [{class, frames: [{class, method, source, line}]}]
```

`type` is `caught` or `uncaught`. At most four cause classes and twelve stack frames per cause are retained. Stack identifiers are restricted and bounded. No exception messages, API keys, model requests/responses, HTTP bodies, headers, prompts, chat text, private reasoning, screenshots, tool arguments, command output or thread names are recorded. Reloaded records are reconstructed using the same field whitelist, dropping extra fields and malformed lines.

`id` is SHA-256 over the installed version code, approved phase, failure type, exception classes and bounded frames. Repeated identical failures aggregate `count`, preserve `firstSeen`, update `lastSeen`, and move to the newest position. The newest 256 entries are retained within a 256 KiB byte cap. Crash type and caught type have different fingerprints. Upgrading the APK preserves old records and tags new records with the new installed version.

## Review before a new APK

The local build collector must pin the physical device serial and retrieve **only** the diagnostics file using the same app owner's `run-as`. It must not extract preferences, databases, model credentials or logcat. A non-debuggable package may reject `run-as`; the collector must report this explicitly rather than treating it as an empty clean report.

`Diagnostics.snapshot(context)` returns sanitized entries. `Diagnostics.acknowledge(context, ids)` deletes only explicitly supplied exact fingerprint IDs. Acknowledgement belongs to a trusted developer/debug path after the underlying fix and regression check have been verified. It must not be exposed as a model tool or auto-triggered on startup, build, APK installation or collection. Collection alone does not prove a problem is fixed.

The developer reads the pending report, diagnoses and fixes the matching code, tests the fix, and acknowledges only verified fingerprints. A newly reported error or a failure in the new version remains pending for the next cycle. Do not claim an unattended build script can independently understand and fix arbitrary runtime failures.

## Evidence and required device proof

On 2026-10-04, the new classes compiled with the Android 35 SDK using Java 8 source/target. An isolated temporary JVM harness passed eleven checks using real temporary files and Android stubs: deduplication/count, exclusion of secret-marked exception messages, frame metadata, whitelisted reload of injected extra fields/corrupt lines, unknown phase handling, exact acknowledgement, both storage caps, previous uncaught-handler delegation, and persisted uncaught type. The `AtomicFile` host stub does not prove Android recovery/durability or manifest startup wiring.

Before release, the test owner must additionally confirm on the exact built APK: the application subclass loads, a caught fixture failure persists across restart, uncaught fixture metadata is recorded without suppressing termination, APK update preserves the prior report/version, collector reads only the stable diagnostics path, and unrelated fingerprints survive explicit acknowledgement. Do not intentionally crash a user's active conversation; use the dedicated test fixture/emulator.

## Native crashes and ANRs

On API 30+, a startup worker queries `ActivityManager.getHistoricalProcessExitReasons` for **this package only**, with a maximum of five results. Only `REASON_CRASH` (4), `REASON_CRASH_NATIVE` (5), and `REASON_ANR` (6) are persisted as `type: process_exit`; low-memory, update, user-requested exits and other reasons are ignored. No exit description, ANR/native trace stream, process-state summary or other app's history is read. Android API 26–29 has no supported historical exit capture through this implementation. The platform may not retain every exit.

These records contain `exitReason`, `exitTimestamp`, `pid`, and `versionMeaning: observed_at_startup` instead of invented exception frames. The public installed version belongs to the **collection startup**, because `ApplicationExitInfo` does not expose the version of the historical crashed APK. Never attribute that exit to the collection version as a proven faulty build. The fingerprint binds exit reason/time/PID without collection version, so repeated startups or an upgrade do not multiply the same retained OS event or inflate its count. Explicit acknowledgement of a retained OS event may allow the OS history to report it again at a later startup; acknowledgement needs a historical-event tombstone mechanism before such entries can be permanently retired. Until that mechanism is provided, reviewed release-ledger IDs should suppress re-review rather than claiming native history has been erased.

The platform contracts are documented in [ActivityManager](https://developer.android.com/reference/android/app/ActivityManager#getHistoricalProcessExitReasons(java.lang.String,%20int,%20int)) and [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo). SDK 35 compilation proves API symbols are valid; the dedicated fixture must still prove actual OS history retrieval and native crash/ANR retention.

## Release APK collection endpoint and browser failures

Release builds cannot rely on `run-as`. `DiagnosticsProvider` offers exactly `content://dev.chanho.hermes.diagnostics/events` as a bounded sanitized JSONL stream through a pipe. The manifest requires `android.permission.DUMP` for external reads; code independently permits only the app's own UID, root UID 0, and Android shell UID 2000. Normal applications, other paths (including query parameters/fragments), write modes, insert/update/delete and arbitrary queries are denied. It never accepts a file path, exposes raw diagnostic storage, preferences or databases, or grants URI permissions. The trusted local collector may use pinned-device `adb shell content read --uri content://dev.chanho.hermes.diagnostics/events`; this adds no network server.

The earlier `run-as`-only guidance remains a debug fallback; lack of `run-as` in a release package is not a clean report when the provider is available. Permission and caller denial, bounded output and exact manifest wiring require native tests on the release-shaped APK.

Browser failures use `Diagnostics.recordBrowserProblem(context, code, source, line, column)` without a JavaScript message or stack. Only `webview_error` and `unhandled_rejection` codes are accepted; source is `app.js`, `index.html` or `unknown`. Numeric locations are bounded to 0–10000000 (the native bridge may impose a stricter bound). `browser_error` records carry this metadata instead of exception frames, aggregate the same installed-version/code/location fingerprint, and are sanitized again before export. Promise rejection contents are deliberately not inspected. Browser event metadata cannot identify all asynchronous errors or guarantee a complete diagnosis.

The provider uses strict `Diagnostics.snapshotForExport`: unavailable storage, malformed entries/lines and reports over the byte/record bounds cause a visible read failure. It never converts those failures into a clean empty export. The ordinary in-app `snapshot` remains best effort for UI resilience. Extra fields on otherwise valid entries are still dropped by the export whitelist.
