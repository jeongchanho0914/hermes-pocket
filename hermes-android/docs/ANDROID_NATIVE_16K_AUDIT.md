# Android native 16 KiB audit

The engine experiment now runs the genuine upstream AIAgent on the Galaxy S24
Ultra. Its actual localhost model → memory tool → skill_view → final response
roundtrip passes (`engine-spike/evidence/s24-engine-conversation-fixed.json`).
That execution occurred on a 4 KiB-page kernel and does not establish 16 KiB
compatibility. The user-reported Android compatibility warning applies to the
separate native engine experiment, not to the Java-only stable v0.05 APK.

## Reproducible inspection

```sh
python3 scripts/native_16k_audit.py APK --output audit.json
```

The read-only script parses ELF program headers directly, checks PT_LOAD
alignment/congruence and the GNU_RELRO end address, and recursively scans native
Python extensions in Chaquopy's nested `.imy` ZIP assets. Checking only the
APK's `lib/` directory would miss these extensions. It also records ZIP payload
alignment separately; ZIP alignment does not repair ELF segments.

Actual engine-probe APK results:

- 117 ELF shared objects, including JNI, extensions and Python stdlib modules.
- Four PT_LOAD failures: `libsharpyuv.so`, `libwebp.so`, `libwebpdemux.so`,
  `libwebpmux.so`. Their load-segment alignment is below 16 KiB.
- 88 GNU_RELRO endpoint failures under the current Android documentation's check.
  These include core libraries whose PT_LOAD segments already use 16 KiB, such
  as Chaquopy JNI, Pydantic Core and jiter. Removing WebP libraries alone cannot
  honestly establish full compatibility.
- `dist/hermes-pocket-v0.05.apk` contains zero native ELF objects and passes this
  structural audit. It still requires normal runtime validation.

JSON evidence: `engine-spike/dependencies/native-16k-probe-audit.json` and
`native-16k-stable-audit.json`.

## Minimal genuine text-engine dependency profile

```sh
python3 scripts/native_engine_closure.py VERIFIED_TERMUX_ROOT --output closure.json
```

The static OpenAI/text/memory/skills native profile includes real Pydantic Core,
jiter, cryptography, psutil, CFFI and ruamel YAML extensions. Their complete
DT_NEEDED closure adds four Termux runtime libraries: `libandroid-support.so`,
`libcrypto.so.3`, `libffi.so`, `libssl.so.3`, alongside the Python SDK and Android
system libraries. No dependency in that profile is unresolved.

This excludes HEIF/WebP/image/document extras and is **not** a full-feature Hermes
package. Original implementations remain intact. A build may omit an optional
backend only if its corresponding capability is disabled honestly; it must not
advertise an image codec or document parser whose native dependency was removed.
PNG/JPEG/Pillow need their own actual dependency profile and functional tests.
The scripts propose and audit a profile; they do not remove package files.

## Real rebuild requirements

Use NDK r28+ for newly compiled native dependencies, and AGP 8.5.1+ for packaging.
For older NDK toolchains the Android documentation requires both linker options:

```text
-Wl,-z,max-page-size=16384
-Wl,-z,common-page-size=16384
```

Rust extensions require the equivalent linker arguments through their build
configuration. A Gradle linker flag does not rebuild precompiled third-party
`.so` files. The four WebP libraries and any strict RELRO failures must be
recompiled from genuine source or replaced with verified compatible upstream
builds. Then rerun the complete ELF audit, `zipalign -c -P 16 4 APK`, and genuine
engine/provider/tool tests on an actual 16 KiB system image. Do not patch ELF
header values or hide the compatibility warning as a substitute for rebuilding.

Chaquopy 17 states that its runtime supports 16 KiB devices but warns that older
native wheels can still fail. Current inspected binaries must remain subject to
both the structural checks and actual 16 KiB runtime tests; a version label is
not sufficient evidence.

Sources:

- https://developer.android.com/guide/practices/page-sizes
- https://www.chaquo.com/chaquopy/doc/current/changelog.html
