# 저장소 구조

[← README로](../../README.ko.md) · [English](../en/repository-layout.md) · **한국어**

```text
hermes-pocket/
├── README.md · README.ko.md        소개 페이지 (영어 / 한국어)
├── docs/                           최상위 이중 언어 문서
│   ├── assets/                     배너와 이미지
│   ├── en/  ko/                    아키텍처, 현황, 구조, 목차
│   └── handoff/                    날짜별 인계 문서 (한국어)
└── hermes-android/                 Android 프로젝트
    ├── app/                        Android 앱 (Java, WebView UI, 에셋)
    ├── docs/                       상세 개발 문서 (대부분 한국어)
    │   └── android-v002 … v013/    버전별 테스트 기록과 근거
    ├── scripts/                    빌드·검증·패키징·설치 스크립트
    ├── tests/                      JVM 테스트와 Android 프로브
    ├── server/                     예전 Python 어댑터 (참고용)
    ├── engine-spike/               기기 내 Python 엔진 실험
    ├── future-v007/ staged-v007/   예전 계획 잔여물
    ├── UPSTREAM.md  LICENSE
    └── settings.gradle build.gradle version.json
```

## 참고

- **`app/`** 이 실제 제품입니다. `app/src/main/assets/` 아래는 WebView UI와 내장 Hermes 스킬입니다.
- **`engine-spike/`** 는 메인 APK에 **들어 있지 않은** 실험입니다. 업스트림 소스·의존성 wheel·후보 빌드의 대용량 복사본은 다시 만들 수 있으므로 git에 두지 않으며 `.gitignore`에 등록했습니다.
- **`server/`** 는 예전 PC 쪽 어댑터입니다. 현재 Android 앱은 여기에 연결하지 않습니다.
- **빌드 산출물**(`dist/`, `build/`, `.gradle/`, `.toolchain/`)과 서명 키(`.signing/`)는 git에서 제외됩니다.
