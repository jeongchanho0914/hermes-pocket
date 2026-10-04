# Hermes Pocket v0.11 verification

APK: `dist/hermes-pocket-v0.11.apk`, 7,042,567 bytes; SHA-256 `d3e484dff19c480be498b414a50eeb3195220cf2111cd861d91a18a31a7b0d75`. Existing signing certificate, v1/v2/v3, manifest, ZIP integrity/resource-table alignment and build source-freeze checks pass.

## New behavior

MiMo's documented public `reasoning_content` stream appears separately in chat. It is bounded, credential-like literals are redacted, cumulative fragments update one run/round entry, and supported content persists separately from final messages and tool results. Other providers expose actual phases without undocumented private thought text. Model phases distinguish sending, receiving, thinking, tool preparation and answering; elapsed time follows actual events and stops when the request settles. This is external provider public output, not access to this coding assistant's hidden reasoning.

Native SQLite schema 3 uses additive 1→3 and 2→3 migrations. Existing messages, transcripts, audit and credentials remain separate. Public provider thoughts have explicit provider/source, session/run/round validation.

Eight ordinary phone tools were added: system surface actions, long-click, bounded range controls, and five standard Android intent actions. Existing volume, rotation and settings navigation gained typed options and actual value checks. Sensitive/unknown Settings or SystemUI surfaces remain protected; ordinary screen access requires trusted actual window/resource identities. Static Samsung resources are evidence of labels and IDs, not proof of visible runtime behavior.

## Verification status

Full production JVM: 129 checks passed. Seven additional checks compile actual SDK classes and exercise framework-free validators and schema inventory. Browser public thought targeted checks passed; complete Python coverage and native ordinary-control checks are finishing. No synthetic host result is treated as Android permission or touch proof.

Actual local SSE/native WebView fixture proves public MiMo thought visible before answer, collapsed expansion, elapsed progression/stopping, single cumulative persisted row, final messages separate, non-MiMo thought exclusion and reopening a prior session. Exact artifact and visible elapsed recheck are being finalized. Evidence: `android-v011/public-thought-actual-stream.json`.

## Delivery and limits

User now requests direct USB phone installation, with no further Gmail delivery. The final v0.10 APK was installed and its physical hash verified; v0.11 direct installation follows the current essential checks. Original Python engine integration and all 107 upstream tools remain unproven in production. Root UID 0 was not obtained. See `HERMES_FEATURE_MATRIX.md` and `HERMES_ANDROID_HUMAN_CONTROL_AUDIT.md`.

Previous verified release: `android-v010/TEST_REPORT.md`.
