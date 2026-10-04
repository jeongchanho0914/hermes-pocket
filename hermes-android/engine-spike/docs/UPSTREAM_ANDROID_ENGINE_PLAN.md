# Original Hermes engine on Android: concrete route and evidence

Pinned original engine: `d795726f78e532ca31655f74656b4be63a907581`.
This investigation changes only the experiment and this plan. It does not change
the production APK, install an APK, read credentials, or change the S24.

## Fastest viable route

Keep the original `run_agent.AIAgent`, original registry/schema discovery,
original tools, original skill files and original provider adapters. Embed
CPython 3.14 with Chaquopy 17 / AGP 8.7.3, and attach native Android tools through
the existing Java bridge. Python and ordinary tool execution stay on the phone;
only configured model/service APIs need remote servers. This is already a real
engine route: the previous S24 fixture executed the original AIAgent, original
memory and skill tool dispatch, and returned the fixture model's final answer.
That evidence does not establish the other tools or all skill scripts.

The immediate static blocker is the four WebP libraries. The existing audit
incorrectly treats every non-16K RELRO endpoint as a runtime failure.

## Corrected RELRO interpretation

The [Android guide](https://developer.android.com/guide/practices/page-sizes)
gives a conservative endpoint rule. The actual [AOSP Android 16 QPR2 linker](https://android.googlesource.com/platform/bionic/+/android16-qpr2-release/linker/linker_phdr_16kib_compat.cpp#427)
checks partial RELRO prefixes and explicitly exempts a RELRO that covers the
whole LOAD segment. Rounding up this entire read-only segment does not protect
live writable data if the next LOAD starts after the rounded boundary.

All 88 endpoint flags in the original 117-library APK are whole-LOAD RELRO.
86 also pass the experiment's safe-padding check. The remaining two are among
the four real 4K-aligned WebP libraries, whose adjacent LOAD layout is already
incompatible. Chaquopy JNI, Python core, Pydantic Core and jiter are not shown to
need rebuilds by these RELRO endpoint flags. The production audit has been left
untouched; `../exploratory/aosp_16k_layout_audit.py` records the corrected scoped
interpretation rather than editing ELF headers or suppressing a warning.

## Isolated candidate

`../candidates/no-webp/` is a separately built Gradle project with application ID
`dev.chanho.hermes.enginecandidate.nowebp`. It copies the previous genuine engine
probe and preserves the vendored source/dependency license records. It removes
only `PIL/_webp*.so` and `libwebp`, `libwebpmux`, `libwebpdemux`, `libsharpyuv`.
Actual DT_NEEDED inspection confirms no retained native library depends on these
four libraries; the sole Python extension consumer is `PIL/_webp`.

PNG/JPEG, Pillow core, HEIF, TLS, cryptography, Pydantic and jiter stay packaged.
This is a diagnostic candidate, **not** the all-feature deliverable. WebP must be
restored from a genuine 16K-built upstream source/library before claiming full
image support. Native Pillow import and PNG/JPEG/HEIF operations still require
actual Android execution. Pillow treats WebP as an optional plugin backend;
its loss is not proof that unrelated original tool modules fail to import.

The initial no-WebP APK builds successfully with no network and contains 112
ELF objects: zero LOAD failures, 84 safe whole-LOAD RELRO endpoint flags, zero
layout-review flags. `zipalign -c -P 16 4` succeeds. A later candidate revision
adds one genuine standalone Python executable and an explicit child probe; see
the final `../exploratory/no-webp-candidate-layout.json` for its actual totals.
There has been no Android execution of this new candidate.

## Dependency closure

The existing `dependencies/native-core-closure.json` resolves genuine Android
CPython3.14 extensions for Pydantic Core, jiter, cryptography, psutil, CFFI and
ruamel YAML. Their non-system closure is `libandroid-support.so`, `libcrypto.so.3`,
`libffi.so`, `libssl.so.3` plus Chaquopy Python/SDK libraries. No dependency in
that text/memory/skills closure is unresolved. The broader candidate retains
all other previously packaged image/document/native dependencies except WebP.
Pure-Python source is taken from the pinned original, not substituted fake
modules. Import registration is not execution proof: upstream discovery catches
module import exceptions and can silently expose a reduced registry. The
minimal host-only test under `../exploratory/host-registry-import.json` exposes
71 tools because its host environment lacks requests/httpx; it is not the
candidate's Android inventory and cannot establish its registry count.

## Python scripts and original execute_code

Embedding alone does not supply a spawnable interpreter. Original
`tools/code_execution_env.py::_resolve_child_python` returns `sys.executable`
in strict mode; original `tools/code_kernel.py` starts that path with
`subprocess.Popen([child_python, hermes_kernel_runner.py], ...)`. Many original
skills also invoke `python` scripts and additional executables. A passing
memory/skill fixture does not prove those child processes.

A concrete compiled launcher already exists in the verified signed Termux
payload: `tools/python/data/data/com.termux/files/usr/bin/python3.14`. It is a
16K-aligned AArch64 PIE, uses `/system/bin/linker64`, and requires only
`libandroid-support.so`, `libpython3.14.so`, and system `libc.so`. Its sole CPython
entry point `Py_BytesMain` is exported by the actual Chaquopy libpython3.14.
The candidate packages this unchanged executable as `libhermes_python.so` in
JNI libs. Android installation extracts it into `nativeLibraryDir`; it is not
executed from writable app files. [Android target29+ restrictions](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)
forbid execve of binaries in the writable app home directory.

The isolated candidate's `standalone_probe.py` unpacks the real packaged stdlib,
sets a private PYTHONHOME/PYTHONPATH and LD_LIBRARY_PATH, and attempts the native
launcher with JSON/sqlite imports and a `/system/bin/sh` child. It clears child
credentials. This probe is compiled into the candidate, **not executed yet**.
Termux's baked-in RUNPATH points to its own package, so explicit library search
and correct CPython stdlib initialization are required. A dynamic-linking
symbol match alone does not prove startup.

Next actual proof must cover: launcher startup, original generated kernel
runner, persistent two-cell state, timeout/cancellation, authenticated local
RPC back to original tools, and a representative original skill script. The
host must then select this launcher and give child processes an extracted real
package path, instead of Chaquopy-only import hooks. Exposing execute_code before
these tests pass would be misleading. In-process exec is not an equivalent
replacement: it loses the original killable subprocess/RPC security envelope.

The original POSIX shell resolver can accept `SHELL=/system/bin/sh`; its plain
command path runs `[shell, -l?, -c, command]`. This can serve basic terminal/file
operations under the app UID after execution tests. It is not full GNU bash,
a Linux filesystem, unrestricted /proc, PTY support, root, or every skill
script's toolchain. Additional genuine binaries (git, ripgrep, bash, ffmpeg,
node etc.) must be packaged and tested, with relocatable paths and the same
16K/load/ZIP audit. A local Termux service is a possible on-phone alternative
for the original CLI environment, but an unrelated Termux sandbox cannot be
accessed directly and needs an authorized service protocol and lifecycle.

## Acceptance before production integration

1. Restore genuine 16K WebP builds, retain license/source provenance, and audit
   the complete APK including native extensions inside nested Chaquopy assets.
2. Execute candidate on an actual 16K Android environment with compatibility
   mode disabled; capture actual page size and linker/import outcomes.
3. Capture original full-registry discovery outcomes, check_fn availability,
   and actual tool dispatch independently. API credentials and native permissions
   gate many of the 107 tools; a count of schemas is not 107 working tools.
4. Execute original code kernel and representative skill scripts as above.
5. Verify the native Android bridge's independent approvals, native tools,
   model/provider settings, session persistence, cancellation and lifecycle.
6. Test each platform-dependent original feature honestly: desktop UI drivers,
   Docker/system services/Linux-only packages and unrestricted PTY/root cannot
   be created by copying Python sources. Android adapters or supported local
   backends need actual implementation and execution before availability claims.

Use NDK r28+ for the eventual source rebuild (including WebP). On older supported
NDK toolchains apply both max-page-size and common-page-size linker options;
Gradle settings do not retroactively rebuild precompiled dependencies. Neither
this plan nor a layout audit is a final all-feature Android execution claim.

## Genuine WebP restoration completed (isolated build)

Official NDK30 was fetched from Android's published download URL and verified
against official SHA1 `5107f898313790e449e87eee2183d9a20602dee9`; local SHA256 is
`753611f410d002cfcd3f3dc2ef49aad532089d3180b436c060a90bf0fcb64df2`.
Pinned genuine libwebp1.6.0 source SHA256 is
`93a852c2b3efafee3723efd4636de855b46f9fe1efddd607e1f42f60fc8f2136`,
matching the official Termux recipe. Source `COPYING`, `PATENTS`, and `AUTHORS`
are retained; the restored candidate also packages their notices.

`exploratory/webp-source/CMakeLists.txt` uses original codec source unchanged.
It selects only codec libraries, links with both16K options, and clears
VERSION/SOVERSION at source build time to match retained Pillow's unversioned
DT_NEEDED entries. This does not alter binary ELF alignment headers. Four new
arm64 libraries compile successfully with two jobs using NDK30. All four pass
LOAD16K and strict RELRO16K endpoint checks; all32 required WebP symbols from
the original Pillow extension exist. Their closure contains only the restored
WebP libraries plus system libc/libm/libdl. See
`exploratory/webp-restoration-audit.json` and source/download provenance files.

`candidates/full-webp/` restores the original genuine Pillow `_webp` extension
and these source-built libraries. It adds PNG/JPEG/WebP and animatedWebP
roundtrip probes, original registry inventory, and the original execute_code
RPC/persistent-state probe. App ID is
`dev.chanho.hermes.enginecandidate.fullwebp`. This is a restored codec candidate,
not proof of all features. It has not executed on Android.

The exclusive verifier reported actual API35 emulator properties:
`ro.product.cpu.abilist=x86_64`, `ro.dalvik.vm.native.bridge=0`. Consequently
the arm64 original-engine candidate is concretely ABI-blocked on that emulator.
No candidate install was attempted, and no physical-device launch was allowed.
A compatible authorized arm64 environment or a genuine x86 dependency build
is required for the pending execution proof.

Final restored candidate audit:118 ELF objects; zeroLOAD failures; all86
nonaligned RELRO endpoints are whole-LOAD safe padding; zero remaining layout
review flags. Complete recursive layout check and `zipalign -c -P 16 4` pass.
APK SHA256 is
`43e6a835a7755cde5ec1c0d0df7feefd32c073550853ab8c048dbda755764693`.
Rebuild codec libraries with `exploratory/build_webp_16k.sh`; packaged candidate
metadata is `candidates/full-webp/artifact-metadata.json`. No production source,
main APK, physical-device state, or user credentials were changed.
