# Security and scope

This is prototype source, not a security-audited privileged agent.

The model sees only thirteen typed Android tools. There is no generic shell,
file reader/writer, package installer or host terminal tool. Owner consent,
argument validation, package allowlists and execution live on the phone.
Neither model output nor the local WebView JS can call an approval endpoint.
The native confirmation uses a one-time nonce and a timeout. Snapshot actions
recheck the package, element and snapshot expiry at execution time.

The WebView serves only bundled assets and blocks arbitrary navigation,
file/content access and network resources. Provider traffic is made by Java,
not through an externally loaded web page. Model text is inserted as text nodes.
There is no embedded analytics or third-party UI/CDN script.

Root has a separate owner-enabled switch and a live UID probe. It does not mean
SELinux or OEM policy is disabled. The Root gateway selects fixed command
structures; it is not a shell interface exposed to the model.

The mobile server is single-owner, bearer authenticated and loopback-only by
default. It rejects browser Origin headers and duplicate conflicting requests.
Phone result IDs are idempotent within the server run. This is not a complete
multi-tenant service, internet deployment or distributed exactly-once protocol.

Known limitations requiring validation before wider use:

- No successful Android build/install test was completed in this session.
- No Android penetration/security review or OEM policy compatibility test.
- No guarantee that every sensitive screen in every third-party app is detected.
- Local SQLite records are not independently encrypted at rest.
- The PC credentials file is mode 600, not application-level encrypted storage.
- Screenshot, UIAutomator, generic coordinates, unattended approval, silent
  package installation, AOSP Binder service and root acquisition are absent.
- Background behavior, notification approval timing, cancellation races,
  transport reconnection, accessibility changes and provider SSE variations need
  Android instrumentation and real-device testing.
- Removing a phone session does not delete the server's history.
- The tool catalog intentionally replaces the wider Hermes tool surface.

Do not expose the plain HTTP adapter publicly. Do not use a personal primary
phone for initial Root testing. Keep signing keys, model keys and owner tokens
out of shared archives and repositories.
