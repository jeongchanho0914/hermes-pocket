# Ordinary Android app requests

`PhoneIntentTools.java` adds five typed native tools without Root or an external execution server. The saved device scope must be `all`, the device module must be enabled, the owner request must remain active and unlocked, and the current native approval policy applies. Conditions are checked before approval, afterward, and again on the Android main thread immediately before dispatch. User input is not written into audit records or injected into terminal environments.

| Tool | Actual Android operation | Honest completion boundary |
| --- | --- | --- |
| `open_link` | `ACTION_VIEW` / browsable HTTP(S) URL | Activity dispatch; page load unverified |
| `open_map` | `ACTION_VIEW` / `geo:` coordinates or encoded search | Activity dispatch; map/route unverified |
| `share_text` | `ACTION_SEND`, `text/plain`, native share chooser | Owner selects recipient; sharing unverified |
| `compose_message` | `ACTION_SENDTO` / SMS or email; `ACTION_DIAL` / number | Opens draft or dialer; never sends or calls |
| `set_alarm` | `AlarmClock.ACTION_SET_ALARM`, `EXTRA_SKIP_UI=false` | Confirmation UI requested; alarm save unverified |

Results use `dispatched=true`, `verified=false`, and `verification=android_activity_dispatch_only`. `handlerPackage` / `handlerActivity` report Android's actual resolved handler, which may be a system resolver rather than the final chosen receiving app. A missing handler or Android start failure is an error, not simulated success. No direct `ACTION_CALL`, SMS sending permission, arbitrary Intent decoding, file/content URI sharing, attachment upload, or silent alarm setting is exposed.

Validation rejects unknown fields, JSON nulls/wrong types, nonfinite/out-of-range numbers, oversized strings, NULs, embedded HTTP credentials, unsupported URI schemes, incomplete/ambiguous map inputs, multiple email recipients, and dial service/MMI/extension control characters. Message content and destination are shown only in native approval details, not activity-log summaries.

The manifest needs narrowly scoped visibility queries for these actions/schemes plus the normal `com.android.alarm.permission.SET_ALARM` permission. Android may still restrict background activity launches; a successful `startActivity` return cannot prove the target UI became visible. Follow up with an actual fresh screen observation instead of claiming the task completed.

Validation: all-production-source SDK 35 / Java 8 compilation passed. Host regression tests inspect the real validator. Device verification should use actual installed browser/map/share/message/dial/clock handlers, test owner denial and scope/plugin/lock changes during approval, and inspect each receiving UI without submitting messages, placing calls or assuming an alarm is saved.

Implementation follows [Android Developers: common intents](https://developer.android.com/guide/components/intents-common) and [intents and filters](https://developer.android.com/guide/components/intents-filters).
