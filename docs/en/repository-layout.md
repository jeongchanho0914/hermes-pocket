# Repository layout

[← Back to README](../../README.md) · **English** · [한국어](../ko/repository-layout.md)

```text
hermes-pocket/
├── README.md · README.ko.md        landing pages (en / ko)
├── docs/                           top-level, bilingual documentation
│   ├── assets/                     banner and images
│   ├── en/  ko/                    architecture, status, layout, index
│   └── handoff/                    dated hand-over notes (Korean)
└── hermes-android/                 the Android project
    ├── app/                        Android app (Java, WebView UI, assets)
    ├── docs/                       detailed engineering notes (mostly Korean)
    │   └── android-v002 … v013/    per-version test records and evidence
    ├── scripts/                    build, verify, package and install scripts
    ├── tests/                      JVM tests and Android probes
    ├── server/                     legacy Python adapter (reference only)
    ├── engine-spike/               on-device Python engine experiments
    ├── future-v007/ staged-v007/   older planning leftovers
    ├── UPSTREAM.md  LICENSE
    └── settings.gradle build.gradle version.json
```

## Notes

- **`app/`** is the product. Everything under `app/src/main/assets/` is the WebView UI plus bundled Hermes skills.
- **`engine-spike/`** holds experiments that are **not** in the main APK. Large vendored copies of upstream source, dependency wheels and candidate builds are reproducible and are not meant to be kept in git; they are listed in `.gitignore`.
- **`server/`** is the old PC-side adapter. The current Android app does not connect to it.
- **Build output** (`dist/`, `build/`, `.gradle/`, `.toolchain/`) and the signing key (`.signing/`) are git-ignored.
