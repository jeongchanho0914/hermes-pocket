# Automatic failure review before APK builds

Hermes stores bounded failure metadata in its own private `files/diagnostics/events.jsonl`: at most 256 records and 256 KiB. Upgrading the APK preserves these records. Exception messages, conversation text, model reasoning, tool arguments/results, API keys, screenshots, URLs, clipboard content and other apps' data are excluded. Records contain app version, phase, exception class and bounded source frames, timestamps and occurrence count. The native helper records caught failures and uncaught crashes without replacing Android's original crash handler. WebView script failures and unhandled rejections retain only a fixed error code, an app.js/index.html/unknown source label and bounded line/column numbers; no JavaScript message or stack is collected.

On Android 11 and later, startup also checks **this package's** recent OS process exits. Only Java crashes, native crashes and ANRs are retained. OS descriptions and trace streams are never read. The OS does not report which historical APK produced an exit: `versionMeaning: observed_at_startup` names the version collecting it, not the version that crashed. An OS exit record contains the exit reason, timestamp and process ID, without invented stack frames.

The authorized local device is configured in the ignored `.toolchain/diagnostics-device.json`:

```json
{"serial": "R3CX20QTRPD"}
```

`python3 scripts/build.py` automatically uses the pinned `.toolchain/android-test/platform-tools/adb` and that exact physical serial. Its only device operation runs `shell content read --uri content://dev.chanho.hermes.diagnostics/events`. The release app's read-only provider requires Android's `DUMP` permission and a caller UID of shell, root or the app itself; it returns only bounded sanitized metadata. `run-as` is not used because production APKs remain non-debuggable. The collector rejects output exceeding 256 KiB. It does not read preferences, databases, logcat or other applications; it does not launch, stop, uninstall or change the phone. It never restarts the ADB server. Collection fails visibly on connection or permission failure. An older APK without the diagnostics provider is recorded as `not_available`; that is not evidence of an error-free run.

Every sanitized collection is saved under `docs/diagnostics/reports/`, tagged with the version being built, and copied to `docs/diagnostics/latest.json`. All reports are considered during preflight, so a later empty snapshot cannot erase an earlier unresolved crash. Raw device output and stderr are not saved. Unknown record fields are stripped; invalid schemas and oversized inputs fail rather than being treated as clean.

## Fix and review

The development agent reads the report, reproduces or assesses the problem, edits the actual source and runs a relevant test before building the next APK. The installed app does **not** generate patches or mark its own errors fixed. Inspect outstanding records with:

```sh
python3 scripts/diagnostics_review.py pending
```

After an actual correction, run the relevant test through the verification command after source edits have finished. This executes only the developer-supplied local argv; diagnostic records never supply executable commands:

```sh
python3 scripts/diagnostics_review.py verify \
  --source app/src/main/java/dev/chanho/hermes/Example.java \
  --output docs/diagnostics/verification/example-test.json \
  -- python3 tests/example_check.py
```

It saves the actual command, successful exit code, source hashes and output hash. A failing test cannot resolve an error. Then review exact IDs:

```sh
python3 scripts/diagnostics_review.py review \
  --id RECORD_SHA256 \
  --resolution fixed \
  --summary 'Describe the actual source correction and resulting behavior.' \
  --source app/src/main/java/dev/chanho/hermes/Example.java \
  --test-command 'python3 tests/example_check.py' \
  --test-evidence docs/diagnostics/verification/example-test.json
```

The example paths are placeholders, not an existing fix. `fixed` and `mitigated` require a concrete explanation, existing app/build source files and a separate structured verification result with matching command, source hashes, successful exit and unchanged test-output log. An arbitrary nonempty text log cannot resolve an error. `external` and `notbug` let a developer accurately classify a caught provider error or intentional permission denial after validation; these cannot resolve an uncaught crash or OS crash/ANR. Review evidence records SHA-256 hashes. Missing/changed evidence or source, or a later occurrence of the same exact ID, reopens the record. A similar fingerprint in a new APK does not silently inherit the previous version's resolution.

The verification runner establishes that the supplied command ran successfully against the recorded source and saved output. A passing test alone cannot establish a causal fix; a developer with filesystem access could also forge evidence. The developer must choose a meaningful check and honestly assess and describe the correction. This is a development workflow, not a tamper-resistant security attestation. The app's explicit native acknowledgement API may remove only reviewed exact IDs; ordinary upgrades and collection never delete records.

An unresolved critical crash or ANR blocks compilation. A deliberate diagnostic development build can pass `--defer-critical-diagnostics 'A concrete reason of at least 20 characters'`; this prints the reason and records it with unresolved IDs in `dist/build-info.json`. It does not mark errors fixed or delete them. Noncritical caught errors remain visible and pending until assessed. No automatic “fixed” ledger entries are generated.
