# Hermes Pocket v0.10 verification

Current candidate: `dist/hermes-pocket-v0.10.apk`, 7,026,183 bytes; SHA-256 `c191bfbfb1bb8b0f77d9fc6d3939b93918ab56805a6cc14ba0c417382da1b923`. Build signatures, ZIP integrity, manifest, resource-table alignment and source-freeze checks passed. The exact candidate passed the native touch and database checks described below.

## Host evidence

122 production JVM checks passed in `/tmp/hermes-v010-jvm-final.log`. This includes real shell process execution, approved skill operations, safe package import/export, public activity metadata/privacy and original packaged skill assets. All 210 original skill documents match source hashes; the 209 eligible packages coexist in one host store without activation. One original resource exceeds the unchanged 1 MiB file cap and rejects explicitly. Installed skill capacity is 256.

Python coverage: 101 cases (86 GUI and 15 transport/version). The initial full discovery of 98 found a real terminal stop-acknowledgement race and a sidebar test retry timing race. After the production stop fix and explicit test response wait, the final affected suite passed 9/9 (five terminal, one sidebar, three new library cases). Unaffected checks were not redundantly rerun. Nine targeted package/activity tests passed, covering approval-to-completion without duplicate cards, session-specific reload, unsafe field exclusion, historical unfinished status, real operation acknowledgements and draft preservation.

Host checks do not prove Android permissions, native SQLite migration, touch delivery or provider intelligence. Preview images `android-v010/chat-activity-preview-white.png` and `chat-activity-preview-black.png` use labeled test responses in the production browser DOM.

## Native status

The first v0.10 candidate (SHA-256 `252ce0e35bfa128d59ebba18f4bb04c8b75a946b34c235361bfabe0cae66c9e0`, 7,026,183 bytes) installed on the Galaxy S24 Ultra / Android 16 with `adb install -r`, preserving app data. The installed APK was pulled and its hash verified. The root did not launch over the owner's other app or claim functional phone UI verification. Evidence: `android-v010/s24-install.json`.

The preceding v0.09 build passed actual app-terminal 13/13 and Shizuku-terminal 7/7 checks on API 35. Its gesture callback completed, but independent delayed XML and an actual screenshot showed the fixture counter still zero. This is an actual touch-delivery failure, not merely stale accessibility output. The final candidate adds actual bounded node refresh and input-window settlement before one gesture. Actual window pixels were checked, a single tap changed the counter to 1, and independent immediate/+200ms/+300ms XML all confirmed 1 before any app switch. Evidence: `android-v010/positive-independent-counter.json`, `android-v010/independent-delayed-counter.json`. The earlier failed candidate remains documented in `android-v009/independent-delayed-counter.json`.

Final API 35 native evidence (all exact APK hash above):

- SQLite schema 1→2 migration preserved 21 sessions, 55 messages, 21 transcripts and 235 audit entries plus explicit baseline markers. Tool activity updates retain first order/time, filter unsupported fields and isolate sessions. A separate process reopened the persisted records, and the actual WebView displayed them. `android-v010/database-migration-and-timeline-native.json`, `database-process-reopen-native.json`, `actual-agent-timeline-process-reopen.json`.
- Native original catalog, document import, non-overwrite/non-activation and size refusal: 11 checks passed. Original antigravity-cli document/resource import and exact bytes: 12 checks passed. `android-v010/skill-library-and-native-timeline.json`, `skill-original-support-final.json`.
- Background terminal lifecycle: 10 checks passed, including actual app UID, independently observed foreground service, same process retention across quick chats, popup Stop cancellation, repeated foreground jobs and plugin-revoke cancellation. `android-v010/terminal-background-final.json`.

The final APK was privately uploaded to owner-only Drive and its download link sent to the owner's Gmail. `android-v010/delivery.json`. A subsequent automatic physical upgrade was deferred while an owner task was active; no active phone task was interrupted.

## Upstream scope

The APK implements a native Java tool loop and actual Android tools. The complete upstream Python engine and all 107 original tool implementations are not established in production. Original skill inclusion does not supply absent Python, PTY, browser, scheduler, gateway or desktop runtimes. See `HERMES_FEATURE_MATRIX.md` for exact source-backed status.

An isolated original-engine candidate and genuine 16 KiB WebP restoration are under `engine-spike/`. The available API 35 emulator is x86_64 with no ARM translation; the engine candidate is arm64. No original-engine Android runtime success is claimed for that candidate.
