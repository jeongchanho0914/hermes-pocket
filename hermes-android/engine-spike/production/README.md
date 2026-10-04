# Reviewable production engine candidate

This Gradle project packages the **real** existing Android Java code, manifest,
resources and UI assets with the phone-verified original Hermes Python engine.
It references pinned upstream source and signed Termux Python dependencies
under `../dependencies`, and the verified native library closure from the
isolated probe. The production Python entry module belongs in
`../../app/src/main/python/hermes_android.py`.

Default application ID is `dev.chanho.hermes.enginecandidate`, allowing checks
alongside the installed stable app. No APK is automatically installed or sent.
`-PengineProductionPackage=true` explicitly builds the real package ID after
integration checks pass. Version metadata follows the repository's version.json.

Build with the existing JDK17 and SDK35:

```sh
JAVA_HOME=../../.toolchain/jdk/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew :app:assembleRelease
```

The release APK is deliberately unsigned here. The release owner must align
and sign it using the retained `.signing/development.jks` and password file,
then verify v1/v2/v3 signatures, manifest version, assets and native ABI. Never
put the key or password in source, command output or delivery files.

Required gates before replacing the stable APK:

- Original agent memory/skill tool loop succeeds on the actual phone.
- Python Java bridge dispatches real native tools and preserves approvals,
  disabled plugin filtering and cancellation.
- Model credentials remain in memory for requests and encrypted in native storage.
- Foreground service, background/reopen, renderer recovery and streaming are checked.
- Android16 compatibility dialog is identified, and supported page size handling
  is verified. Native ELF alignment has its own evidence, not inferred from APK ZIP alignment.

This project does not claim every desktop executable/integration works on
Android. Device, shell, browser and scheduled features require individual
working implementations and tests before being advertised.
