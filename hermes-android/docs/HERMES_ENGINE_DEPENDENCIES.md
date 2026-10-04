# Genuine Hermes engine dependency spike

This directory is an isolated feasibility experiment. None of its Python or native
libraries is included in the production Hermes Pocket APK yet.

## Source and runtime

- Upstream source pinned to `d795726f78e532ca31655f74656b4be63a907581`:
  https://github.com/NousResearch/hermes-agent/tree/d795726f78e532ca31655f74656b4be63a907581
- Current upstream runtime dependencies target CPython 3.14. Older interpreter
  acceptance in package metadata exists for updater compatibility.
- Chaquopy 17 supports CPython 3.14 and Android API 24+:
  https://chaquo.com/chaquopy/doc/current/versions.html
- Exact upstream files, pin metadata, source archive and unmodified source copies
  are under `engine-spike/dependencies/`. The `vendor-minimal` name is historical:
  its closure expanded to the genuine Python packages needed by lazy imports.
  No fake OpenAI/Pydantic/native module is supplied.

## Default package channels

Cross-target `pip download --only-binary=:all:` against PyPI and the official
Chaquopy native index, CPython 3.14 / Android 24 / arm64, fails for:

| Requirement | Observed result |
| --- | --- |
| OpenAI 2.24.0 | Dependency `jiter>=0.10.0,<1` has no matching wheel |
| Pydantic 2.13.4 | `pydantic-core==2.46.4` has no matching wheel |
| cryptography 50.0.1 | No matching wheel |
| Pillow 12.3.0 | No matching wheel |
| httptools 0.8.0 | No matching wheel |
| watchfiles 1.3.0 | No matching wheel |
| resvg-py 0.4.0 | Android arm64 abi3 wheel resolves successfully |

These results are specific to those package channels. They do **not** mean an
Android port is impossible. The complete logs are in `dependencies/pip-*.log`.

## Verified upstream Android package alternative

The official Hermes Termux canary repository contains a signed Android arm64
package. Its authenticated Release, package hash and static extraction are owned
by the sibling `engine-spike/termux-assets/` audit.

The extracted site-packages contain actual CPython 3.14 Android extensions for
Pydantic Core 2.46.4, jiter, cryptography 50.0.1, Pillow 12.3.0 and other native
requirements. This is a real alternate dependency source, not a renamed Linux
wheel. Stable repository requests returned HTTP 404 in this session.

`dependencies/termux-native-dependencies.json` records ELF DT_NEEDED and RUNPATH.
Pydantic Core and jiter require `libpython3.14.so`, `libdl.so`, and Android `libc.so`.
They have absolute Termux RUNPATHs. Other extensions need additional packaged
OpenSSL, libffi, libandroid-support, libheif and codec libraries. These must be
resolved inside the APK namespace; installing Termux is not required for this
experiment. Android execution of the native extensions is the next verification
step; matching filenames and ELF headers alone do not establish ABI compatibility.

## Genuine component execution

The host CPython 3.14 probe executes the unmodified upstream implementation:

- IterationBudget: consumption limit and refund.
- SessionDB: actual SQLite session creation, message append and retrieval.
- Skill metadata: actual YAML frontmatter parsing.
- MemoryStore: actual protected file write, new instance reload and restored entry.

All four pass on the host with only `ruamel.yaml==0.18.16` installed.
`dependencies/host-component-probe.json` stores results. This proves source
closure and behavior, **not** Android execution or whole-agent feature parity.
The separate Chaquopy APK tests the same calls on Android. Full `AIAgent` import,
real provider roundtrip, web API tools, subprocess tools, Android lifecycle and
background scheduling remain separate gates before integrating an engine.

## Constraints that packaging cannot remove

- Android application sandbox and permissions still apply to genuine Hermes code.
- Root-only device actions require root; a Python engine does not grant it.
- Desktop browser/process/terminal providers need Android-specific executable and
  permission adapters. Local file tools must use app-private or user-granted paths.
- Features with external backends still need their respective API credentials.
- Cron jobs need Android lifecycle-aware scheduling; a Python timer alone is not
  reliable when the application is backgrounded or killed.

## Prepared native engine dependency source

`dependencies/vendor-engine/` contains 4,210 original files (56.8 MB) copied from
that authenticated Termux package's site-packages, with `.pyc`/cache files omitted.
Native extensions, distribution metadata and license files are preserved. It is
intended solely for the Android probe's arm64 nativeCore variant. The accompanying
`termux-packaged-versions.json` makes packaged versions inspectable: OpenAI 2.24.0,
Pydantic 2.13.4/Core 2.46.4, jiter 0.16.0, cryptography 50.0.1, httpx 0.28.1,
python-dotenv 1.2.2 and truststore 0.10.4. The authenticated package has ruamel.yaml
0.18.17 and psutil 8.0.0; these differ from the current upstream declaration and
must be reviewed explicitly instead of being described as an exact lock closure.

The source copies are intentionally not production assets. Dependencies that
load, imports that succeed and features that actually execute are reported as
separate milestones. Keep private model credentials out of probe logs.

## Android import milestone and agent-loop fixture

The separate engine APK has now executed on the connected Galaxy S24 Ultra:
`run_agent.AIAgent` imports successfully, actual Pydantic/Core/jiter validation
passes, and all four genuine upstream component probes pass. The physical proof
is recorded by the coordinating agent in `engine/evidence/s24-engine-first.json`.
This removes the import/ABI blocker; it does not yet prove every tool backend.

`dependencies/engine_fixture.py` provides the next executable gate. Its only fake
is a deterministic model API listening on the phone's own `127.0.0.1` interface.
The original AIAgent and original tool registry implementations are unchanged.
The API sends structured `memory` and `skill_view` tool calls followed by a final
response. The probe checks request schemas, real persisted memory, SQLite session
messages and tool-result roundtrips. It uses a synthetic localhost credential and
a separate app-private `fixture-hermes` directory. It neither reads production
API keys nor calls a paid provider. Runtime budget, iteration bound and server
cleanup are included. Android execution of this next gate remains to be recorded.

The corrected physical AIAgent model/tool loop now passes: genuine memory write,
skill content retrieval, SQLite message persistence and three HTTP model rounds.
See `engine-spike/evidence/s24-engine-conversation-fixed.json`. The separate native
APK still has a 16 KiB compatibility gate; see `ANDROID_NATIVE_16K_AUDIT.md` for
actual ELF/RELRO checks and rebuild requirements. A 4 KiB Galaxy execution proves
behavior but must not be reported as passing a 16 KiB-device compatibility test.
