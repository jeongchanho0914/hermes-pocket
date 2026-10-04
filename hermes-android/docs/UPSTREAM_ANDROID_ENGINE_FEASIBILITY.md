# 원본 Hermes 엔진의 휴대폰 실행 검토

확인일: 2026-10-04. 이 문서는 실행 완료 보고서가 아니라 원본 엔진 도입을 위한
기술 검토와 검증 순서입니다. 현재 APK는 Java 에이전트이며, 원본 Python 엔진이
설치·연결됐다고 표기하면 안 됩니다. 실제 휴대폰에 다른 런타임을 설치하지 않았습니다.

## 현재 원본의 Android 지원 상태

공식 [Android / Termux 문서](https://hermes-agent.nousresearch.com/docs/getting-started/termux)는
arm64 패키지를 제공하지만, 확인 시점에는 **패키지가 현재 동작하지 않으며 수정 중**이라고
안내합니다. 정상 실행을 확인하기 전 이를 완성된 설치 경로로 안내할 수 없습니다.
패키지는 표준 Termux 경로에 맞춰져 있으며, 이름을 바꾼 Termux 앱에 그대로 넣는 방식은
지원하지 않습니다. Python 3.14·Node와 미리 빌드한 핵심 의존성을 제공하는 구조입니다.

원본 Android 패키지도 모든 데스크톱 기능을 포함하지 않습니다. Electron·로컬 Chromium·
데스크톱 컴퓨터 조작·Docker·일부 미디어와 외부 플러그인은 별도 제약이 있습니다.
Android는 화면이 꺼졌을 때 백그라운드 프로세스를 종료할 수 있습니다.

## 세 가지 경로

| 경로 | 실제 엔진 | 휴대폰 외부 중계 | 추가 설치 | 판단 |
| --- | --- | --- | --- | --- |
| 현재 Java 기능 확장 | 자체 루프 | 없음 | 없음 | 앱 오류·모바일 도구를 빠르게 개선할 수 있지만 원본 엔진 전체가 되지는 않음 |
| Termux + 원본 `hermes-acp` + 현재 GUI | 원본 Python | 없음 | Termux와 검증된 원본 패키지 | 원본을 사용하는 가장 작은 초기 검증 경로. 현재 공식 패키지 오류를 먼저 확인해야 함 |
| 한 APK에 CPython + 원본 엔진 내장 | 원본 Python | 없음 | 없음 | 원하는 최종 형태에 가까움. 바이너리 의존성·프로세스·패키지 관리·Android 어댑터 작업 필요 |

Termux 경로의 중계는 **같은 휴대폰 안의 IPC 어댑터**입니다. PC나 인터넷 서버를 두는
구조가 아닙니다. 하지만 별도 앱 설치가 필요하므로 한 APK만으로 동작한다고 설명하면
안 됩니다. 실행 중 외부 연결은 사용자 설정의 모델·웹 검색 등 API로 제한할 수 있으며,
초기 패키지 설치·업데이트 다운로드와 실행 중 서비스 연결을 구분해야 합니다.

## 가장 먼저 만들 검증용 원본 엔진 연결

원본 `acp_adapter/entry.py`와 `server.py`에는 실제 Python 엔진을 제공하는
`hermes-acp` 진입점과 initialize/new_session/load_session/prompt/cancel/model 변경이
존재합니다. [공식 ACP 문서](https://hermes-agent.nousresearch.com/docs/user-guide/features/acp)는
표준입출력 연결을 설명합니다. 기존 GUI를 유지하면서 스트림·도구 진행·승인·중단을
이 인터페이스로 연결하는 것이 독자적인 원본 실행 프로토콜을 새로 만드는 것보다 작습니다.

단, 기본 `hermes-acp` 도구 모음은 편집기용이며 예약 작업 관리·메신저 전달을 제외합니다.
원본의 모든 도구를 노출하려면 ACP의 도구 선택을 명시적으로 확장하거나 GUI 전용
`AIAgent` 호스트 어댑터를 만들어야 합니다. ACP 연결만으로 원본 전체 기능이 활성화되지는 않습니다.

1. **실기기 설치 전에 아키텍처가 맞는 격리 환경에서 원본 패키지 검증.**
   저장소 서명과 arm64 의존성을 확인하고 `hermes --version`, `hermes-acp --check`를
   실행합니다. 현재 실패하는 패키지라면 실패 지점과 수정 커밋을 기록합니다.
2. **ACP 프로토콜 검증.** 외부 유료 API 키 없이 로컬 테스트 모델 API에 연결하여
   initialize → session/new → session/prompt → 실제 함수 실행 → 후속 응답을 확인합니다.
   원본 스킬·메모리·대화 검색과 하위 에이전트가 원본 코드에서 실행되는지 검사합니다.
3. **GUI 연결 검증.** Java GUI의 메시지·진행 상태·중단·승인 화면을 ACP 이벤트로
   연결합니다. 모델이 보내는 숨은 생각을 UI에 자동으로 표시하지 않습니다.
4. **Android 도구 연결.** 원본에 실제 native 도구 어댑터를 등록하여 Android 앱에서
   기기 상태·허용한 앱·볼륨·접근성을 실행합니다. Python 명령 실행이 Android의 사용자
   승인·앱 허용 목록·권한 검사를 건너뛰지 않도록 합니다.
5. **화면 종료와 프로세스 재생성 검증.** 엔진 종료·취소·앱 재시작·백그라운드 제한을
   재현하고, 완료된 도구 결과와 실행 중이던 대화가 어떻게 복원되는지 기록합니다.

Termux [RUN_COMMAND 인터페이스](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)는
외부 앱의 실행 권한과 `allow-external-apps` 설정을 요구합니다. 이것은 명령 실행·결과
전달 인터페이스이며, ACP의 지속적인 양방향 스트림과 같은 기능은 아닙니다. 따라서
Termux 안의 작은 프로세스가 ACP 표준입출력을 휴대폰 내부 통신으로 연결해야 합니다.
앱에 API 키를 명령행 인자로 넣거나 공개 네트워크 주소에 서비스를 노출하지 않습니다.
로컬 연결도 다른 앱에서 접근할 가능성이 있으므로 페어링·인증·제한된 명령만 처리해야 합니다.

## 한 APK 내장 경로의 실제 작업

[Chaquopy 17 문서](https://chaquo.com/chaquopy/doc/current/android.html)는 Python 3.14와
arm64를 지원합니다. 따라서 Python 버전만으로 내장 방식이 불가능한 것은 아닙니다.
그러나 순수 Python 패키지 지원이 모든 native 의존성 지원을 뜻하지는 않으며,
Android에서는 일부 다중 프로세스 API가 제한됩니다.

검토한 원본 `pyproject.toml`은 Python 3.14용으로 OpenAI SDK, pydantic,
cryptography 등을 지정하고 Android psutil은 특정 소스 커밋을 사용합니다.
OpenAI SDK가 가져오는 jiter, pydantic-core, cryptography 등에는 실제 Android용
바이너리 빌드와 동적 라이브러리 호환성 검사가 필요합니다. Termux 패키지의 빌드 자료는
재사용 후보지만, Termux 고정 경로의 바이너리를 다른 패키지명 안에 단순 복사하면 안 됩니다.

- Gradle/Chaquopy 빌드 경로를 만들고 검증한 CPython 3.14·의존성을 APK에 포함합니다.
- 원본 코드와 라이선스를 포함하고 `HERMES_HOME`을 앱 개인 저장소에 분리합니다.
- upstream 패키지 관리자가 실행 중 데스크톱 Python·Node 도구를 내려받는 경로를
  Android 패키지 자산과 설치 가능한 Android 의존성으로 바꿉니다.
- git·Node·ripgrep·ffmpeg·shell 등 실제 기능이 요구하는 실행파일의 ABI와 경로를
  준비합니다. 제공하지 않은 바이너리를 제공했다고 주장하지 않습니다.
- [Android 실행 정책](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)에
  맞춰 실행파일을 패키징합니다. target API 35 앱에서 개인 저장소에 복사한 파일에
  chmod만 붙여 실행하는 것은 실행 환경 구현을 대신하지 못합니다.
- Java foreground service, 원본 취소·승인 이벤트, Android 도구 어댑터를 연결합니다.
- `hermes-acp --check`에 해당하는 import 검사부터 실제 모델·도구·스킬·위임까지
  단계별로 검증한 뒤 각 기능을 사용 가능하다고 표시합니다.

완료 판단은 원본 모듈 몇 개가 APK에 존재하는지가 아니라 **원본 엔진이 실제 요청을
처리하고 해당 기능을 실행·저장·복원하는지**로 해야 합니다. Root가 필요한 휴대폰 작업,
Docker나 데스크톱 화면처럼 실행 환경에 의존하는 기능까지 일반 Galaxy에서 자동으로
가능해진다고 약속할 수는 없습니다.

## 공식 서명 패키지를 실제로 확인한 결과

위 검토 후 공식 APT 자산을 직접 내려받아 서명·해시·ELF를 확인했습니다.
**PyPI에서 Android wheel을 찾지 못했다고 원본의 사전 빌드 의존성이 존재하지 않는 것은
아닙니다.** 다음은 실제 자산 검사 결과이며 Android import 실행 결과와 구분합니다.

- stable의 `key.asc`, `Release`, `InRelease`, `Release.gpg`는 확인 시점 HTTP 404였습니다.
- canary는 위 파일 모두 HTTP 200이었습니다. 공개키의 주 지문을 공식 문서의
  `C572B5FDD1A29CCFA9A912B6840B0848E139156D`와 대조했습니다.
- GPG로 `Release.gpg`와 `InRelease` 서명을 각각 검증해 GOODSIG·VALIDSIG 및 exit 0을
  확인했습니다. 검증된 Release의 SHA-256·크기로 Packages를 검증했습니다.
- Packages가 가리킨 `hermes-agent_0.27.1~canary.20260917131241-1_aarch64.deb`를
  내려받았습니다. 크기는 161,855,904바이트이며 SHA-256은
  `324397ce52887ce24f124e0396fde3974062d20451b0254613f9c3c5f4064fb4`입니다.
  이 값이 서명된 Packages와 일치한 뒤에만 정적 추출했습니다. 설치 스크립트를
  실행하지 않았으며 실제 휴대폰에도 설치하지 않았습니다.
- 패키지의 실제 site-packages에 pydantic 2.13.4 / pydantic-core 2.46.4,
  jiter 0.16.0, cryptography 50.0.1, Pillow 12.3.0, psutil 8.0.0이 존재했습니다.
  native 확장 23개와 패키지 내 ELF 라이브러리 391개를 정적 검사했습니다.

핵심 확장 두 개인 pydantic-core와 jiter는 CPython 3.14 arm64 Android용이며,
DT_NEEDED가 `libpython3.14.so`, `libdl.so`, `libc.so`입니다. Termux 절대 경로 RUNPATH가
존재하지만 이것만으로 로딩 실패를 확정할 수는 없습니다. Chaquopy spike에서 실제
빌드된 arm64 `libpython3.14.so`의 동적 export와 비교했을 때 pydantic-core 162개,
jiter 96개, cryptography 151개, psutil 44개의 CPython 참조 중 빠진 심볼은 없었습니다.

cryptography에는 OpenSSL 3의 `libssl.so.3`·`libcrypto.so.3`, psutil에는
`libandroid-support.so`도 필요합니다. 이 라이브러리들은 해당 패키지에 포함돼 있으며
각각의 비시스템 DT_NEEDED도 패키지 내에서 확인했습니다. Chaquopy의 OpenSSL은 별도
이름의 라이브러리를 사용합니다. native 확장과 추가 라이브러리를 Android loader가
실제로 로드할 수 있는지, CPython 구조체 ABI와 런타임 동작까지 맞는지는 arm64 APK의
실제 import·API 요청으로 검증해야 합니다. 정적 심볼 일치는 실행 성공을 보장하지 않습니다.

서명·해시·ELF 검사 증거는 `engine-spike/termux-assets/`의 다음 파일에 저장했습니다.

- `metadata-fetch.json`: 실제 요청 URL·HTTP 결과.
- `signature-verification.json`: 지문·GPG 서명 검증 결과.
- `packages-verification.json`, `deb-verification.json`: 서명된 메타데이터와 해시 비교.
- `native-elf-inventory.json`, `library-closure.json`: 확장·라이브러리 DT_NEEDED/RUNPATH.
- `chaquopy-symbol-compatibility.json`: 실제 Chaquopy libpython과 CPython 심볼 대조.

공식 문서의 패키지 오류 공지는 원인을 특정하지 않습니다. HTTP 404인 stable과
서명이 정상인 canary의 존재는 확인했지만, canary가 왜 동작하지 않는지나 전체 원본
기능이 정상인지까지 정적 검사로 확정하지 않았습니다.

## 실기기 원본 엔진 spike에서 확인된 범위

이후 별도 검증용 APK를 Galaxy S24 Ultra / Android 16에서 실행했습니다.
`engine-spike/evidence/s24-engine-conversation-fixed.json`에는 CPython 3.14 arm64,
실제 upstream `run_agent.AIAgent` import, 원본 SessionDB·메모리·스킬 및 native
pydantic-core 동작을 기록했습니다. 테스트 모델 API의 도구 요청을 원본 에이전트가
실제로 실행하고 결과를 다음 모델 요청으로 전달하는 대화 루프도 확인했습니다.

이 결과는 **원본 엔진과 핵심 기능을 Android APK 안에서 실행하는 경로가 실제로
성립한다는 증거**입니다. 전체 상용 모델·기기 도구·예약 작업·미디어·브라우저의
완성이나 현재 Hermes Pocket 배포 APK에 원본 엔진이 통합됐다는 뜻은 아닙니다.

프로덕션 연결 후보 `engine-spike/integration/hermes_android.py`는 원본 `AIAgent`를
호출하고 원본 SessionDB와 Java UI 대화 기록을 연결합니다. Android 도구는 Java
승인·허용 목록·권한을 유지하는 bridge에 등록하며, 원본 메모리·스킬 구현을 Java
프리셋으로 대체하지 않습니다. 이 연결 후보 자체의 실기기 실행과 사용자 앱 통합은
별도로 검증해야 합니다. 기존 배포 런타임을 이 문서만으로 교체하지 않습니다.
