# Galaxy S24 Ultra root feasibility: read-only findings

Device: SM-S928N, Android 16. This report performs no unlocking, flashing, reset,
reboot or additional phone inspection.

Observed by the coordinator: `su` unavailable (exit 127), bootloader flash lock
`1`, vbmeta locked, Verified Boot green, OEM-unlock-supported property empty,
Knox warranty value `0`, KnoxGuard raw value `0x4`.

**Current result:** no usable root interface is present and boot verification is
locked. Installing an APK or connecting USB/ADB does not supply UID 0. Actual
Magisk installation feasibility is not yet established.

The official [Magisk Samsung instructions](https://topjohnwu.github.io/Magisk/install.html#samsung-devices)
require checking the actual Download-mode OEM Lock and KnoxGuard labels. Allowed
OEM unlocking, an unlockable bootloader and a non-blocking KnoxGuard state are
prerequisites. If permitted, the process uses exact matching Samsung firmware,
patches the AP archive on the target device, then flashes the patched firmware.
It requires data erasure and changes the Knox warranty bit irreversibly.

The actual OEM unlocking menu and Download-mode status were **not verified**.
A blank OEM-support property is not an explicit `0`, and does not prove either
support or absence of unlocking. The observed raw `kg=0x4` has no verified primary
source mapping here; it must not be labelled Checking/Completed/Unlocked by
assumption. Warranty value `0` does not prove an OEM unlock route exists. Korean
model suffix and Android version alone also do not establish feasibility.

[AOSP bootloader documentation](https://source.android.com/docs/core/architecture/bootloader/locking_unlocking)
describes explicit user unlock authorization, physical confirmation and factory
data reset. Its generic fastboot commands must not be substituted for Samsung's
manufacturer-specific process.

No destructive action is necessary to continue fixing the app. Ordinary Android
permissions, accessibility and an explicitly granted ADB/shell bridge can expose
some device operations, but those are distinct privilege levels and must not be
reported as root or unrestricted full control.

## Firmware-specific update

Further coordinator observations:

- One UI property `ro.build.version.oneui=80500`.
- PDA/bootloader `S928NKSS6DZH2`.
- Android security patch `2026-08-05`.
- An initial view of Developer options showed memory/bugreport/backup/stay-awake/
  Bluetooth logging controls without an OEM unlocking row. The whole menu was
  not checked because the owner was actively using the device. This partial view
  must not be described as a verified absent setting.

[Magisk issue #9447](https://github.com/topjohnwu/Magisk/issues/9447)
reports an Android 16 / One UI 8 Galaxy A54 VBMETA flashing failure. A repository
**contributor**, salvogiangri, attributes it to the One UI 8 bootloader removing
unlocking capability ([specific comment](https://github.com/topjohnwu/Magisk/issues/9447#issuecomment-3425187805)).
Another comment says prior unlocking does not preserve that ability after the
update. The reporter describes recovery by an older firmware with the same
bootloader revision. These are real first-person reports and a contributor's
explanation, not a Samsung manufacturer guarantee for this S24 firmware.

[#9885](https://github.com/topjohnwu/Magisk/issues/9885) and
[#9920](https://github.com/topjohnwu/Magisk/issues/9920) report similar problems
on a Galaxy Tab S10+ from the **same reporter**. They are not two independent
confirmations. #9885 was automatically closed for report-format/build-version
reasons; #9920 carries `not enough info` and is closed as not planned. Neither
closure proves Samsung's firmware behavior or confirms every device model.

**Inference:** the phone's observed Android 16 / One UI generation, locked green
boot state and partial absence of OEM unlocking are strongly consistent with
these newer Samsung unlock-removal reports. A normal Magisk installation route
on the current firmware is therefore doubtful. It remains unverified for this
exact SM-S928N build without the full OEM setting and actual Download-mode label.
Do not state that every S24 is permanently unrootable, or promise a downgrade.
An older matching-revision firmware's existence and permitted rollback have not
been established. No unlock, downgrade, firmware flash or reset was attempted.

## Updated public search: locked-bootloader runtime Root

CVE-2026-43499-based runtime Root is a credible additional route, distinct from bootloader unlocking and persistent Magisk installation. [RootMyS24](https://github.com/NanoTurtle1145/root-my-s24) publishes tested Chinese/HK/TW S9280 profiles, including DZH3. Do not infer an August security patch cutoff without a verified patch diff.

Actual S928N kernel read: `6.1.145-android14-11-33419968-abS928NKSS6DZH2`. Original [Root My Galaxy support feed](https://github.com/BuSung-dev/Root-My-Galaxy-Payloads/blob/main/support/targets-v3.json) contains no S928N profile. [Fusiondrive's validated offline package](https://github.com/fusiondrive/Root-My-Galaxy-SM-S928B-U-W-Offline) explicitly rejects S928N and non-DZF2 firmware. Shared kernel version/build-family does not prove compatible exploit offsets, structure layout or KernelSU artifacts. No supported exact S928N/DZH2 runtime-root method was found. A Korean target would require firmware-specific porting and device verification.

Samsung's [official Korean firmware history](https://doc.samsungmobile.com/SM-S928N/028912240216/kor.html) lists One UI7/Android15 BYG8 at binary4; the current DZH2 is binary6, and no same-binary One UI7 target was found. No firmware was downloaded or flashed.

Coordinator opened developer settings through the initial general section to USB debugging; no OEM-unlock item was observed there. Download mode was entered by `adb reboot download` for status inspection, but OEM Lock/KG labels were not obtained. No unlock, wipe, partition write or firmware flash occurred. Owner was instructed to leave using volume-down plus side-power. Subsequent ADB kernel/property reads confirm normal Android connectivity returned.
