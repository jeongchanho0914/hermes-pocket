# Optional Shizuku transport for v0.07

The four Java sources were promoted into `app/src/main/java/dev/chanho/hermes`
after the v0.07 transition was authorized. Existing native tools retain their
approval and cancellation entry points. This transport does not launch a PC,
relay, external service, or arbitrary model-generated shell command.

API contract:

```java
ShizukuPhoneBridge bridge = new ShizukuPhoneBridge(applicationContext);
bridge.setEnabled(savedShizukuEnabled);
bridge.setGlobalScope("all".equals(savedDeviceScope)); // false means no app control
JSONObject status = bridge.status();
bridge.requestPermission(requestCode); // asynchronous, explicit system permission
bridge.connect();                     // asynchronous UserService connection
bridge.wifi(enabled);                 // worker thread, after native approval
bridge.processes();
bridge.forceStop(packageName);         // installed non-system, nonprotected apps only
bridge.readSystemSetting(key);
bridge.writeSystemSetting(key, integerValue);
bridge.cancel();
bridge.close();
```

`status.available` requires enabled state, a live Shizuku binder, granted
permission, a live UserService, verified owner app UID, and actual service UID
2000 or 0. `ready_shell` and `ready_root` are distinct. The existing Root
verification must continue to mean a successful `su` UID0 check.

Writable `Settings.System` fields are `screen_brightness` 0..255,
`screen_brightness_mode`/`accelerometer_rotation` 0..1, and
`screen_off_timeout` 15000..1800000 milliseconds. `user_rotation` is read-only.
Writes use a fixed namespace/argument array and report read-back verification.
Wi-Fi and force-stop report requested execution; callers must independently
check actual effects where available.

The Binder service validates caller UID, operation IDs, package syntax,
all/none app scope, system/protected targets, setting keys and numeric ranges.
It uses per-request cancellation IDs and bounded command/output limits.
Manager shutdown uses Shizuku's reserved destroy transaction, including its
empty-payload form. No deprecated `Shizuku.newProcess` API is used.

Official API/provider/aidl/shared 13.1.5 and AndroidX annotation 1.3.0 are
recorded in `dependencies/artifacts.json`. New sources compiled successfully
against SDK35 and Java8. The release builder owns dependency verification,
jar merging and full license text; these AARs have no native ELF libraries.

No Shizuku installation or privileged device execution was performed by this
implementation agent. Actual shell UID and Samsung command behavior must be
verified separately before claiming successful privileged controls.
