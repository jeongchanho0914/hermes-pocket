# Android build dependencies

The manual builder includes the official Shizuku API `13.1.5` modules
`api`, `provider`, `aidl`, and `shared`, plus their AndroidX Annotation `1.3.0`
dependency. These are Java libraries. The production APK does not include the
separate Python engine experiment or native libraries.

`scripts/android_dependencies.py` downloads the four AARs from
`https://repo.maven.apache.org/maven2/dev/rikka/shizuku/` and AndroidX Annotation
from Google's Android Maven repository. Each file has a source-controlled
SHA-256 pin. Cached artifacts are verified on every build; a mismatch stops the
build. Verified AAR `classes.jar` entries are regenerated before compilation.
The builder rejects AARs containing resources, additional assets, native
libraries, or nested libraries because these require additional merge steps.

The five jars are supplied to both `javac` and D8. D8 merges the app and the
libraries into the APK's dex files. The app manifest explicitly includes the
Shizuku provider declaration, permission and client metadata; this manual build
does not perform automatic AAR manifest merging.

The full Shizuku MIT and AndroidX Apache 2.0 licenses are distributed in
`assets/third-party-licenses.txt`. Dependency coordinates, URLs and SHA-256
values are recorded in `dist/build-info.json` for each completed build and in
`.toolchain/shizuku-13.1.5/verified-dependencies.json`.

Validation performed before the v0.07 build: all five pinned artifact hashes
and ZIP CRCs passed; 103 class names contained no duplicates; the Shizuku API
and provider classes were present; a corrupted cached file was rejected; a
dependency-only D8 merge against SDK 35/min API 26 succeeded. This validates
packaging inputs, not live Shizuku permission or user-service operation.

Official references:

- [Shizuku API and integration guide](https://github.com/RikkaApps/Shizuku-API)
- [Shizuku API 13.1.5 Maven publication](https://central.sonatype.com/artifact/dev.rikka.shizuku/api/13.1.5)
- [Shizuku MIT license](https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE)
- [AndroidX Annotation](https://developer.android.com/jetpack/androidx/releases/annotation)
