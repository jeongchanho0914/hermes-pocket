# Hermes Pocket v0.05 actual Android verification

Final APK SHA256: `7cc7e0c63dd2908860f311c05cc2a2261f29cab9ba121fbb14c759ebef876dce` (1,512,215 bytes). Installed the final v0.05 APK on Android 15 / API 35 emulator-5554. No physical phone was accessed. A separate same-signed test-only instrumentation APK exercised the actual production Activity, WebView, native bridge, AgentRuntime, SQLite, private skill files, and approval dialogs. It is not shipped in the release and is removed after testing.

All targeted checks passed:

- Real `chrome://crash` terminated the WebView renderer. Production callback created a different WebView; the old view was detached, the same Activity and AgentRuntime survived, and the recovered app completed a native boot roundtrip reporting v0.05. Intentional renderer-crash logs are retained as evidence.
- Actual production `stopAll()` sets Net cancelled. Actual readonly `localTool(get_device_state)` resets Net, settles busy, and writes a completed audit record. This proves the production entry path rather than relying on an early missing-key error.
- A real touch focused the composer and displayed Android IME. Clicking the app's actual new-chat button hid IME, cleared text, and left the input unfocused.
- Native approval denial preserves memory; approval saves an appended public fixture marker. SQLite session search finds actual prior conversation data.
- Approved local SKILL.md save, list, read, and selection succeed in private Android storage. Selected memory and skill markers appear in the actual outgoing model HTTP request, with low reasoning effort.
- Separate web-key save/clear preserves the model credential; no Tavily key produces an explicit error. Paid live Tavily search/extraction is not claimed.
- Fresh process restores memory, selected skill and SKILL.md file, model, low effort, and conversation history.

The model response is from a labeled local HTTP fixture, not a paid live account. Twenty registered tool schemas are verified in the request; this is not a claim that every device operation is permission-ready. See the separately labeled [v0.03 device boundary results](android-v003/device-boundaries.json) for actual read successes and permission/approval refusals.

Evidence: [machine report](android-v005/release-report.json), [renderer/Net lifecycle](android-v005/lifecycle.json), [actual IME test](android-v005/newchat-ime.json), [local-agent/approval results](android-v005/local-agent.json), [fresh-process persistence](android-v005/cold-persistence.json), [actual model HTTP requests](android-v005/api-fixture-requests.jsonl), [wire assertions](android-v005/wire-assertions.json), [crash buffer](android-v005/crash-buffer.txt). Credentials are omitted from request logs.
