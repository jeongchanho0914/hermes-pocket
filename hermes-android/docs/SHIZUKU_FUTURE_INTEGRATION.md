> Historical research record. v0.07 includes the typed Shizuku bridge. Actual release verification is tracked in TEST_REPORT.md; this record does not claim device test success.

# Shizuku optional device bridge — research only

Checked 2026-10-04. No app sources, permissions, device settings or installations
were changed for this research. The original Python engine integration remains
on hold until its native compatibility requirements are resolved.

The official manager's latest release is **v13.6.0**. It explicitly supports
Android16 QPR1 and offers nonroot automatic startup on Android13+ when connected
to trusted WLAN. This is a manager capability, not a guarantee for every Samsung
firmware. [Official release](https://github.com/RikkaApps/Shizuku/releases/tag/v13.6.0)

Android11+ allows initial wireless-debug pairing on the phone itself. Normal
client operations use local Binder; no computer or remote relay is required.
Reboot/disconnection can stop the service, so the app must check its live state
and offer reconnection. The older setup guide says manual restart after reboot;
v13.6.0's optional trusted-WLAN startup should be presented separately.
[Official setup guide](https://shizuku.rikka.app/guide/setup/)

Nonroot Shizuku normally runs as shell UID2000, not root UID0. It cannot promise
access to every setting or other apps' private data. OEM/Android policy still
applies. Keep Accessibility for UI gestures, SAF for user-selected files, and
the existing native approval/allowlist rules.
[Official API guide](https://github.com/RikkaApps/Shizuku-API)

## SDK35 manual build

Maven metadata currently reports API/provider **13.1.5** as latest. Include four
AARs: `dev.rikka.shizuku:{api,provider,aidl,shared}:13.1.5`, plus
`androidx.annotation:annotation:1.3.0`. Their AARs contain no resources or native
ELF libraries. Extract `classes.jar`, include all jars in javac and D8 inputs,
and explicitly merge the provider manifest entries. Existing minSdk26 exceeds
their minSdk requirements. Actual SDK35 / Java8 compilation of binder,
permission and UserService API calls passed with these verified artifacts.
[Maven API metadata](https://repo.maven.apache.org/maven2/dev/rikka/shizuku/api/maven-metadata.xml),
[provider metadata](https://repo.maven.apache.org/maven2/dev/rikka/shizuku/provider/maven-metadata.xml)

Manual manifest merge must include:

```xml
<uses-permission android:name="moe.shizuku.manager.permission.API_V23" />
<queries><package android:name="moe.shizuku.privileged.api" /></queries>
<!-- Inside application; replace authority with the actual application ID. -->
<meta-data android:name="moe.shizuku.client.V3_SUPPORT" android:value="true" />
<provider android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="dev.chanho.hermes.shizuku"
    android:multiprocess="false" android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

## Future implementation contract

Prefer a typed local `UserService` through `Shizuku.bindUserService`, rather
than deprecated `Shizuku.newProcess`. Proposed components:

- `ShizukuPhoneBridge`: binder-received/dead listeners, permission callback,
  bounded connection lifetime, cancellation and status publication.
- `PrivilegedPhoneService extends IPhoneControl.Stub`: shell-side operation
  dispatch with fixed operation names and validated typed arguments.
- `IPhoneControl`: `getStatus`, `runOperation`, `cancel`, and the documented
  special `destroy` transaction; no model-supplied arbitrary shell string.

State contract: `not_installed`, `not_running`, `permission_required`,
`connecting`, `ready_shell`, `ready_root`, or `disconnected`. Publish a tool
schema only when the selected plugin is enabled, the binder is alive,
permission is granted, and that operation was independently proven supported.
Use `pingBinder`, `checkSelfPermission`, `requestPermission`, and `getUid`.
Check again immediately before execution, because Binder can die after a UI
toggle. Return an explicit failure and refresh availability on disconnect.
Package visibility must be configured before using PackageManager to report
`not_installed`; otherwise a hidden installed package can be misreported.

Candidate operations are Wi-Fi/Bluetooth controls, selected settings and
allowlisted app management. They remain **candidates** until Samsung Android16
tests verify permissions, command behavior, output and restoration. The service
process is not a normal application process; ordinary Context/ContentResolver
assumptions are invalid there. Prefer tested system commands or system Binder
interfaces through the typed service. Do not weaken existing approvals.

Readiness tests must cover service absent, denied permission, actual UID2000,
death during a request, reboot/reconnect, plugin disable, cancellation and
reversible per-operation execution. No installation recommendation or automatic
Shizuku startup has been added to the current app.
