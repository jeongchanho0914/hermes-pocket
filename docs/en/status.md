# Project status

[← Back to README](../../README.md) · **English** · [한국어](../ko/status.md)

Snapshot of **v0.13 beta**, taken from the v0.13 handoff on 2026-10-04 (Korean original: [handoff/HERMES_WEB_HANDOFF_V013.md](../handoff/HERMES_WEB_HANDOFF_V013.md)).

## Release

| Item | Value |
|---|---|
| Package | `dev.chanho.hermes` |
| Version | v0.13 beta, `versionCode` 13 |
| Android | minSdk 26 (Android 8.0), targetSdk 35 |
| APK size | 7,099,970 bytes |
| APK SHA-256 | `ce76dfbf9835b895e010ca46b02994676e58da03bf58526f26f9574e4ea342ad` |
| Installed on | Galaxy S24 Ultra, updated in place (no uninstall, existing data kept) |
| Verified | Installed hash, size and version match the build; app launches and process runs |

## What works

- Independent background jobs: up to 2 parallel, 8 queued; own HTTP connection, history, cancel and stored result. Model and API costs apply.
- Chat UI, tool loop, approvals, memory, skills, terminal, accessibility control, web search.
- Original Hermes skills: 210 bundled, 209 importable.

## Verification honesty

| Check | Result | Note |
|---|---|---|
| Install, hash, version, launch on S24 | ✅ | Not the same as a full user test |
| Background job / CLI / re-query checks | ✅ 14 passed | |
| Semantic click on emulator | ⚠️ Not verified | Accessibility service not connected, precondition failed |
| Overall native verification `allPassed` | ❌ `false` | Left as-is, not rewritten to success |
| Errors collected after install | 2, both from v0.12 | 0 from v0.13; not a guarantee of error-free long use |

The first run did not pass everything at once. Two tool-count expectations were updated for the new tools, and a missing local-asset allowance for `cli.js` found on real Android was fixed. Failed candidate logs are kept.

## On-device Python engine experiment

A separate test app (`dev.chanho.hermes.enginecandidate.fullwebp`) ran on the S24 with Python 3.14, the original `AIAgent` import, session/memory/skill metadata, Pydantic and PNG/JPEG/WebP checks. 90 upstream schemas registered, which does **not** mean 90 tools run.

Failed: an agent conversation (test server context 32768 is below the upstream minimum of 64000) and standalone code execution (missing `math` extension module). A fix script exists (`scripts/repair_arm_probe_v013.py`) but was **not run**. Python and `execute_code` are therefore **not** in the main APK.

## Not done

- Full test on a real S24 combining remote model API, default-browser result reading, semantic click, long background use and popups.
- Full Python `AIAgent`, `execute_code`/PTY, MCP/plugin execution, cron, gateway, OAuth/failover, voice, image/video generation, full session branching and backup/restore.
- Root: UID 0 was not obtained. Shizuku's Shell UID 2000 is different from root.

## Where to start next

Read `hermes-android/docs/android-v013/PERFORMANCE_ARCHITECTURE.md` and `RELEASE_STATUS.json` first. Prefer verified install and test records over document counts or tool registration.
