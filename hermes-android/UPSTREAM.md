# Upstream / attribution

Current Android behavior: standalone Java agent loop with direct model API calls only.
The Android app does not connect to the Python adapter below. `server/` is retained
as legacy source/reference; its upstream integration notes describe that adapter.
The upstream Hermes Python engine, Skills, terminal, browser, and cron are not
embedded in the APK.


This is an unofficial Android integration for Nous Research's Hermes Agent.
It is not an official Nous Research Android release and does not embed the
Hermes Python runtime or model weights inside the Android package.

- Repository: https://github.com/NousResearch/hermes-agent
- Upstream license: MIT; Copyright (c) 2025 Nous Research.
- Integration reference checkout: `516535b54275e963a82b4c28f866338fb768e7bc`.
- A separate, prepared upstream source environment is required for Hermes mode.
- The runtime reports the selected checkout's actual revision separately from
  the adapter reference revision. Unknown revisions are reported as null.
- Direct mode is this project's Java tool-call loop, not the upstream Python agent.

The adapter imports `run_agent.AIAgent`, registers a custom phone-only toolset,
uses `run_conversation`, and stores its returned message history. It does not
copy the upstream CLI, terminal, browser, cron, skills marketplace, voice mode,
or self-improvement behavior into the APK.

Primary references:

1. https://hermes-agent.nousresearch.com/docs/guides/python-library
2. https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server
3. https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
4. https://developer.android.com/tools/apksigner
5. https://source.android.com/docs/core/permissions/perms-allowlist

Android SDK/JDK binaries are not included. Their own licenses apply.
A locally generated development signing key must not be uploaded or shared.
