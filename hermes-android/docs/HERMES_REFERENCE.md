# Hermes reference for the Android beta

Research date: 2026-10-04. This is a design and behavior reference, not a claim
that the Android APK includes the upstream Python engine.

## Primary sources

- [Official CLI guide](https://hermes-agent.nousresearch.com/docs/user-guide/cli/):
  conversation streaming, tool progress, interruption, session picker, model
  selection and context/status surfaces.
- [Official sessions guide](https://hermes-agent.nousresearch.com/docs/user-guide/sessions/):
  persistent conversations include tool calls and results; resume restores usable
  history. Context compression and deletion are separate operations.
- [Official configuration guide](https://hermes-agent.nousresearch.com/docs/user-guide/configuration/):
  configurable agent budgets and automatic context compression with protected
  recent messages.
- [Official persistent memory guide](https://hermes-agent.nousresearch.com/docs/user-guide/features/memory/):
  memory is stored locally, edited through a tool and injected as a frozen snapshot
  at session start. Searching prior sessions is a separate tool.
- [Official model configuration guide](https://hermes-agent.nousresearch.com/docs/user-guide/configuring-models):
  provider, base URL and model are separate settings; current-session switching
  should be explicit. Model discovery alone does not prove tool-calling capability.
- [Upstream source](https://github.com/NousResearch/hermes-agent).

Local source inspected: `/home/chanho/Desktop/hermes_MIT`, revision
`516535b54275e963a82b4c28f866338fb768e7bc`. These references identify the inspected
snapshot; the official website can describe newer behavior.

| Upstream evidence | Android interpretation |
| --- | --- |
| `run_agent.py:267`, `cli.py:900` | Budget belongs to agent configuration; do not present an arbitrary eight-round mobile limit as upstream behavior. |
| `hermes_cli/cli_stream_mixin.py:659` | Show named tool execution and its result in the conversation, in addition to streaming answer text. |
| `hermes_cli/cli_commands_mixin.py:914`, `agent/interrupt_control.py:115` | Provide an immediate Stop control; preserve completed actions and mark partial output accurately. |
| `hermes_cli/cli_commands_mixin.py:1276`, `hermes_cli/cli_session_mixin.py:188` | A session drawer is the mobile counterpart of session selection and resume. |
| `hermes_cli/cli_session_mixin.py:930`, `agent/context_engine.py:98` | Context reduction must preserve the system prompt, recent turns and valid tool call/result relationships. |
| `hermes_cli/cli_model_switch_mixin.py:26` | Model, provider and endpoint are one coherent route; credentials must not silently follow a different endpoint. |
| `tools/memory_tool.py:207`, `tools/session_search_tool.py:619` | Editable user notes alone are a smaller feature than upstream model-managed memory and historical retrieval. |

## Implementation priorities identified before beta hardening

These were findings in the starting Android source, not the final release status.
Use release test reports to determine which are implemented and verified.

1. **Complete stream validation.** `Net.stream` accepted EOF without a terminal
   marker, and `DirectAgent` ignored `finish_reason`. A broken stream could be
   mistaken for a complete reply or usable tool request. Reject truncated streams
   before dispatching any model-proposed action; test EOF, malformed arguments,
   token limits and valid completion.
2. **Durable interrupted history.** The user message was saved for display before
   the first model request, but request history was persisted only at completion
   or after a tool batch. Save the input before requesting; preserve shown partial
   responses as interrupted output and completed tool results with valid pairing.
3. **Context management.** Request history grew without a budget. Provide a
   configurable context/output budget, retain recent complete conversation groups,
   and persist a summary when reducing older context. Display reduction clearly;
   do not describe reduction as deleting the user's archive.
4. **Useful iteration budget.** Eight hardcoded rounds end ordinary multi-step
   tasks too early. Configure a bounded mobile default and user-selectable limit.
   At the limit, retain completed work and explain why execution stopped.
5. **Honest connection test.** `/models` proves a catalog request only. A beta
   should distinguish endpoint access, real chat generation and structured tool
   support, allow manual model IDs, and offer provider presets without promising
   compatibility with protocols it does not implement.
6. **Traceable tool progress.** Stream text plus a generic busy label is too weak
   for an agent. Show tool name, approval waiting state, completion/error and an
   accessible result. After switching to another app, foreground service and
   notification behavior need Android runtime verification.
7. **Memory and retrieval scope.** Existing manual notes apply to new sessions,
   matching the upstream frozen-snapshot pattern. To offer upstream-like learning,
   add model-callable local memory operations and local session search. Until
   implemented, clearly label manual notes and omit self-learning claims.
8. **Mobile command access.** New chat, history/resume, model/configuration, tools,
   context/status and Stop should be accessible through the mobile GUI. Slash
   commands can be optional shortcuts; desktop shell, git-worktree and gateway
   controls are not appropriate substitutes for Android features.

## Architecture boundary

The requested app runs conversation orchestration, storage, approval and Android
tools on the phone. Its only required external service is the selected model API.
The upstream Python adapter is not a dependency of this mode. Reproducing Hermes
interaction patterns in Java is not the same as bundling Hermes Python, terminal,
browser automation, plugins, skills, cron and the original learning system.

The beta should be described as an unofficial native Android adaptation with an
explicit feature list. Installation and verification claims must refer to the
actual signed APK and Android tests, not browser mocks or legacy adapter tests.

## v0.01 implementation status

The final native runtime adds strict SSE completion checks before tool dispatch,
whole-batch JSON argument parsing, durable tool call/result pairs, preserved partial
responses on cancellation/error, configurable 2–64 model requests, complete-turn
context omission, and a selected-model API probe. The mobile UI shows tool progress,
supports interruption and session resume, and lets users edit memory locally.

Intentional differences: memory edits apply on the next turn rather than a frozen
upstream session snapshot; context budgeting omits old complete turns and preserves
the full local transcript rather than producing an LLM summary; memory is manually
edited, with no automatic memory or historical-session-search tool. Original Python
Skills/plugins/terminal/cron/voice are not included. Read `TEST_REPORT.md` for actual
APK, fixture, Android and browser test evidence.

## v0.02 제공업체 설정과 로고

설정 흐름은 공식 Hermes의 `hermes_cli/auth.py` 제공업체 레지스트리와
`hermes_cli/providers.py` 설정을 참고했습니다. 참조한 커밋은
`516535b54275e963a82b4c28f866338fb768e7bc`입니다.

- 제공업체 선택 → 해당 업체의 API 키 저장 → 실제 모델 목록에서 선택 → 생각 수준 설정.
- Android에서는 인증 정보를 Android Keystore로 암호화하며, 연결 대상 변경 시 이전 키를 제거합니다.
- 기본 API 주소는 native 카탈로그로 고정하고 직접 연결만 주소를 편집합니다.
- 원본의 Anthropic Messages·OpenAI Responses·AWS·OAuth 어댑터 전체를 이식하지 않았습니다. 현재는 Chat Completions 호환 API 방식입니다.
- 원본 캐릭터를 APK 내부에 포함했습니다. [이미지 출처](HERMES_ASSETS.md).

공식 출처: [제공업체 문서](https://hermes-agent.nousresearch.com/docs/integrations/providers/),
[auth.py](https://github.com/NousResearch/hermes-agent/blob/516535b54275e963a82b4c28f866338fb768e7bc/hermes_cli/auth.py),
[providers.py](https://github.com/NousResearch/hermes-agent/blob/516535b54275e963a82b4c28f866338fb768e7bc/hermes_cli/providers.py).

기본 모델·기본 생각 수준은 앱 재실행과 새 대화에도 유지합니다. 생각 수준은
`hermes_constants.py`의 `parse_reasoning_effort`와 제공업체 설정 선택기를 참고해
`minimal/low/medium/high/xhigh/max/ultra`를 제공합니다. `none`은 끄기, 앱의 `auto`는
원본의 빈 값(제공업체 기본값)입니다. `ultra`의 실제 API 값은 원본
`agent/reasoning_effort.py`와 같이 `max`로 처리합니다.
