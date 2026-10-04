# Independent candidate execution protocol

This protocol requires coordinator authorization for the selected emulator or
arm64 test environment. Do not install or launch it on the owner's phone while
another application is focused. Candidate is experimental, not the main APK.

Artifact: `candidates/full-webp/app/build/outputs/apk/engine/debug/app-engine-debug.apk`
Package: `dev.chanho.hermes.enginecandidate.fullwebp`
Activity: `dev.chanho.hermes.engineprobe.MainActivity`

First query the test serial's `ro.product.cpu.abilist`, `ro.dalvik.vm.native.bridge`
and `getconf PAGESIZE`. The current candidate includes arm64-v8a only. A plain
x86_64 emulator cannot execute its Termux Pydantic/jiter/cryptography extensions;
do not interpret INSTALL_FAILED_NO_MATCHING_ABIS as original engine failure.
Native ARM translation is an additional dependency requiring its own evidence.

When an authorized compatible environment is exclusively available, install the
candidate, launch the fully qualified activity, wait for probe completion, then
read only the candidate's `files/probe-result.json` through `run-as`. Capture
candidate-specific errors from Logcat if no result file exists. No production
preferences, credentials, files, or provider settings are used by the probe.
Its model fixture lives on the test Android device's own loopback interface.

Required result fields:

- `upstream_engine.imported == true`.
- `agent_conversation.success == true`: original AIAgent/memory/skill dispatch,
  local HTTP model exchange and final answer.
- `standalone_child.returncode == 0`, stdout includes its actual Python/SQLite
  versions, executable path in nativeLibraryDir, and `shell: child-ok`.
- `standalone_child.original_execute_code.success == true`: original public
  execute_code → unchanged persistent kernel → authenticated RPC → original
  read_file handler, followed by a second cell producing `persistent 42`.

The original execute_code test uses a narrow runtime path adapter around the
original child environment builder. The original credential scrub, guard,
security callbacks, generated runner, per-cell authority, tool allowlist,
RPC token, original read_file implementation and process teardown remain.
Fixed packaged PYTHONHOME/LD_LIBRARY_PATH/PYTHONPATH are added only for the
exact candidate launcher; no user keys are passed to child Python.

Also record `getconf PAGESIZE == 16384` for any 16K runtime claim. A 4K emulator
or ARM-translation startup cannot establish actual16K native compatibility.
The static native audit and zipalign result are separate evidence.

Potential failures are real investigation outcomes, not reasons to set success:
launcher library namespace resolution; stdlib module initialization; Unix socket
path length; subprocess FD propagation; original guard/proc identity; original
secret-scope setup; shell backend commands; imported tool availability.

Restored candidate also requires `upstream_components.image_codecs.success`
to be true, with actual PNG/JPEG/WebP/animatedWebP roundtrip results.
`original_tool_registry.registered_count` and names describe actual imported
original registrations only; credentials/permissions still affect availability.
The current API35 x86_64 emulator has native bridge0 and cannot run this APK.
