# Android terminal reference for v0.09

Reference: the local Hermes upstream snapshot pinned to `d795726f78e532ca31655f74656b4be63a907581`, inspected through `engine-spike/dependencies/vendor-minimal/tools/`. This describes reference behavior and implementation options, not a claim that every capability exists in the Android port.

## Advertised calls

`terminal`: required `command:string`; optional `background:boolean=false`, `timeout:integer>=1` (default 180 seconds), `workdir:string` (absolute), `pty:boolean=false`, `notify:boolean|string[]`, `heartbeat:integer>=0=0`, `persist_on_release:boolean=false`. No advertised environment argument. The internal legacy aliases `notify_on_complete` and `watch_patterns` remain accepted; explicit `notify` takes precedence.

`process_manage`: required `action` from `list,poll,log,wait,kill,write,submit,close,handoff`; `session_id:string` required except for list; optional `data:string`, `timeout:integer>=1`, `offset:integer`, `limit:integer>=1`. IDs have `proc_` plus a hex tail; exact IDs and unique prefixes of at least four tail characters work. Bare tail prefixes are normalized. Missing/ambiguous/short prefixes return `status:not_found`.

PTY, notify, heartbeat and persistence are background modifiers; the dispatcher rejects their use in foreground calls. Positive heartbeat is clamped to at least 60 seconds and implies completion notification. Pattern notifications are rate limited and may fall back to completion notification. A foreground timeout above the configurable 600-second cap promotes the command to a tracked notifying background job; it must not execute twice.

## Results and ownership

Foreground returns JSON `output,exit_code,error` with `error:null` for a completed execution, even for nonzero command exit. `cwd` is optional and reports an observed change from the command's starting directory. Optional annotations include `approval,exit_code_meaning,hint,verification_evidence` and spill metadata `output_total_chars,full_output_path,truncation_note`.

Background spawn returns `output:"Background process started",session_id,pid,exit_code:0,error:null`. This zero means launch success, not command completion. Optional flags describe notification delivery, heartbeat, persistence or unsupported delivery.

| Action | Reference result and behavior |
| --- | --- |
| list | `processes` array; entries include `session_id,command,cwd,pid,owner_task_id,started_at,uptime_seconds,status,output_preview`, plus optional metadata. Lists running and retained owner/conversation results. |
| poll | `session_id,command,status,pid,uptime_seconds,output_preview`; preview is the last 1,000 characters, **not** a cursor or newly produced delta despite schema prose. Exited results add `exit_code,completion_reason,termination_source`. |
| log | `session_id,command,status,output,total_lines,showing`; default last 200 lines; explicit `offset:0` selects the head. Reads the bounded rolling capture, not unlimited history. |
| wait | Completed: `status:exited,command,exit_code,completion_reason,termination_source,output` and optional `output_cut`. Timeout: `status:timeout,command,output,process_running:true,timeout_note`; process remains running. Timeout defaults/clamps to configured 180 seconds. |
| kill | Successful explicit stop: `status:killed,session_id,completion_reason:killed,termination_source:process.kill,output` and optional `output_cut`. Already finished: `status:already_exited` plus exit snapshot. Survivors produce `status:error,process_running:true,survivors` and remain manageable. |
| write | Raw data without newline. Success `status:ok,bytes_written` (upstream uses string length, despite field name). |
| submit | Calls write with appended `\n` on POSIX; Windows PTY uses `\r\n`. |
| close | EOF only, no implicit kill. Success `status:ok,message:"stdin closed"` or `"EOF sent"` for PTY. |
| handoff | Subagent-only transfer to live parent; requires purpose sentence in data and ownership. Android without delegation should explicitly reject it. |

Actual upstream normal background pipe mode launches stdin as `DEVNULL`; write/submit/close therefore return `status:error` for these sessions. PTY and specifically adopted pipe handles can accept input. A Java pipe mode with writable stdin is a possible Android extension and must not be described as PTY.

Live capture rolls at 200,000 characters; finished in-memory entries retain for 30 minutes with 64 tracked-entry pruning. Durable redacted completion receipts retain seven days/newest 64 per profile and are read-only; they do not restore a live PID or replay completion. Live sessions belong to a spawning owner and conversation; receipt reads require the owning conversation or its compression successor. UUID-like process IDs are separate from conversation/session IDs.

## Shell state and cancellation

Local foreground uses a fresh bash per call, sources a shell snapshot, runs the command, saves exported environment and emits a private cwd marker. Initial snapshot includes functions/aliases; subsequent snapshots re-dump exports only, so dynamic function/alias persistence is not guaranteed. Only observed completed cwd without a transient workdir updates the conversation cwd. Explicit workdir never replaces it. A background command has its own initial cwd and does not change the conversation cwd. Background spawning uses sanitized base environment, not the foreground snapshot wrapper, so export mutations solely held in that snapshot are not automatically inherited.

Foreground timeout kills the process group/tree and returns exit 124 with `[Command timed out after Ns]`; cancellation returns 130 with `[Command interrupted]`. A command intentionally exiting 124/130 is distinguishable internally. A new steering message can instead yield a foreground command into a tracked background session without killing it. `process_manage wait` interruption or elapsed wait window never itself kills the job. Explicit kill terminates descendants and checks survivors; lifecycle cleanup skips `persist_on_release` jobs, but explicit operator stop still reaches them. Persistence is not survival of Android process death or reboot.

## Android implementation boundary

The app targets SDK 35. A small Java implementation can run the system shell with `ProcessBuilder`, merge stderr, maintain writable stdin/output capture and keep registry state without Python or large ELF bundles. Its shell is Android `/system/bin/sh`, so bash-specific upstream snapshot commands require adaptation and bash-only scripts need an actual bash installation. Executing a script through the system shell does not supply missing interpreters, packages or privileges. Android shell commands retain the app's UID and permissions.

A real PTY can be implemented with a small packaged JNI library; Termux's primary implementation opens `/dev/ptmx`, grants/unlocks it, forks, calls `setsid`, attaches the slave to stdin/out/err, sets window dimensions and execs the chosen shell. Java `ProcessBuilder` pipes alone do not do this. PTY readiness requires implementation and device verification; reject unsupported `pty:true` honestly until then. [Termux JNI source](https://github.com/termux/termux-app/blob/master/terminal-emulator/src/main/jni/termux.c).

Apps targeting Android 10+ cannot directly execute files from writable app home, so a bootstrap that downloads binaries and chmods them is not an equivalent deployment plan; package needed native code in the APK or use existing system executables. [Android execution restriction](https://developer.android.com/about/versions/10/behavior-changes-10).

Background tool execution means the tool call returns while a process continues. It does not by itself authorize unrestricted Android background execution. A user-visible foreground service supports ongoing execution; Android 12+ restricts starting such services while backgrounded, and Android lifecycle rules can still stop app processes. Do not promise indefinite daemon survival. [Foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [process lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle).
