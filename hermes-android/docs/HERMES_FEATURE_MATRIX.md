# Hermes feature parity inventory — pinned reference, initial audit

Reference: NousResearch/hermes-agent `d795726f78e532ca31655f74656b4be63a907581` from `engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581`. The manifest pins this revision. **This is not a claim that the local tree is the latest upstream. No live upstream freshness verification was performed.** Native Java is the shipped execution model; the engine-spike/source/vendor/vendor-minimal trees do not establish that Python Hermes runs in the APK.

The audited finite inventory contains **107 exact registered tool names** (90 built-ins and 17 bundled plugin tools), **102 bundled plugin manifests**, **24 gateway enum names**, and **210 upstream skill documents**. Initial tool statuses: missing 76, partial 13, platformblocked 18. No original tool is marked fully implemented. Status is based on code, not intended UI labels.

`implemented`: complete audited contract; `partial`: actual native subset/equivalent with gaps; `missing`: code absent; `platformblocked`: original desktop/host contract requires adaptation; `needsAPIconfig`: working implementation additionally requires an API/account configuration. A missing integration is not merely a missing key. All test references below are files found in the repository; **none were executed by this documentation agent** and none alone proves upstream parity. The skill owner separately reports 10 PASS in the isolated production `SkillPackagesHarness`; this is recorded as reported execution, scoped to package/store behavior, not an assertion that every listed test ran.

The source tools discovery scanner imports top-level self-registration in `tools/*.py` and `tools/*/tool.py` ([registry source](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/registry.py#L93)). Inventory extraction inspected AST without importing the Python engine. Table registration loops were expanded, including browser, kanban, HA, Feishu, Discord, Yuanbao, xAI video, Spotify, Google Meet and A2A. MCP-advertised tools, context-engine tools, and arbitrary externally installed plugin tools are dynamic families, explicitly listed below. Internal helper functions/files are not presented as independent user features.

## Runtime, sessions, learning, scheduler and execution backends

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `Streaming model/tool loop` | partial | DirectAgent streams OpenAI-compatible chat completions and dispatches native tools; original Python run_agent is not running. | [run_agent.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/run_agent.py#L1) | tests/jvm/DirectAgentHarness.java |
| `Provider selection, custom endpoint, model discovery` | partial | LocalCapabilities providers catalog + Net; API credentials/configuration needed for remote models. OAuth/provider-specific transports not all ported. | [agent/provider_registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/provider_registry.py#L1) | tests/jvm/LocalFeaturesHarness.java |
| `Reasoning effort and reasoning replay` | partial | Native provider effort mapping/reasoning replay; original per-model metadata/transports not all ported. | [agent/reasoning_effort.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/reasoning_effort.py#L1) | tests/jvm/DirectAgentHarness.java |
| `Iteration/context budget` | partial | DirectAgent bounded rounds and character-budget whole-turn truncation; lacks original token budget semantics. | [agent/iteration_budget.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/iteration_budget.py#L1) | tests/jvm/DirectAgentHarness.java |
| `Context summarization/compaction` | missing | Native history truncation is not LLM summarization/micro compaction. | [agent/context_compressor.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/context_compressor.py#L1) | None identified |
| `Context engine selection and runtime-provided tools` | missing | No context-engine backend registry or plugin tools. | [agent/context_engine.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/context_engine.py#L1) | None identified |
| `Parallel tool batches` | missing | No parity claim for upstream concurrent tool batch executor. | [agent/tool_call_batches.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/tool_call_batches.py#L1) | None identified |
| `Subagent isolated contexts, depth and fan-out` | missing | No native delegate_task. | [tools/delegate_tool.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/delegate_tool.py#L1) | None identified |
| `Persistent sessions, list/resume/rename/delete` | partial | Store private JSON conversation records; lacks original SQLite session/event database contract. | [hermes_state.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/hermes_state.py#L1) | tests/jvm/LocalFeaturesHarness.java |
| `Session branching, rewind, recovery and export` | missing | No native upstream session branch/rewind/export/recovery flow. | [hermes_state_rewind.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/hermes_state_rewind.py#L1) | None identified |
| `Usage/cost accounting` | missing | No original durable per-model cost ledger. | [hermes_state_usage.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/hermes_state_usage.py#L1) | None identified |
| `Multimodal screenshots and attachments` | partial | ScreenCapture image transport exists; original all file/audio/video inbound formats not ported. | [agent/message_content.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/message_content.py#L1) | tests/jvm/ScreenVisionHarness.java |
| `Voice memo transcription` | missing | No native original transcription provider registry. | [agent/transcription_registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/transcription_registry.py#L1) | None identified |
| `Approval, cancellation and tool audit` | partial | ApprovalGate, OwnerApprovalPolicy, service cancellation/audit; independent Android approval policy, not exact upstream approvals. | [tools/approval.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/approval.py#L1) | tests/jvm/OwnerApprovalPolicyHarness.java |
| `Durable memory and user profile` | partial | One native memory store; no separate user modeling provider. | [agent/memory_manager.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/memory_manager.py#L1) | tests/jvm/LocalFeaturesHarness.java |
| `Periodic memory/skill learning nudges` | missing | No original periodic learning loop. | [agent/periodic_scheduler.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/periodic_scheduler.py#L1) | None identified |
| `Skill discovery/injection and progressive disclosure` | partial | Native local package discovery plus original 210-package bundled catalog. Preserved original categories are catalog metadata; installed category tree/plugin/cross-profile lookup remains absent. Documents remain instructions; installation does not activate scripts. | [tools/skills_tool.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/skills_tool.py#L1) | tests/jvm/LocalFeaturesHarness.java |
| `Skill creation, revision and resources` | partial | Native runtime now dispatches canonical atomic create/patch/write_file/remove_file/delete operation arrays after transaction preview and approval; full SKILL.md documents/resources and owner ZIP import/export work. Category/plugin namespaces, hub/sync/lint/security scan, dependency installation and full YAML syntax remain gaps. | [tools/skill_manager_tool.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/skill_manager_tool.py#L1) | tests/jvm/LocalFeaturesHarness.java |
| `Skills hub install/search/update/publish` | missing | No network skills hub installation lifecycle. | [tools/skills_hub.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/skills_hub.py#L1) | None identified |
| `Skill security scan and quarantine` | missing | Native safe path/size/frontmatter validation exists but no original scan/quarantine system. | [tools/skills_guard.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/skills_guard.py#L1) | None identified |
| `Scheduled create/list/update/pause/resume/remove/run tasks` | missing | No native cron scheduler or AlarmManager/WorkManager adaptation. | [cron/scheduler.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/cron/scheduler.py#L1) | None identified |
| `Scheduler durable state, timezones and delivery` | missing | No scheduled job store, boot recovery, background execution or platform delivery. | [cron/jobs.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/cron/jobs.py#L1) | None identified |
| `Gateway routing and cross-platform continuity` | missing | Native foreground agent service is not original gateway. | [gateway/run.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/run.py#L1) | None identified |
| `Gateway allowlists, pairing and channel permissions` | missing | No messaging gateway authentication/owner pairing. | [gateway/config.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L1) | None identified |
| `API server and webhooks` | missing | No externally reachable API/webhook gateway. | [gateway/platforms/api_server.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/platforms/api_server.py#L1) | None identified |
| `Gateway multiplex profiles and room isolation` | missing | No multi-profile platform gateway. | [gateway/platform_registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/platform_registry.py#L1) | None identified |
| `Plugin install/update/enable/disable/remove` | missing | LocalCapabilities plugin toggles select native modules only; no original plugin install/load. | [hermes_cli/plugins.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/hermes_cli/plugins.py#L1) | None identified |
| `Plugin lifecycle hooks and custom tool/CLI registrations` | missing | No general plugin hooks/import/entrypoint loader. | [hermes_cli/plugins.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/hermes_cli/plugins.py#L1) | None identified |
| `MCP server connect/discover/tools/resources/prompts` | missing | No native MCP client registry or discovered tools. | [tools/mcp_tool.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/mcp_tool.py#L1) | None identified |
| `MCP tools server transport` | missing | No native Hermes MCP server transport. | [agent/transports/hermes_tools_mcp_server.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/transports/hermes_tools_mcp_server.py#L1) | None identified |
| `ACP IDE agent protocol` | missing | No Android ACP adapter. | [acp_adapter/entry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/acp_adapter/entry.py#L1) | None identified |
| `A2A agent discovery/orchestration` | missing | No Android A2A client/server. | [plugins/platforms/a2a/tools.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/a2a/tools.py#L1) | None identified |
| `Terminal local backend` | partial | Android /system/bin/sh app UID replaces host local Python backend. | [tools/environments/local.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/environments/local.py#L1) | tests/jvm/TerminalToolsHarness.java |
| `Terminal Docker backend` | platformblocked | No Android Docker daemon/client backend bundled; requires external runtime/explicit remote adaptation. | [tools/environments/docker.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/environments/docker.py#L1) | None identified |
| `Terminal SSH backend` | missing | No SSH transport/client backend; Android shell existence does not implement SSH. | [tools/environments/ssh.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/environments/ssh.py#L1) | None identified |
| `Terminal Singularity backend` | platformblocked | No Singularity runtime on native Android. | [tools/environments/singularity.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/environments/singularity.py#L1) | None identified |
| `Terminal Modal backend` | missing | No Modal SDK/service adapter; requires account/API configuration and real implementation. | [tools/environments/modal.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/environments/modal.py#L1) | None identified |
| `Terminal Daytona backend` | missing | No Daytona SDK/service adapter; requires account/API configuration and real implementation. | [tools/environments/daytona.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/environments/daytona.py#L1) | None identified |
| `Terminal Vercel Sandbox backend` | missing | No Vercel SDK/service adapter; requires account/API configuration and real implementation. | [tools/environments/vercel_sandbox.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/environments/vercel_sandbox.py#L1) | None identified |
| `PTY, foreground streams and process persistence` | partial | Native pipes/background jobs exist; PTY and detached original session persistence absent. | [tools/process_registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/process_registry.py#L1) | tests/jvm/TerminalSessionsHarness.java |
| `Browser automation, CDP and extension backend` | missing | Android accessibility is not DOM refs/CDP/browser tool parity. | [agent/browser_registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/browser_registry.py#L1) | None identified |
| `Browser vault, login and one-time codes` | missing | No encrypted browser vault or Bitwarden/1Password adapter. | [tools/browser_vault_tool.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_vault_tool.py#L1) | None identified |
| `Web research provider registry` | partial | Mwmbl/Tavily/SearXNG adapter subset. | [agent/web_search_registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/web_search_registry.py#L1) | tests/jvm/LocalFeaturesHarness.java |
| `Image/video generation registry` | missing | No original image/video generation backend adapter. | [agent/image_gen_registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/image_gen_registry.py#L1) | None identified |
| `Text-to-speech backend registry` | missing | No registered native text_to_speech tool. | [agent/tts_registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/tts_registry.py#L1) | None identified |
| `Memory-provider plugins / Honcho` | missing | No Honcho dialectic/user modeling or memory backend plugin. | [agent/memory_provider.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/memory_provider.py#L1) | None identified |
| `Provider OAuth, credential pool and failover` | missing | Native single selected API credential configuration; no original pool/OAuth/cooldown/fallback lifecycle. | [agent/credential_pool.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/credential_pool.py#L1) | None identified |
| `Profiles, named workspaces and config migration` | missing | Native preferences exist; no upstream profile/home/config contract. | [hermes_cli/profiles.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/hermes_cli/profiles.py#L1) | None identified |
| `Backups and restore/portable state` | missing | No upstream archive/SQLite/secret-aware restore. | [hermes_cli/backup.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/hermes_cli/backup.py#L1) | None identified |
| `Interactive TUI/slash commands/history` | partial | Android chat/composer/sidebar adaptations; no original terminal TUI/slash command engine. | [cli.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/cli.py#L1) | tests/test_ui.py |
| `Desktop projects, preview panes and window integration` | platformblocked | Requires explicit Android surface replacements. | [tui_gateway/server.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tui_gateway/server.py#L1) | None identified |
| `Kanban persistent DAG/review/dispatcher` | missing | No original persistent board/worker dispatcher/dashboard. | [tests/hermes_cli/fixtures/plugin_compat_legacy/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tests/hermes_cli/fixtures/plugin_compat_legacy/plugin.yaml#L1) | None identified |
| `Research batch trajectories and compression` | missing | No native research batch/trajectory pipeline. | [batch_runner.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/batch_runner.py#L1) | None identified |
| `Package management/install/update/service orchestration` | platformblocked | Android APK deployment replaces desktop Python PM/systemd/s6 installation; no Python package manager port. | [pm/registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/pm/registry.py#L1) | None identified |
| `Secrets sources/vault and backend integrations` | missing | Native local configured keys are not original secret-source provider registry. | [agent/secret_sources/registry.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/secret_sources/registry.py#L1) | None identified |

## Every exact registered upstream tool name

### a2a

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `a2a_call` | missing | No native registered implementation. | [plugins/platforms/a2a/tools.py:359](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/a2a/tools.py#L359) | None identified |
| `a2a_discover` | missing | No native registered implementation. | [plugins/platforms/a2a/tools.py:359](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/a2a/tools.py#L359) | None identified |
| `a2a_history` | missing | No native registered implementation. | [plugins/platforms/a2a/tools.py:359](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/a2a/tools.py#L359) | None identified |
| `a2a_list` | missing | No native registered implementation. | [plugins/platforms/a2a/tools.py:359](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/a2a/tools.py#L359) | None identified |
| `a2a_orchestrate` | missing | No native registered implementation. | [plugins/platforms/a2a/tools.py:359](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/a2a/tools.py#L359) | None identified |

### browser

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `browser_back` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_click` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_console` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_get_images` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_navigate` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_press` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_scroll` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_snapshot` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_type` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |
| `browser_vault_enter_code` | missing | No native registered implementation. | [tools/browser_vault_tool.py:754](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_vault_tool.py#L754) | None identified |
| `browser_vault_fill` | missing | No native registered implementation. | [tools/browser_vault_tool.py:763](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_vault_tool.py#L763) | None identified |
| `browser_vault_list` | missing | No native registered implementation. | [tools/browser_vault_tool.py:727](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_vault_tool.py#L727) | None identified |
| `browser_vault_save_login` | missing | No native registered implementation. | [tools/browser_vault_tool.py:745](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_vault_tool.py#L745) | None identified |
| `browser_vault_unlock` | missing | No native registered implementation. | [tools/browser_vault_tool.py:736](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_vault_tool.py#L736) | None identified |
| `browser_vision` | missing | No native registered implementation. | [tools/browser_tool.py:1365](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_tool.py#L1365) | None identified |

### browser-cdp

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `browser_cdp` | missing | No native registered implementation. | [tools/browser_cdp_tool.py:396](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_cdp_tool.py#L396) | None identified |
| `browser_dialog` | missing | No native registered implementation. | [tools/browser_dialog_tool.py:101](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_dialog_tool.py#L101) | None identified |

### browser-use

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `browser_exec` | missing | No native registered implementation. | [tools/browser_use_cli.py:828](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/browser_use_cli.py#L828) | None identified |

### clarify

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `clarify` | missing | No native registered implementation. | [tools/clarify_tool.py:188](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/clarify_tool.py#L188) | None identified |

### code_execution

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `execute_code` | platformblocked | Python tool-RPC interpreter not shipped in native app. Engine-spike Python files are development evidence, not installed execution capability. | [tools/code_execution_tool.py:964](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/code_execution_tool.py#L964) | None identified |

### computer_use

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `computer_use` | partial | DeviceTools + accessibility/screen capture adapt mouse/keyboard to touch, text, scroll and navigation; desktop cua-driver schema/backend absent. | [tools/computer_use_tool.py:13](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/computer_use_tool.py#L13) | tests/jvm/ScreenVisionHarness.java, tests/android_device_boundaries.py |

### connections

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `manage_connections` | missing | No native registered implementation. | [tools/connectors/tool.py:132](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/connectors/tool.py#L132) | None identified |

### cronjob

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `cronjob_manage` | missing | No native registered implementation. | [tools/cronjob_tools.py:1189](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/cronjob_tools.py#L1189) | None identified |

### delegation

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `delegate_task` | missing | No native registered implementation. | [tools/delegate_tool.py:785](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/delegate_tool.py#L785) | None identified |

### desktop_ui

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `annotate_preview` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/annotate_preview_tool.py:90](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/annotate_preview_tool.py#L90) | None identified |
| `apply_layout` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/apply_layout_tool.py:46](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/apply_layout_tool.py#L46) | None identified |
| `close_terminal` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/close_terminal_tool.py:46](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/close_terminal_tool.py#L46) | None identified |
| `desktop_preview` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/preview_tool.py:72](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/preview_tool.py#L72) | None identified |
| `drive_preview` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/drive_preview_tool.py:143](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/drive_preview_tool.py#L143) | None identified |
| `focus_pane` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/focus_pane_tool.py:24](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/focus_pane_tool.py#L24) | None identified |
| `gui_tour` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/tour_tool.py:139](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/tour_tool.py#L139) | None identified |
| `react_to_message` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/react_to_message_tool.py:112](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/react_to_message_tool.py#L112) | None identified |
| `read_terminal` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/read_terminal_tool.py:75](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/read_terminal_tool.py#L75) | None identified |
| `read_window_below` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/read_window_tool.py:42](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/read_window_tool.py#L42) | None identified |
| `show_tip` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/tip_tool.py:77](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/tip_tool.py#L77) | None identified |

### discord

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `discord` | missing | No native registered implementation. | [tools/discord_tool.py:627](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/discord_tool.py#L627) | None identified |

### discord_admin

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `discord_admin` | missing | No native registered implementation. | [tools/discord_tool.py:627](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/discord_tool.py#L627) | None identified |

### feishu_doc

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `feishu_doc_read` | missing | No native registered implementation. | [tools/feishu_doc_tool.py:67](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/feishu_doc_tool.py#L67) | None identified |

### feishu_drive

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `feishu_drive_add_comment` | missing | No native registered implementation. | [tools/feishu_drive_tool.py:192](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/feishu_drive_tool.py#L192) | None identified |
| `feishu_drive_list_comment_replies` | missing | No native registered implementation. | [tools/feishu_drive_tool.py:192](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/feishu_drive_tool.py#L192) | None identified |
| `feishu_drive_list_comments` | missing | No native registered implementation. | [tools/feishu_drive_tool.py:192](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/feishu_drive_tool.py#L192) | None identified |
| `feishu_drive_reply_comment` | missing | No native registered implementation. | [tools/feishu_drive_tool.py:192](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/feishu_drive_tool.py#L192) | None identified |

### file

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `patch` | missing | No native registered implementation. | [tools/file_tools.py:1414](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/file_tools.py#L1414) | None identified |
| `read_file` | partial | SAF-scoped equivalents phone_read_file / phone_write_file exist; upstream path/line-offset/binary/shell filesystem contract differs. | [tools/file_tools.py:1390](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/file_tools.py#L1390) | tests/jvm/PhoneFilesPathsHarness.java, tests/android-probe/v006_saf.js |
| `search_files` | missing | No native registered implementation. | [tools/file_tools.py:1415](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/file_tools.py#L1415) | None identified |
| `write_file` | partial | SAF-scoped equivalents phone_read_file / phone_write_file exist; upstream path/line-offset/binary/shell filesystem contract differs. | [tools/file_tools.py:1391](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/file_tools.py#L1391) | tests/jvm/PhoneFilesPathsHarness.java, tests/android-probe/v006_saf.js |

### google_meet

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `meet_join` | platformblocked | Upstream Google Meet plugin requires Linux/macOS Chromium and desktop audio routing; no Android meeting bot adapter. | [plugins/google_meet/__init__.py:50](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/google_meet/__init__.py#L50) | None identified |
| `meet_leave` | platformblocked | Upstream Google Meet plugin requires Linux/macOS Chromium and desktop audio routing; no Android meeting bot adapter. | [plugins/google_meet/__init__.py:50](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/google_meet/__init__.py#L50) | None identified |
| `meet_say` | platformblocked | Upstream Google Meet plugin requires Linux/macOS Chromium and desktop audio routing; no Android meeting bot adapter. | [plugins/google_meet/__init__.py:50](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/google_meet/__init__.py#L50) | None identified |
| `meet_status` | platformblocked | Upstream Google Meet plugin requires Linux/macOS Chromium and desktop audio routing; no Android meeting bot adapter. | [plugins/google_meet/__init__.py:50](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/google_meet/__init__.py#L50) | None identified |
| `meet_transcript` | platformblocked | Upstream Google Meet plugin requires Linux/macOS Chromium and desktop audio routing; no Android meeting bot adapter. | [plugins/google_meet/__init__.py:50](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/google_meet/__init__.py#L50) | None identified |

### hermes-yuanbao

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `yb_query_group_info` | missing | No native registered implementation. | [tools/yuanbao_tools.py:492](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/yuanbao_tools.py#L492) | None identified |
| `yb_query_group_members` | missing | No native registered implementation. | [tools/yuanbao_tools.py:492](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/yuanbao_tools.py#L492) | None identified |
| `yb_search_sticker` | missing | No native registered implementation. | [tools/yuanbao_tools.py:492](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/yuanbao_tools.py#L492) | None identified |
| `yb_send_dm` | missing | No native registered implementation. | [tools/yuanbao_tools.py:492](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/yuanbao_tools.py#L492) | None identified |
| `yb_send_sticker` | missing | No native registered implementation. | [tools/yuanbao_tools.py:492](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/yuanbao_tools.py#L492) | None identified |

### homeassistant

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `ha_call_service` | missing | No native registered implementation. | [tools/homeassistant_tool.py:335](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/homeassistant_tool.py#L335) | None identified |
| `ha_get_state` | missing | No native registered implementation. | [tools/homeassistant_tool.py:335](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/homeassistant_tool.py#L335) | None identified |
| `ha_list_entities` | missing | No native registered implementation. | [tools/homeassistant_tool.py:335](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/homeassistant_tool.py#L335) | None identified |
| `ha_list_services` | missing | No native registered implementation. | [tools/homeassistant_tool.py:335](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/homeassistant_tool.py#L335) | None identified |

### image_gen

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `image_generate` | missing | No native registered implementation. | [tools/image_generation_tool.py:930](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/image_generation_tool.py#L930) | None identified |

### kanban

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `kanban_attach` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_attach_url` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_attachments` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_block` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_comment` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_complete` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_create` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_heartbeat` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_link` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_list` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_request_changes` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_request_review` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_schedule` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_show` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |
| `kanban_unblock` | missing | No native registered implementation. | [tools/kanban_tools.py:1314](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/kanban_tools.py#L1314) | None identified |

### memory

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `memory` | partial | Owner memory CRUD; single 4000-character store, no separate MEMORY.md/USER.md targets or upstream periodic curation. | [tools/memory_tool.py:429](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/memory_tool.py#L429) | tests/jvm/LocalFeaturesHarness.java |

### project

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `desktop_project` | platformblocked | Desktop renderer/pane/window contract absent on Android. Requires a deliberate native surface adaptation; Android screen tools do not implement this exact API. | [tools/project_tools.py:164](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/project_tools.py#L164) | None identified |

### session_search

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `session_search` | partial | Literal local excerpt search; lacks FTS5 ranked search and model-generated summaries. | [tools/session_search_tool.py:786](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/session_search_tool.py#L786) | tests/jvm/LocalFeaturesHarness.java |

### setup

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `manage_catalog` | missing | No native registered implementation. | [tools/connectors/tool.py:150](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/connectors/tool.py#L150) | None identified |

### skills

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `skill_manage` | partial | Native runtime now dispatches canonical atomic create/patch/write_file/remove_file/delete operation arrays after transaction preview and approval; full SKILL.md documents/resources and owner ZIP import/export work. Category/plugin namespaces, hub/sync/lint/security scan, dependency installation and full YAML syntax remain gaps. | [tools/skill_manager_tool.py:924](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/skill_manager_tool.py#L924) | tests/jvm/LocalFeaturesHarness.java |
| `skill_view` | partial | Native full SKILL.md/filepath reads, linked paths, UTF-8/base64 resources and hash-verified original catalog reads. Import/read/export preserve arbitrary safe relative original paths; author write/remove remains restricted to allowed resource directories. Preprocessing, full YAML syntax, category/plugin/cross-profile lookup and dependency readiness remain gaps. | [tools/skills_tool.py:760](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/skills_tool.py#L760) | tests/jvm/LocalFeaturesHarness.java |
| `skills_list` | partial | Native local package discovery plus original 210-package bundled catalog. Preserved original categories are catalog metadata; installed category tree/plugin/cross-profile lookup remains absent. | [tools/skills_tool.py:727](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/skills_tool.py#L727) | tests/jvm/LocalFeaturesHarness.java |

### spotify

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `spotify_albums` | missing | No native registered implementation. | [plugins/spotify/__init__.py:31](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/spotify/__init__.py#L31) | None identified |
| `spotify_devices` | missing | No native registered implementation. | [plugins/spotify/__init__.py:31](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/spotify/__init__.py#L31) | None identified |
| `spotify_library` | missing | No native registered implementation. | [plugins/spotify/__init__.py:31](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/spotify/__init__.py#L31) | None identified |
| `spotify_playback` | missing | No native registered implementation. | [plugins/spotify/__init__.py:31](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/spotify/__init__.py#L31) | None identified |
| `spotify_playlists` | missing | No native registered implementation. | [plugins/spotify/__init__.py:31](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/spotify/__init__.py#L31) | None identified |
| `spotify_queue` | missing | No native registered implementation. | [plugins/spotify/__init__.py:31](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/spotify/__init__.py#L31) | None identified |
| `spotify_search` | missing | No native registered implementation. | [plugins/spotify/__init__.py:31](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/spotify/__init__.py#L31) | None identified |

### terminal

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `process_manage` | partial | Managed native jobs poll/wait/log/list/write/submit/kill; upstream PTY/process persistence/send-keys semantics differ. | [tools/process_registry.py:2892](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/process_registry.py#L2892) | tests/jvm/TerminalSessionsHarness.java |
| `terminal` | partial | Real /system/bin/sh under app UID/Shizuku/root; PTY absent, Android lifecycle/background ownership differs; no Python upstream backend execution. | [tools/terminal_tool.py:1631](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/terminal_tool.py#L1631) | tests/jvm/TerminalToolsHarness.java |

### todo

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `todo_list` | missing | No native registered implementation. | [tools/todo_tool.py:319](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/todo_tool.py#L319) | None identified |

### tts

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `text_to_speech` | missing | No native registered implementation. | [tools/tts_tool.py:648](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/tts_tool.py#L648) | None identified |

### video

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `video_analyze` | missing | No native registered implementation. | [tools/vision_tools.py:1182](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/vision_tools.py#L1182) | None identified |

### video_gen

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `video_generate` | missing | No native registered implementation. | [tools/video_generation_tool.py:370](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/video_generation_tool.py#L370) | None identified |
| `xai_video_edit` | missing | No native registered implementation. | [tools/xai_video_tools.py:122](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/xai_video_tools.py#L122) | None identified |
| `xai_video_extend` | missing | No native registered implementation. | [tools/xai_video_tools.py:122](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/xai_video_tools.py#L122) | None identified |

### vision

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `vision_analyze` | partial | Actual screen image can be attached to configured vision-capable chat model; arbitrary image vision_analyze tool/schema not registered. | [tools/vision_tools.py:1027](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/vision_tools.py#L1027) | tests/jvm/ScreenVisionHarness.java |

### web

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `web_extract` | partial | Android equivalent named web_fetch; Tavily extraction requires API key. Upstream exact tool name/schema absent. | [tools/web_tools.py:558](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/web_tools.py#L558) | tests/jvm/LocalFeaturesHarness.java |
| `web_search` | partial | Native Mwmbl/Tavily/SearXNG search; does not expose all upstream registered search backends. Configured backend may need API key/endpoint. | [tools/web_tools.py:552](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/web_tools.py#L552) | tests/jvm/LocalFeaturesHarness.java |

### x_search

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `x_search` | missing | No native registered implementation. | [tools/x_search_tool.py:339](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/x_search_tool.py#L339) | None identified |

## Dynamic registrations

- **MCP-discovered tools**: Names depend on connected server advertisements. Native status: missing. [tools/mcp_tool_registration.py:348](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/tools/mcp_tool_registration.py#L348)
- **Context-engine tools**: Names supplied by selected engine. Native status: missing. [agent/context_engine.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/agent/context_engine.py#L1)
- **Third-party plugin tools**: Names supplied by installed register(ctx) entrypoints. Native status: missing. [hermes_cli/plugins.py:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/hermes_cli/plugins.py#L1)

## Every gateway enum name

| Feature / exact name | Status | Android mapping and limits | Reference | Native tests (existing, not run) |
|---|---|---|---|---|
| `local` | partial | Native local conversation, not original gateway. | [gateway/config.py:220](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L220) | None identified |
| `telegram` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:221](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L221) | None identified |
| `discord` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:222](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L222) | None identified |
| `whatsapp` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:223](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L223) | None identified |
| `whatsapp_cloud` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:224](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L224) | None identified |
| `slack` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:225](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L225) | None identified |
| `signal` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:226](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L226) | None identified |
| `mattermost` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:227](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L227) | None identified |
| `matrix` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:228](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L228) | None identified |
| `homeassistant` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:229](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L229) | None identified |
| `email` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:230](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L230) | None identified |
| `sms` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:231](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L231) | None identified |
| `dingtalk` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:232](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L232) | None identified |
| `api_server` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:233](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L233) | None identified |
| `webhook` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:234](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L234) | None identified |
| `msgraph_webhook` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:235](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L235) | None identified |
| `feishu` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:236](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L236) | None identified |
| `wecom` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:237](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L237) | None identified |
| `wecom_callback` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:238](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L238) | None identified |
| `weixin` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:239](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L239) | None identified |
| `bluebubbles` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:240](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L240) | None identified |
| `qqbot` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:241](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L241) | None identified |
| `yuanbao` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:242](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L242) | None identified |
| `relay` | missing | No native original messaging adapter; service credentials/permissions plus implementation required. | [gateway/config.py:243](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/gateway/config.py#L243) | None identified |

Bundled plugin platform names below supplement the enum (e.g. IRC, Teams, LINE, A2A). Their presence in source is not Android connectivity.

## Every bundled plugin manifest

### browser

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `browser-browser-use` | [plugins/browser/browser_use/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/browser/browser_use/plugin.yaml#L1) | missing | Browser Use (https://browser-use.com) cloud browser backend. Supports both direct BROWSER_USE_API_KEY and the managed Nous tool gateway. Also powers the 'Nous Subscription' UX flow that bills usage to a Nous subscription. |
| `browser-browserbase` | [plugins/browser/browserbase/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/browser/browserbase/plugin.yaml#L1) | missing | Browserbase (https://browserbase.com) cloud browser backend. Requires BROWSERBASE_API_KEY + BROWSERBASE_PROJECT_ID. Supports stealth, proxies, and keep-alive sessions; auto-falls-back when paid features are unavailable. |
| `browser-firecrawl` | [plugins/browser/firecrawl/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/browser/firecrawl/plugin.yaml#L1) | missing | Firecrawl (https://firecrawl.dev) cloud browser backend. Requires FIRECRAWL_API_KEY. Distinct from the firecrawl WEB search/extract plugin — the two share an API key but operate on different endpoints. |

### cron_providers

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `chronos` | [plugins/cron_providers/chronos/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/cron_providers/chronos/plugin.yaml#L1) | missing | >- |

### dashboard_auth

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `basic` | [plugins/dashboard_auth/basic/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/dashboard_auth/basic/plugin.yaml#L1) | missing | Dashboard auth provider — username/password (no OAuth IDP). A self-hosted 'just put a password on my dashboard' provider. Activates when dashboard.basic_auth.username plus a password (or password_hash) are configured via config.yaml (canonical surface) or the HERMES_DASHBOARD_BASIC_AUTH_* env vars. Sessions are stateless HMAC-signed tokens minted by the provider; password hashing uses stdlib scrypt (no third-party dependency). Set dashboard.basic_auth.secret for restart-surviving / multi-worker sessions. |
| `drain` | [plugins/dashboard_auth/drain/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/dashboard_auth/drain/plugin.yaml#L1) | missing | Dashboard auth provider — non-interactive shared-bearer-secret for the gateway drain-control endpoint. The first consumer of the generic token-auth capability (supports_token/verify_token). nous-account-service provisions a per-agent unique secret via HERMES_DASHBOARD_DRAIN_SECRET; this provider verifies an inbound Authorization bearer token against it with a constant-time compare and registers /api/gateway/drain as token-authable. Fails CLOSED: a weak/short/low-entropy secret (< 256 bits) is rejected at registration and the endpoint stays disabled. No-op when the env var is unset. Behavioural knobs (scope, min_secret_chars) live under dashboard.drain_auth in config.yaml. |
| `nous` | [plugins/dashboard_auth/nous/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/dashboard_auth/nous/plugin.yaml#L1) | missing | Dashboard auth provider — OAuth 2.0 (authorization-code + PKCE) against Nous Portal. Auto-activates when a client_id is configured via either dashboard.oauth.client_id in config.yaml (canonical surface) or HERMES_DASHBOARD_OAUTH_CLIENT_ID env var (operator override; Portal injects this at Fly.io provisioning). dashboard.oauth.portal_url / HERMES_DASHBOARD_PORTAL_URL are optional and default to https://portal.nousresearch.com. |
| `self-hosted` | [plugins/dashboard_auth/self_hosted/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/dashboard_auth/self_hosted/plugin.yaml#L1) | missing | Dashboard auth provider — generic self-hosted OpenID Connect (authorization-code + PKCE, public client). Works against any conformant OIDC identity provider (Authentik, Keycloak, Zitadel, Authelia, Auth0, Okta, Google, …) via OIDC discovery. Auto-activates when an issuer + client_id are configured, either under dashboard.oauth.self_hosted.{issuer,client_id} in config.yaml (canonical surface) or via the HERMES_DASHBOARD_OIDC_ISSUER + HERMES_DASHBOARD_OIDC_CLIENT_ID env vars (operator override / secret injection). Scopes default to 'openid profile email'. Verifies the OIDC ID token (RS256/ES256) against the discovered jwks_uri. |

### disk-cleanup

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `disk-cleanup` | [plugins/disk-cleanup/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/disk-cleanup/plugin.yaml#L1) | missing | Auto-track and clean up ephemeral files (test scripts, temp outputs, cron logs) created during Hermes sessions. Runs via plugin hooks — no agent action required. |

### google_meet

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `google_meet` | [plugins/google_meet/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/google_meet/plugin.yaml#L1) | missing | Join a Google Meet call, transcribe live captions, speak in realtime, and follow up afterwards. v1 transcribe-only is the default; v2 realtime duplex audio via OpenAI Realtime + BlackHole/PulseAudio ships with mode='realtime'; v3 remote node host lets the bot run on a different machine than the gateway (gateway on Linux, Chrome+signed-in profile on the user's Mac). Explicit-by-design: only joins meet.google.com URLs passed in \u2014 no calendar scanning, no auto-dial. |

### image_gen

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `deepinfra` | [plugins/image_gen/deepinfra/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/image_gen/deepinfra/plugin.yaml#L1) | missing | DeepInfra image generation backend (FLUX, Qwen-Image, …) via OpenAI-compatible /v1/images/generations. Catalog discovered live from api.deepinfra.com. |
| `fal` | [plugins/image_gen/fal/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/image_gen/fal/plugin.yaml#L1) | missing | FAL.ai image generation backend (flux-2-klein, flux-2-pro, nano-banana-2, nano-banana-pro, gpt-image-1.5, recraft-v3, etc.). |
| `krea` | [plugins/image_gen/krea/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/image_gen/krea/plugin.yaml#L1) | missing | Krea image generation backend (Krea 2 Large + Medium + Medium Turbo foundation models). Direct KREA_API_KEY or managed Nous Subscription gateway. |
| `meta-ai-image-gen` | [plugins/image_gen/meta-ai/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/image_gen/meta-ai/plugin.yaml#L1) | missing | Meta Model API image generation backend (muse-image). OpenAI-compatible /v1/images/generations. Saves images to $HERMES_HOME/cache/generated/images/. |
| `openai` | [plugins/image_gen/openai/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/image_gen/openai/plugin.yaml#L1) | missing | OpenAI image generation backend (GPT Image 2 and GPT Image 2.5 Flare/Sunburst). Saves generated images to $HERMES_HOME/cache/generated/images/. |
| `openai-codex` | [plugins/image_gen/openai-codex/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/image_gen/openai-codex/plugin.yaml#L1) | missing | OpenAI image generation backed by ChatGPT/Codex OAuth (gpt-image-2 via the native Codex images/generations and images/edits endpoints). Saves generated images to $HERMES_HOME/cache/generated/images/. |
| `openrouter` | [plugins/image_gen/openrouter/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/image_gen/openrouter/plugin.yaml#L1) | missing | OpenRouter + Nous Portal image generation. Chat-completions image output (reference-grounded) plus OpenRouter's Dedicated Image API (/images/generations) for gpt-image-2, Krea 2, Qwen Image 3 Pro, MAI-Image-2.5 and Grok Imagine — exact per-model aspect ratios, resolution/quality/background/seed/n, up to 16 reference images. Text-to-image and image-to-image. |
| `xai` | [plugins/image_gen/xai/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/image_gen/xai/plugin.yaml#L1) | missing | xAI image generation backend (grok-imagine-image). Text-to-image. |

### memory

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `byterover` | [plugins/memory/byterover/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/memory/byterover/plugin.yaml#L1) | missing | ByteRover — persistent knowledge tree with tiered retrieval via the brv CLI. |
| `holographic` | [plugins/memory/holographic/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/memory/holographic/plugin.yaml#L1) | missing | Holographic memory — local SQLite fact store with FTS5 search, trust scoring, and HRR-based compositional retrieval. |
| `mem0` | [plugins/memory/mem0/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/memory/mem0/plugin.yaml#L1) | missing | Mem0 — server-side LLM fact extraction with semantic search, automatic deduplication, and opt-in reranking (platform mode). |
| `openviking` | [plugins/memory/openviking/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/memory/openviking/plugin.yaml#L1) | missing | OpenViking context database — session-managed memory with automatic extraction, tiered retrieval, and filesystem-style knowledge browsing. |
| `retaindb` | [plugins/memory/retaindb/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/memory/retaindb/plugin.yaml#L1) | missing | RetainDB — cloud memory API with hybrid search and 7 memory types. |

### model-providers

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `actual-provider` | [plugins/model-providers/actual/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/actual/plugin.yaml#L1) | missing | Actual Computer inference |
| `ai-gateway-provider` | [plugins/model-providers/ai-gateway/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/ai-gateway/plugin.yaml#L1) | missing | Vercel AI Gateway |
| `alibaba-provider` | [plugins/model-providers/alibaba/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/alibaba/plugin.yaml#L1) | missing | Alibaba DashScope (international) |
| `alibaba-coding-plan-provider` | [plugins/model-providers/alibaba-coding-plan/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/alibaba-coding-plan/plugin.yaml#L1) | missing | Alibaba Cloud Coding Plan |
| `anthropic-provider` | [plugins/model-providers/anthropic/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/anthropic/plugin.yaml#L1) | missing | Anthropic (Claude) |
| `arcee-provider` | [plugins/model-providers/arcee/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/arcee/plugin.yaml#L1) | missing | Arcee AI |
| `azure-foundry-provider` | [plugins/model-providers/azure-foundry/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/azure-foundry/plugin.yaml#L1) | missing | Microsoft Foundry |
| `bedrock-provider` | [plugins/model-providers/bedrock/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/bedrock/plugin.yaml#L1) | missing | AWS Bedrock |
| `commandcode-provider` | [plugins/model-providers/commandcode/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/commandcode/plugin.yaml#L1) | missing | CommandCode — unified multi-model API (OpenAI chat completions + Anthropic Messages) |
| `copilot-provider` | [plugins/model-providers/copilot/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/copilot/plugin.yaml#L1) | missing | GitHub Copilot |
| `copilot-acp-provider` | [plugins/model-providers/copilot-acp/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/copilot-acp/plugin.yaml#L1) | missing | GitHub Copilot via ACP subprocess |
| `custom-provider` | [plugins/model-providers/custom/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/custom/plugin.yaml#L1) | missing | Custom / Ollama / local OpenAI-compatible endpoint |
| `deepinfra-provider` | [plugins/model-providers/deepinfra/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/deepinfra/plugin.yaml#L1) | missing | DeepInfra — 100+ open models, pay-per-use |
| `deepseek-provider` | [plugins/model-providers/deepseek/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/deepseek/plugin.yaml#L1) | missing | DeepSeek |
| `fireworks-provider` | [plugins/model-providers/fireworks/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/fireworks/plugin.yaml#L1) | missing | Fireworks AI — fast inference for open and proprietary models |
| `gemini-provider` | [plugins/model-providers/gemini/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/gemini/plugin.yaml#L1) | missing | Google Gemini (API key + Cloud Code OAuth) |
| `gmi-provider` | [plugins/model-providers/gmi/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/gmi/plugin.yaml#L1) | missing | GMI Cloud |
| `huggingface-provider` | [plugins/model-providers/huggingface/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/huggingface/plugin.yaml#L1) | missing | HuggingFace Inference Providers |
| `kilocode-provider` | [plugins/model-providers/kilocode/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/kilocode/plugin.yaml#L1) | missing | Kilo Code |
| `kimi-coding-provider` | [plugins/model-providers/kimi-coding/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/kimi-coding/plugin.yaml#L1) | missing | Moonshot Kimi Coding (global + China) |
| `meta-ai-provider` | [plugins/model-providers/meta-ai/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/meta-ai/plugin.yaml#L1) | missing | Meta Model API — Muse Spark family (Meta Superintelligence Labs) |
| `minimax-provider` | [plugins/model-providers/minimax/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/minimax/plugin.yaml#L1) | missing | MiniMax M-series (global + China + OAuth) |
| `nebius-token-factory-provider` | [plugins/model-providers/nebius-token-factory/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/nebius-token-factory/plugin.yaml#L1) | missing | Nebius Token Factory OpenAI-compatible inference |
| `nous-provider` | [plugins/model-providers/nous/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/nous/plugin.yaml#L1) | missing | Nous Research Portal |
| `novita-provider` | [plugins/model-providers/novita/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/novita/plugin.yaml#L1) | missing | NovitaAI AI-native cloud for builders and agents |
| `nvidia-provider` | [plugins/model-providers/nvidia/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/nvidia/plugin.yaml#L1) | missing | NVIDIA NIM |
| `ollama-cloud-provider` | [plugins/model-providers/ollama-cloud/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/ollama-cloud/plugin.yaml#L1) | missing | Ollama Cloud |
| `openai-codex-provider` | [plugins/model-providers/openai-codex/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/openai-codex/plugin.yaml#L1) | missing | OpenAI Codex (Responses API) |
| `opencode-zen-provider` | [plugins/model-providers/opencode-zen/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/opencode-zen/plugin.yaml#L1) | missing | OpenCode (Zen + Go) |
| `openrouter-provider` | [plugins/model-providers/openrouter/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/openrouter/plugin.yaml#L1) | missing | OpenRouter aggregator |
| `qwen-oauth-provider` | [plugins/model-providers/qwen-oauth/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/qwen-oauth/plugin.yaml#L1) | missing | Qwen Portal (OAuth) |
| `router-provider` | [plugins/model-providers/router/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/router/plugin.yaml#L1) | missing | Ramp Router (router.com) — OpenAI Responses-compatible LLM gateway |
| `stepfun-provider` | [plugins/model-providers/stepfun/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/stepfun/plugin.yaml#L1) | missing | StepFun Step Plan |
| `upstage-provider` | [plugins/model-providers/upstage/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/upstage/plugin.yaml#L1) | missing | Upstage (Solar API) |
| `vertex-provider` | [plugins/model-providers/vertex/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/vertex/plugin.yaml#L1) | missing | Google Vertex AI (Gemini via OpenAI-compatible endpoint, OAuth2) |
| `xai-provider` | [plugins/model-providers/xai/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/xai/plugin.yaml#L1) | missing | xAI Grok (Responses API) |
| `xiaomi-provider` | [plugins/model-providers/xiaomi/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/xiaomi/plugin.yaml#L1) | missing | Xiaomi MiMo |
| `zai-provider` | [plugins/model-providers/zai/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/model-providers/zai/plugin.yaml#L1) | missing | Z.AI / GLM |

### observability

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `langfuse` | [plugins/observability/langfuse/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/observability/langfuse/plugin.yaml#L1) | missing | Optional Langfuse observability for Hermes — traces conversations, LLM calls, and tool usage. Opt-in via `hermes plugins enable observability/langfuse` or `hermes tools → Langfuse Observability`. |

### platforms

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `a2a-platform` | [plugins/platforms/a2a/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/a2a/plugin.yaml#L1) | missing | > |
| `buzz-platform` | [plugins/platforms/buzz/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/buzz/plugin.yaml#L1) | missing | > |
| `dingtalk-platform` | [plugins/platforms/dingtalk/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/dingtalk/plugin.yaml#L1) | missing | > |
| `discord-platform` | [plugins/platforms/discord/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/discord/plugin.yaml#L1) | missing | > |
| `email-platform` | [plugins/platforms/email/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/email/plugin.yaml#L1) | missing | > |
| `feishu-platform` | [plugins/platforms/feishu/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/feishu/plugin.yaml#L1) | missing | > |
| `google_chat-platform` | [plugins/platforms/google_chat/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/google_chat/plugin.yaml#L1) | missing | > |
| `homeassistant-platform` | [plugins/platforms/homeassistant/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/homeassistant/plugin.yaml#L1) | missing | > |
| `irc-platform` | [plugins/platforms/irc/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/irc/plugin.yaml#L1) | missing | > |
| `line-platform` | [plugins/platforms/line/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/line/plugin.yaml#L1) | missing | > |
| `matrix-platform` | [plugins/platforms/matrix/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/matrix/plugin.yaml#L1) | missing | > |
| `mattermost-platform` | [plugins/platforms/mattermost/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/mattermost/plugin.yaml#L1) | missing | > |
| `ntfy-platform` | [plugins/platforms/ntfy/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/ntfy/plugin.yaml#L1) | missing | > |
| `photon-platform` | [plugins/platforms/photon/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/photon/plugin.yaml#L1) | missing | > |
| `raft-platform` | [plugins/platforms/raft/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/raft/plugin.yaml#L1) | missing | > |
| `simplex-platform` | [plugins/platforms/simplex/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/simplex/plugin.yaml#L1) | missing | > |
| `slack-platform` | [plugins/platforms/slack/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/slack/plugin.yaml#L1) | missing | > |
| `sms-platform` | [plugins/platforms/sms/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/sms/plugin.yaml#L1) | missing | > |
| `teams-platform` | [plugins/platforms/teams/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/teams/plugin.yaml#L1) | missing | > |
| `telegram-platform` | [plugins/platforms/telegram/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/telegram/plugin.yaml#L1) | missing | > |
| `wecom-platform` | [plugins/platforms/wecom/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/wecom/plugin.yaml#L1) | missing | > |
| `whatsapp-platform` | [plugins/platforms/whatsapp/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/platforms/whatsapp/plugin.yaml#L1) | missing | > |

### security-guidance

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `security-guidance` | [plugins/security-guidance/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/security-guidance/plugin.yaml#L1) | missing | Append security warnings to file-write tool results when the new content contains known-dangerous patterns (pickle.load, yaml.load, eval(, os.system, dangerouslySetInnerHTML, verify=False, ECB, XXE, GitHub Actions injection, ...). 25 regex/substring rules forked from Anthropic's claude-plugins-official under Apache-2.0. Non-blocking — the file is written and the warning rides back to the model in the next turn so it can self-correct. |

### spotify

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `spotify` | [plugins/spotify/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/spotify/plugin.yaml#L1) | missing | Native Spotify integration — 7 tools (playback, devices, queue, search, playlists, albums, library) using Spotify Web API + PKCE OAuth. Auth via `hermes auth spotify`. Tools gate on `providers.spotify` in ~/.hermes/auth.json. |

### teams_pipeline

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `teams_pipeline` | [plugins/teams_pipeline/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/teams_pipeline/plugin.yaml#L1) | missing | Microsoft Teams meeting pipeline plugin with durable runtime state and operator CLI flows for Graph-backed transcript-first meeting summaries. |

### video_gen

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `deepinfra` | [plugins/video_gen/deepinfra/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/video_gen/deepinfra/plugin.yaml#L1) | missing | DeepInfra video generation (text-to-video & image-to-video) via the OpenAI-compatible /v1/openai/videos endpoint. Catalog discovered live from api.deepinfra.com. |
| `fal` | [plugins/video_gen/fal/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/video_gen/fal/plugin.yaml#L1) | missing | FAL.ai video generation backend. Multi-model — Veo 3.1, Kling, Pixverse — covering text-to-video and image-to-video via fal_client's queue API. |
| `openrouter` | [plugins/video_gen/openrouter/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/video_gen/openrouter/plugin.yaml#L1) | missing | OpenRouter video generation backend. Every generative model on the /api/v1/videos API (Veo, Sora, Kling, Seedance, Wan, Hailuo, Grok Imagine, FLUX Video, …) — text-to-video, image-to-video and reference-to-video; catalog and per-model limits discovered live. |
| `xai` | [plugins/video_gen/xai/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/video_gen/xai/plugin.yaml#L1) | missing | xAI Grok Imagine video generation backend. Supports text-to-video, image-to-video, reference-to-video, video editing, video extension, and stored public URLs via the xAI async videos API. |

### web

| Name | Reference | Status | Upstream purpose |
|---|---|---|---|
| `web-brave-free` | [plugins/web/brave_free/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/brave_free/plugin.yaml#L1) | missing | Brave Search (free tier) — web search via Brave's Data-for-Search API. Requires BRAVE_SEARCH_API_KEY (free signup at https://brave.com/search/api/, 2k queries/month). |
| `web-ddgs` | [plugins/web/ddgs/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/ddgs/plugin.yaml#L1) | missing | DuckDuckGo web search via the ddgs Python package — no API key required. Install with `pip install ddgs`. |
| `web-exa` | [plugins/web/exa/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/exa/plugin.yaml#L1) | missing | Exa web search and content extraction. Requires EXA_API_KEY — sign up at https://exa.ai. |
| `web-firecrawl` | [plugins/web/firecrawl/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/firecrawl/plugin.yaml#L1) | missing | Firecrawl web search + content extraction. Supports keyless cloud, direct API, and Nous-hosted tool-gateway routing for subscribers. |
| `web-keenable` | [plugins/web/keenable/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/keenable/plugin.yaml#L1) | missing | Keenable web search + page fetch (independent web index for AI apps). Works keyless on Keenable's free tier as part of the default rotation; set KEENABLE_API_KEY for higher limits — https://keenable.ai. |
| `web-openai-native` | [plugins/web/openai_native/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/openai_native/plugin.yaml#L1) | missing | OpenAI native web search — declares the Responses API server-side ``web_search`` built-in instead of running a client-side search. Requires the Codex Responses transport plus openai-codex OAuth (``hermes auth add openai-codex``). |
| `web-parallel` | [plugins/web/parallel/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/parallel/plugin.yaml#L1) | missing | Parallel.ai web search + content extraction. Search returns objective-tuned results; extract uses the async SDK for parallel page fetches. Requires PARALLEL_API_KEY — sign up at https://parallel.ai. |
| `web-perplexity` | [plugins/web/perplexity/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/perplexity/plugin.yaml#L1) | missing | Perplexity Search API web search and query-relevant page snippets. Requires PERPLEXITY_API_KEY — get one at https://www.perplexity.ai/account/api. |
| `web-searxng` | [plugins/web/searxng/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/searxng/plugin.yaml#L1) | missing | SearXNG web search — free, self-hosted, privacy-respecting metasearch engine. Requires SEARXNG_URL pointing at your instance. |
| `web-tavily` | [plugins/web/tavily/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/tavily/plugin.yaml#L1) | missing | Tavily web search + extract. Opt-in keyless via hermes tools; set TAVILY_API_KEY for higher limits — https://app.tavily.com/home. |
| `web-xai` | [plugins/web/xai/plugin.yaml:1](../engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/plugins/web/xai/plugin.yaml#L1) | missing | xAI Web Search — search the web via Grok's agentic web_search tool (Responses API). Requires xAI Grok OAuth (via `hermes auth`) or XAI_API_KEY (https://x.ai). |

## Upstream skill documents

These are instruction packages, not executable tool registrations. Installing a Markdown document does not port its Python scripts, Docker/SSH dependencies, desktop software, account authorization or provider API. Native ships its `android-terminal` guidance plus owner-created documents and now preserves all 210 original packages in an offline catalog. Every original document/resources package remains visible/readable; current catalog marks 209 importable and 1 blocked by disclosed native limits. Individual importability and blockers appear in JSON. Installing does not execute scripts or establish original runtime/API capability.

### skills

`apple/apple-notes`, `apple/apple-reminders`, `apple/findmy`, `apple/imessage`, `autonomous-ai-agents/claude-code`, `autonomous-ai-agents/codex`, `autonomous-ai-agents/computer-use`, `autonomous-ai-agents/hermes-agent`, `autonomous-ai-agents/opencode`, `creative/architecture-diagram`, `creative/ascii-video`, `creative/baoyu-infographic`, `creative/claude-design`, `creative/design-md`, `creative/humanizer`, `creative/manim-video`, `creative/p5js`, `creative/popular-web-designs`, `creative/songwriting-and-ai-music`, `devops/sdlc-review`, `email/email-inbox-triage`, `email/himalaya`, `media/gif-search`, `media/songsee`, `media/youtube-content`, `note-taking/obsidian`, `productivity/airtable`, `productivity/box`, `productivity/document-to-action-items`, `productivity/docx`, `productivity/google-workspace`, `productivity/maps`, `productivity/meeting-action-items`, `productivity/notion`, `productivity/pdf`, `productivity/powerpoint`, `productivity/product-price-monitor`, `productivity/teams-meeting-pipeline`, `productivity/weekly-review-planning`, `productivity/xlsx`, `research/arxiv`, `research/competitor-news-monitor`, `research/grounded-citations`, `research/llm-wiki`, `social-media/xurl`, `software-development/codebase-inspection`, `software-development/dogfood`, `software-development/github`, `software-development/hermes-agent-skill-authoring`, `software-development/inspecting-hermes-desktop-dom`, `software-development/node-inspect-debugger`, `software-development/python-debugpy`, `software-development/requesting-code-review`, `software-development/simplify-code`, `software-development/spike`, `software-development/systematic-debugging`, `software-development/test-driven-development`, `web/blocked-page-recovery`

### optional-skills

`autonomous-ai-agents/agent-merge-conflict-arbiter`, `autonomous-ai-agents/antigravity-cli`, `autonomous-ai-agents/blackbox`, `autonomous-ai-agents/dynamic-workflow`, `autonomous-ai-agents/grok`, `autonomous-ai-agents/honcho`, `autonomous-ai-agents/openhands`, `blockchain/evm`, `blockchain/hyperliquid`, `blockchain/solana`, `communication/one-three-one-rule`, `creative/ai-presenter-video`, `creative/archify`, `creative/ascii-art`, `creative/audiocraft-audio-generation`, `creative/auteur`, `creative/baoyu-article-illustrator`, `creative/baoyu-comic`, `creative/brag`, `creative/brag-slim`, `creative/comfyui`, `creative/concept-diagrams`, `creative/creative-ideation`, `creative/draw-your-font`, `creative/dream-loop`, `creative/excalidraw`, `creative/heartmula`, `creative/hyperframes`, `creative/impeccable`, `creative/ip-as-logo`, `creative/kanban-video-orchestrator`, `creative/meme-generation`, `creative/mono-color`, `creative/pixel-art`, `creative/pretext`, `creative/simple-english`, `creative/sketch`, `creative/social-media-content-calendar`, `creative/system-atlas`, `creative/tldraw-offline`, `creative/unreal-mcp`, `data-science/jupyter-notebook`, `devops/actual-setup`, `devops/docker-management`, `devops/hermes-s6-container-supervision`, `devops/inference-sh-cli`, `devops/pinggy-tunnel`, `devops/setup-wizard-generator`, `devops/watchers`, `dogfood/adversarial-ux-test`, `email/agentmail`, `finance/3-statement-model`, `finance/comps-analysis`, `finance/dcf-model`, `finance/excel-author`, `finance/lbo-model`, `finance/merger-model`, `finance/polymarket`, `finance/pptx-author`, `finance/stocks`, `gaming/minecraft-modpack-server`, `gaming/pokemon-player`, `health/fitness-nutrition`, `health/neuroskill-bci`, `mcp/fastmcp`, `mcp/mcp-oauth-remote-gateway`, `mcp/mcporter`, `migration/openclaw-migration`, `mlops/accelerate`, `mlops/chroma`, `mlops/clip`, `mlops/evaluation/evaluating-llms-harness`, `mlops/evaluation/weights-and-biases`, `mlops/faiss`, `mlops/flash-attention`, `mlops/guidance`, `mlops/huggingface-tokenizers`, `mlops/inference/llama-cpp`, `mlops/inference/outlines`, `mlops/inference/serving-llms-vllm`, `mlops/instructor`, `mlops/lambda-labs`, `mlops/llava`, `mlops/modal`, `mlops/models/huggingface-hub`, `mlops/models/segment-anything-model`, `mlops/nemo-curator`, `mlops/obliteratus`, `mlops/peft`, `mlops/pinecone`, `mlops/pytorch-fsdp`, `mlops/pytorch-lightning`, `mlops/qdrant`, `mlops/research/dspy`, `mlops/saelens`, `mlops/simpo`, `mlops/slime`, `mlops/stable-diffusion`, `mlops/tensorrt-llm`, `mlops/torchtitan`, `mlops/training/axolotl`, `mlops/training/trl-fine-tuning`, `mlops/training/unsloth`, `mlops/whisper`, `payments/mpp-agent`, `payments/stripe-link-cli`, `payments/stripe-projects`, `productivity/canvas`, `productivity/decision-questionnaire`, `productivity/here-now`, `productivity/live-dashboard`, `productivity/memento-flashcards`, `productivity/property-listings`, `productivity/shop`, `productivity/shopify`, `productivity/siyuan`, `productivity/telephony`, `research/bioinformatics`, `research/blogwatcher`, `research/darwinian-evolver`, `research/domain-intel`, `research/drug-discovery`, `research/duckduckgo-search`, `research/gitnexus-explorer`, `research/osint-investigation`, `research/parallel-cli`, `research/pinecone-research`, `research/qmd`, `research/research-paper-writing`, `research/rss-feeds`, `research/scrapling`, `research/searxng-search`, `security/1password`, `security/godmode`, `security/oss-forensics`, `security/sherlock`, `security/unbroker`, `security/web-pentest`, `smart-home/openhue`, `social-media/reddit-reading`, `software-development/ast-grep`, `software-development/code-wiki`, `software-development/grill-me`, `software-development/pr-lens`, `software-development/rest-graphql-debug`, `software-development/subagent-driven-development`, `web-development/cloudflare-temporary-deploy`, `web-development/har-derived-api-client`, `web-development/page-agent`, `web-development/publish-site`, `web-development/scrollcraft`, `yuanbao`

## Android-native additions and immediate implementation priorities

The native Android surface additionally provides device state/app/settings/volume/brightness, accessibility screen observation/actions, screenshot vision transport, app control approvals, root/Shizuku capability probes, and SAF folder access. Those are Android additions/adaptations, not evidence that unrelated original Hermes tools work.

1. Add real `todo_list`, `clarify`, `web_extract` and explicit SAF-scoped `read_file`/`write_file`/`patch`/`search_files` contracts with approval and permission boundaries.
2. Complete remaining SKILL.md category/plugin/hub/preprocess/dependency behavior, separate durable memory/user profile, and ranked session recall; current native resource CRUD/atomic operations/import/export do not imply script portability.
3. Implement durable Android scheduling with timezone/boot/lifecycle limits; then isolated native subagent delegation and kanban as actual systems.
4. Port browser/media/Home Assistant/MCP/connector/provider integrations only with working protocol adapters and real account/API configuration. Keep every absent item visible.
5. Keep Python `execute_code`, original PTY, Docker/Singularity, SSH, cloud sandboxes, desktop panes and Google Meet routing explicit until actual executable adapters exist. APK packaging of a source file is not execution.

This initial inventory is a source-grounded baseline while parallel native changes are in progress. Refresh status only after inspecting merged implementation and meaningful verification; never change missing to implemented merely because a placeholder dispatch branch was added.

## v010 native progress visibility and updated evidence

Native `Store.recordActivity` persists public assistant summaries and tool lifecycle events; `AgentRuntime` attaches run/session/tool-call IDs and emits started, approval-waiting, completed, failed and cancelled states. This gives an owner a durable activity timeline. Public summaries and tool result summaries are different from model-internal reasoning: no original hidden reasoning implementation or trace parity is claimed. Existing history/loading and UI behavior require their own meaningful runtime/UI checks.

`LocalAgentTools` now wires canonical atomic skill operation arrays to approved native transactions. Owner import/export and resource reads preserve full SKILL.md packages without executing resource scripts. The skill owner reports **10 passing isolated Java SkillPackagesHarness tests**, including binary ZIP roundtrip, traversal/symlink rejection, package bounds, atomic batch rollback and concurrent owner-edit protection. This documentation agent did not rerun those tests. Other listed harnesses remain existing verification targets unless separately reported by their owner.

The original Python engine execution proof is still **untested** here. Source pinning, a preserved original skill catalog, native timeline visibility, and native skill package tests do not establish that upstream Hermes `run_agent.py` executes on Android or that all original tools work.

Final catalog contract: **210 packages, 209 importable, 1 blocked**. `optional-skills/mlops/training/unsloth` contains `references/llms-full.md` at 1,077,327 bytes, exceeding the 1 MiB per-file bound. Native import/read/export now preserves original safe root/nested LICENSE, README, examples and other resources; author mutation still follows allowed resource directories. Catalog installability is package compatibility, not proof of original scripts or external services working.

The library owner additionally reports final real-JVM helper/store/importer verification: 210 original documents read, 209 individually imported, 1,086 imported files matched source SHA-256, the oversized package rejected, and all 209 duplicate installs rejected. **The native store allows 256 installed skills per store**; this provides capacity for all 209 individually eligible original packages plus custom skills. No automatic installation/activation or all-at-once installation verification is claimed. These are reported package verification results, not an original Python engine execution test.

## Ordinary owner Android phone control audit

The current native Accessibility/Shizuku gaps and exact policy gates are documented in [HERMES_ANDROID_HUMAN_CONTROL_AUDIT.md](HERMES_ANDROID_HUMAN_CONTROL_AUDIT.md). The largest immediate gap is blanket Settings/SystemUI exclusion, which blocks benign Settings GUI after `open_settings` succeeds. Existing Wi-Fi/process/force-stop already fall back to actual Shizuku shell; Root-only tool descriptions overstate their requirement. Proposed expansions are specific surface policies, global/element actions, ordinary typed settings, Bluetooth adapter/readback and refined system-app eligibility. Platform sources were checked; no device/tests/credentials were touched and no Root-equivalence claim is made.
