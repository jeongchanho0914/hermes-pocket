# Hermes Pocket v0.13 — performance-first architecture and contracts

Status: Android implementation, not a complete original-Hermes port. This document describes actual v0.13 sources and distinguishes planned adapters. The UI remains a local WebView; model inference uses the owner's configured provider. There is no inference model bundled in this release.

## 1. Execution lanes

```text
Owner / CLI-like conversation / minimal overlay
       |
       +-- user lane: DirectAgent + own conversation transcript
       |      +-- single device writer: native tools / actual default browser
       |      +-- terminal session processes under existing approval/UID boundaries
       |      +-- delegate_task -> private worker queue
       |
       +-- explicit /bg -> private worker queue
                              +-- worker 1: separate Net + history + cancellation
                              +-- worker 2: separate Net + history + cancellation
                              +-- remaining queued tasks

All active work -> AgentRuntime ownership -> visible ForegroundService + Stop
Job status/results -> private durable TaskLedger -> owner jobs panel / task_result
```

The user's requested 'ports' are internal execution channels, not listening TCP ports. v0.13 opens no network listener for these lanes. Keeping a task out of the chat transcript is not hiding it from the owner: /jobs and the foreground notification expose active work and cancellation. Default-browser UI remains visible and shares the one device-control lane.

### Actual bounds

| Resource | v0.13 contract |
|---|---|
| Independent model workers | At most 2 concurrently |
| Active plus queued workers | At most 8 |
| Worker time | At most 15 minutes |
| Worker model rounds | At most 12 |
| Worker tool calls | At most 48 |
| Delegate/result bounded wait | 0–30 seconds |
| Retained job records | Bounded by TaskLedger; not an infinite task archive |
| Worker privileges | Read-only memory/documents/skills/session search; no screen, browser, shell, MCP or recursive delegation |
| Device mutation | Existing single writer; cannot make two apps simultaneously own the phone display |
| Snapshot age | Existing 45,000 ms boundary is unchanged |

Source: TaskPool.java, TaskLedger.java, WorkerAgent.java, RuntimeJobs.java, ExtensionSchemas.java, AgentRuntime.java.

## 2. Lifecycle and cancellation

A worker start first reserves ownership, then obtains acknowledgement that the foreground service is live. Stop changes the generation and cancels pending starts, workers, primary requests and managed terminal work. Cancellation remains `cancelling` until the actual task unwinds; merely receiving the stop request does not release the worker slot early.

`anyWork()` includes the main run, terminal processes, workers and pending starts. The service is released only when that ownership is empty. Service instances have identity-aware teardown so an old instance cannot silently tear down a newer one. The service is non-sticky and implements its timeout callback; this is not an unlimited background-execution guarantee.

Durable job states: `queued`, `running`, `cancelling`, `completed`, `failed`, `cancelled`, `interrupted`. Restarted processes preserve completed results and mark formerly active tasks interrupted. They do not automatically repeat API calls or previously requested device actions. Re-running interrupted work requires a new explicit request. This is not a globally exactly-once transaction system.

## 3. Fast, fresh native targeting

`act_on_screen` reduces one source of latency and stale state: the model chooses an exact semantic selector, then the phone obtains a fresh observation and resolves that selector without another model round trip.

Example tool arguments:

```json
{
  "action": "click",
  "expected_package": "dev.example.app",
  "resource_id": "dev.example.app:id/confirm_button",
  "label": "Continue"
}
```

This is an example selector, not a real installed package or approval to confirm a sensitive action. Resource ID and label are exact and conjunctive when both are supplied. Labels may match text or accessibility description. No fuzzy match, first-match fallback or coordinate fallback is used. Exactly one enabled actionable element must match; otherwise the tool returns the fresh observation and `TARGET_NOT_FOUND` or `TARGET_AMBIGUOUS`, without acting.

The wrapper reuses the production read_screen and action tools, including both existing approval checks and Android/window/target restrictions. Supported actions are click, long_click, type, scroll and progress. Action-specific input arguments are disjoint. The returned post-state must still be inspected before claiming the user's goal is complete.

`OBSERVATION_REQUIRED` provides typed recovery for replaced, expired or changed snapshots. The model must obtain a new read_screen and reselect the target; it must not blindly reuse the old arguments. The response deliberately does not claim that every earlier step of a compound operation had no effects.

No measured speedup percentage or end-to-end success-rate improvement is claimed without a controlled benchmark. Source: SemanticTarget.java, SemanticActions.java, ObservationRequired.java, PhoneAccessibilityService.java, DeviceTools.java.

## 4. Actual default-browser contract

- `browser_search`: dispatches a search to the exact selected default-browser package and optionally observes its fresh visible screen.
- `browser_open`: opens a validated public HTTPS URL in that same browser and optionally observes it.
- `browser_snapshot`: observes the current exact browser without navigating.
- `web_search` defaults to the browser route.
- `web_fetch` defaults to browser_open rather than silently demanding a search-provider API key.
- Explicit `mode: "api"` retains configured API routes and their actual prerequisites.

A returned requestedUrl is not a verified final URL. Dispatch is not page completion. Visible accessibility content is not a complete DOM or full-document extraction, and a browser window is not a headless session. Browser text is untrusted data. The agent must inspect actual content and real source URLs; it cannot manufacture search hits, navigation success or citations.

Reading multiple web sources is serialized through the phone display. The primary agent can collect evidence and pass bounded text to independent workers for comparison; workers themselves cannot hijack the display. Public-page content, URL redirects, downloads and site-specific UI still require actual device/browser verification.

Source: BrowserSearch.java, WebTools.java, DirectAgent.java, AgentRuntime.java.

## 5. CLI and popup surface

Local commands are parsed on the phone and do not create fake model messages:

`/help`, `/status`, `/jobs`, `/bg task`, `/result job_id`, `/cancel job_id`, `/stop`, `/plan`, `/tools`, `/skills`, `/memory`, `/compact`, `/new`.

The transcript labels user and assistant turns `you ›` and `hermes ›`; existing persisted timelineOrder still governs real thought/tool/answer order. Jobs have an independent panel and remain usable during primary chat. The owner sees actual status/result/known usage; unknown token usage is not fabricated as zero. New text being typed into the job form survives progress updates, and late list reads cannot overwrite newer event revisions.

The popup defaults to a compact collapsed view with status and stop/close controls; expansion exposes input. Voice-only interaction is a future adapter, not a v0.13 capability. Source: app.js, cli.js, app.css, AgentOverlay.java.

## 6. Capability audit — do not equate document count with runtime support

| Area | Actual status in v0.13 |
|---|---|
| Android native tool agent | Implemented Java runtime; not original Python AIAgent |
| Independent subagents | Implemented bounded read-only workers; not full upstream delegate_task equivalence |
| Main conversation + workers | Separate histories/connections/ownership; subject to actual lifecycle verification |
| Device observation/action verification | Existing protected native tools plus new exact semantic selection |
| Default browser search/open/read | Implemented visible Android-browser adapter; not full browser/CDP/vault parity |
| Memory / SKILL.md / compaction | Existing native v0.12 facilities preserved |
| Original skill library | 210 documents bundled, 209 installable under current limits; prerequisites are not thereby implemented |
| Terminal | Actual /system/bin/sh and optional existing Shizuku backend; not desktop Linux or PTY |
| Python / original execute_code | Not in production; isolated ARM candidate has unresolved execution checks |
| MCP client/plugin execution | Full upstream runtime not implemented |
| Cron/scheduler | Not implemented; todo is bookkeeping, not scheduled execution |
| Voice, image or video generation | Not implemented by this release |
| Gateway / provider OAuth / credential pool / failover | Not complete upstream equivalents |
| Session branch/rewind/portable backup/cost ledger | Not complete upstream equivalents |
| Root/system privilege | Not granted by this work; Shizuku Shell is not Root |

The original reference remains commit d795726f78e532ca31655f74656b4be63a907581, not an assertion about current upstream HEAD.

## 7. Extension acceptance contracts

The following are design requirements for future implementations, not registered placeholder tools:

**Original Python/code adapter:** isolated runtime startup, exact dependency provenance, child-interpreter imports, killable code process, persistent-cell behavior, tool RPC authentication and cancellation must pass before exposing execute_code. Keep Android permissions in the native boundary. Do not insert API credentials into code-process environments.

**MCP adapter:** connection storage protected separately, transport negotiation and bounded tool schema validation, unique tool namespaces, owner-controlled permissions, cancellable calls, error/usage provenance and server disconnect handling. Register tools only after their connection/runtime is actually available.

**Scheduling adapter:** persistent tasks with explicit owner intent, bounded jobs and OS-compatible delivery, timezone-aware recurrence, duplicate prevention, visible failure/retry state and Stop. Do not promise exact or unbounded background execution merely by storing a timestamp.

**Voice adapter:** user-initiated recording, visible recording status, explicit permission, bounded audio retention, stop/cancel, transcript confirmation where appropriate. Reuse existing runtime entry points rather than inventing a second hidden controller.

**Session/backup adapter:** versioned export, private-field policy, authenticated encryption where credentials are included, corruption checks, transactional restore and tests that preserve the current installation. No destructive replacement of user data to simplify migration.

## 8. Evidence and release rules

`host-regression-results.json` records suite-level actual commands, source hashes, logs and earlier failures. The original JVM inventory tests needed updating after the semantic screen tool was added; those failure logs remain preserved. Do not describe the result as one initial all-green run.

`native-verification.json` is the actual emulator result, including failures if any. `s24-arm-candidate.json` is the isolated engine experiment, not the main APK. `s24-install.json`, when produced, is installation/start/hash evidence only unless it explicitly records more.

Build the frozen source only after diagnostics review and relevant tests. Preserve the existing signing identity and use a serial-bound `adb install -r` update. Never treat a zero exit from a report-collection script as proof that every nested test succeeded.
