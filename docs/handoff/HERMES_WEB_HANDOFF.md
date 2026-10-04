# Hermes Pocket 작업 인계 — Web Hermes에서 이어가기

작성일: 2026-10-04, 한국 시간. 이 문서는 지금까지의 요청·구현·검증·제한을 다음 작업 환경에 전달하기 위한 기록이다. API 키, 서명 비밀번호, 개인 대화 본문은 포함하지 않았다.

## 1. 가장 먼저 알아야 할 현재 상태

- 프로젝트: **Hermes Pocket**, Android용 모바일 에이전트.
- 작업 폴더: `/home/chanho/Desktop/hermes-pocket-source/hermes-android`.
- 이 인계 파일: `/home/chanho/Desktop/hermes-pocket-source/HERMES_WEB_HANDOFF.md`.
- 최신 배포: **v0.12 베타**, Android `versionCode=12`, 패키지 `dev.chanho.hermes`.
- 사용자의 **갤럭시 S24 Ultra SM-S928N / Android 16**에 업데이트 설치했다. 설치 성공, 설치본 해시 일치, 앱 시작 및 프로세스 실행을 확인했다.
- 설치는 `adb install -r`로 진행했다. 제거·초기화하지 않고 기존 앱 데이터를 유지하는 업데이트 방식이다.
- 설치 직후 앱의 제한된 오류 메타데이터를 수집했을 때 **기록된 이벤트는 0건**이었다. 모든 기능이 오류 없이 작동한다는 보증은 아니다.
- 현재 배포 APK는 **Java 에이전트 + Android 네이티브 도구 + WebView UI**이다. 원본 Hermes Python 엔진 전체를 실행하는 APK는 아니다.
- **Root UID 0을 얻지 못했다.** Shizuku의 Shell UID 2000도 Root와 다르다.
- 다음 작업 환경은 사용자가 말한 **Web Hermes**이다. 제품의 최종 대상은 지금까지 Android였다. 웹 기반 작업 환경에서 기존 Android 개발을 이어가는 경우 이 목적을 유지한다. 제품 자체를 웹앱으로 바꾸는 범위는 별도로 정해야 한다.

### 설치된 APK

| 항목 | 값 |
|---|---|
| 파일 | `hermes-android/dist/hermes-pocket-v0.12.apk` |
| 크기 | 7,075,335 bytes |
| SHA-256 | `98e5fa2fbf5908a0dadb55ccbd02f2fdf76783d3a96f72b3f3212df6efcbdcf7` |
| Android 요구 | minSdk 26, targetSdk 35 |
| 서명 인증서 SHA-256 | `6d997f21e5f0c15e3756f5cc2b792262b40ed6d9c6e688f79ab7081a1c6d8e7c` |

최신 설치 근거: [s24-install.json](../../hermes-android/docs/android-v012/s24-install.json).

## 2. 사용자의 최종 목표와 지속되는 작업 조건

최종 목표는 **Hermes처럼 채팅으로 휴대폰의 일반적인 작업을 수행하는 Android 에이전트**다. 모델 API를 연결하면 별도 PC·중계 서버 없이 휴대폰에서 도구 실행, 기록, 메모리와 스킬을 처리하고, UI는 유명 채팅 앱처럼 간결하고 부드럽게 만든다.

사용자의 구체적인 요구:

1. 모델 Provider 추가·수정·변경, 기본 모델과 기본 생각 수준 설정.
2. 입력창의 모델 선택과 생각 수준 선택을 별도 기능으로 제공.
3. 모델 선택은 약 5개가 보이는 스크롤 목록. 연결되지 않았을 때는 설정 이동 안내.
4. 화이트·블랙 두 테마, 간결한 사이드바, 하단의 자연스러운 설정 버튼.
5. 새 채팅에서 입력창에 자동 포커스하거나 키보드를 띄우지 않기.
6. 대화 삭제의 X 버튼 대신 길게 누른 뒤 좌우로 밀어 삭제하는 동작.
7. `+` 메뉴에서 도구·스킬·플러그인·대화 정리를 자연스럽게 접근.
8. 접근성 화면 읽기·이미지 보기·탭·스크롤·앱 조작과 실제 터미널.
9. 다른 앱을 사용하는 동안 작은 팝업에서 현재 작업과 입력창 표시.
10. 실행 중 전송 버튼을 **중지**로 바꾸고 **백그라운드에서 작업하기** 제공.
11. 도구·생각·답변을 실제 발생 순서대로 채팅 사이에 표시.
12. 모델/API 문맥 한도를 활용하고 길어지면 Hermes식 compact.
13. `USER.md`, `MEMORY.md`, 추가 Markdown, 생성된 스킬을 목록으로 보고 펼쳐서 읽기.
14. 유용한 사용자 메모리와 검증된 재사용 스킬을 모델이 자발적으로 생성·갱신.
15. 기기 도구와 기기 권한은 서로 다른 화면. 고급 설정은 제거.
16. 새 APK 전에 이전 오류를 수집·검토·수정하는 개발 흐름.
17. 최신 생각 UI: **작은 뇌 아이콘 + `thinking.` → `thinking..` → `thinking...` + 경과 시간**. 모든 모델에 같은 표시를 적용하되 실제 공개 출력이 있는 모델만 본문을 펼치기.

진행 방식:

- 적절한 멀티에이전트·서브에이전트·병렬 작업을 사용해 달라는 명시적 요청이 있었다. 파일 소유권을 분리하고, 최종 빌드·설치 담당은 한 명으로 유지한다.
- 완료한 작업은 실제 결과로 검증한다. 단순 도구 목록·UI 버튼·Intent 전달 성공을 작업 완료로 표현하지 않는다.
- **최신 APK는 Gmail로 보내지 않고 USB로 휴대폰에 직접 설치한다.** 과거 Gmail 전송 요청보다 나중의 이 지시가 우선한다.
- 초기에는 외부 연결을 API 중심으로 제한했으나, 이후 웹 검색은 실제 기본 브라우저를 사용하도록 요청했다. 이 후속 요구를 반영했다.
- 휴대폰 조작은 실제 권한과 Android의 보호 범위 안에서 한다. 임의의 Root 설치·부트로더 해제·초기화·펌웨어 플래시는 이어서 실행하지 않는다. 다운로드 모드 진입으로 사용자가 혼란을 겪은 이력이 있다.

## 3. 실제 앱 구조

```text
Android MainActivity / WebView
  ├─ app.js · app.css · index.html
  ├─ 타입이 정해진 NativeBridge
  └─ AgentRuntime + ForegroundService
       ├─ DirectAgent → Net → 설정한 모델 API
       ├─ DeviceTools / 접근성 / 화면 캡처
       ├─ 터미널 / 파일 / 앱 Intent / 브라우저 검색
       ├─ 메모리 / 스킬 / 원본 스킬 라이브러리
       └─ SQLite Store / 진단 오류 기록
```

모델 추론은 연결한 API 제공자가 수행한다. UI·도구 실행 루프·승인·기기 작업·로컬 기록은 휴대폰에서 처리한다. 앱 실행에는 개발 PC가 필요하지 않다. 모델 및 검색 API 사용 비용은 제공자에 따라 발생할 수 있다.

현재 UI만 일반 웹 브라우저에 복사하면 Android 기기 제어는 동작하지 않는다. 그 기능은 `MainActivity`의 네이티브 브리지와 Android 서비스가 제공한다. 실제 웹 제품으로 확장하는 경우에는 기기 측 실행 주체와 연결 구조부터 설계해야 한다. API 인증 정보는 Android Keystore 기반으로 암호화 저장한다.

### 주요 소스 위치

Java 파일은 아래 공통 폴더에 있다.

`hermes-android/app/src/main/java/dev/chanho/hermes/`

| 파일/영역 | 담당 기능 |
|---|---|
| `MainActivity.java` | WebView와 NativeBridge, 설정·도구·백그라운드 이동 연결 |
| `AgentRuntime.java` | 실행 소유권, 도구 분배, 중지, 이벤트, 서비스 유지 |
| `DirectAgent.java`, `Net.java` | OpenAI 호환 API, SSE 스트리밍, 도구 루프, 공개 출력 처리 |
| `LocalCapabilities.java` | Provider·생각 수준·도구 스키마·플러그인 허용 상태 |
| `Store.java` | 설정, 암호화 인증 정보, 대화·transcript·활동·생각·순서 저장 |
| `ModelLimits.java`, `ContextCompactor.java` | 문맥 한도, 사용량 추정, 체크포인트 요약 |
| `DeviceTools.java`, `PhoneAccessibilityService.java`, `ScreenCapture.java` | 화면·제스처·기기 설정·안전한 전면 창 판별 |
| `AgentOverlay.java`, `AgentForegroundService.java` | 작은 팝업, 백그라운드 실행, 중지·서비스 수명 |
| `PhoneIntentTools.java`, `BrowserSearch.java`, `WebTools.java` | 외부 앱 요청, 실제 기본 브라우저, 명시적 검색 API 대체 경로 |
| `TerminalSessions.java`, `TerminalTools.java` | 실제 셸 프로세스, 입출력, 백그라운드 작업과 취소 |
| `PhoneFiles.java`, `PhoneFilesPaths.java` | 사용자 허용 파일 영역과 파일 작업 |
| `LocalAgentTools.java`, `MemoryDocuments.java` | 메모리·추가 Markdown·대화 검색·스킬 도구 |
| `LocalSkillStore.java`, `SkillPackages.java`, `BuiltinSkillLibrary.java` | SKILL.md, 리소스, 가져오기·내보내기, 원본 라이브러리 |
| `ShizukuPhoneBridge.java`, `PrivilegedPhoneService.java` | 선택적 Shizuku Shell 연결 |
| `Diagnostics.java`, `PocketApplication.java`, `DiagnosticsProvider.java` | 지속 오류 메타데이터 및 제한된 수집 경로 |
| `app/src/main/assets/{app.js,app.css,index.html}` | 실제 채팅·설정·스킬·메모리 화면 |

`HermesEngine.java`, `PythonPhoneBridge.java`, `engine-spike/` 등의 파일 존재만으로 원본 Python 엔진이 현재 앱에서 실행된다고 판단하면 안 된다.

## 4. 지금까지 구현한 기능

### 모델 설정과 채팅

- Provider/주소/키/모델 설정, 기본 모델과 생각 수준, 모델 목록 조회 및 연결 검사.
- OpenAI 호환 도구 호출과 스트리밍을 처리하는 실제 에이전트 루프.
- 키 저장과 제거, Provider별 생각 수준 매핑 및 필요한 reasoning replay 처리.
- 대화 기록, 새 대화, 기록 재개와 삭제 UX.
- 실제 실행 중 전송 → 중지 전환, 백그라운드 이동, 실행 서비스 유지.
- 화이트·블랙 UI, `+` 메뉴, 기기 도구/권한 분리, 고급 설정·수동 문맥 입력 제거.

### 생각·도구·답변 타임라인

- DB 버전은 **4**. 메시지·도구 활동·공개 생각에 지속적인 `timelineOrder`를 부여한다.
- 도구 카드의 상태 갱신은 같은 카드를 갱신하며 이전 항목을 위아래로 재정렬하지 않는다.
- 도구 실행 전 모델이 실제로 출력한 설명은 해당 위치의 일반 답변 형태로 표시한다.
- 여러 도구 라운드의 본문을 마지막 답변에 다시 붙여 중복시키지 않는다.
- 앱을 다시 열어도 순서 유지. 과거 조회가 늦게 돌아와 최신 이벤트를 덮지 않도록 처리.
- 읽던 위치, 펼친 카드와 텍스트 선택을 유지하는 회귀 수정.
- 뇌 아이콘·점 애니메이션·초 단위 시간. 완료·오류·취소 시 시간 정지.
- 실제 공개 생각 출력만 별도 저장하고 펼친다. 공개 출력의 타입·Provider·세션·실행·라운드를 검증하며, 현재 직접 검증된 공개 본문 경로는 MiMo이다. 다른 모델에는 내용을 만들어 표시하지 않는다.
- 긴 제목, Provider/출처/라운드 설명, 빠른 미리보기, 중복 진행 문구 제거.

### 기기 조작·화면 보기·작은 팝업

- 접근성 트리 읽기, 화면 이미지 전송, 탭·스와이프·길게 누르기·범위 슬라이더·텍스트 입력.
- 앱 실행, 최근 앱, 알림, 빠른 설정과 패널 닫기.
- 미디어/벨소리/알람/알림 볼륨, 밝기 관련 설정, 화면 회전 설정 등 네이티브 기기 도구.
- 일반 설정 화면은 실제 시스템 창·제목·리소스가 확인된 경우에만 읽고 조작한다.
- 전화번호 다이얼, SMS·메일 작성, 지도·링크·공유·알람 요청을 Android 앱에 전달한다. 요청 전달과 실제 전송·통화·저장 완료는 구분한다.
- 좌표는 최근 화면 snapshot과 전면 패키지·창·surface에 연결하며, 오래된 관찰로 무조건 다시 탭하지 않는다.
- 팝업을 실제로 떼고 접근성 창에서 사라졌는지 확인한 뒤 제스처를 한 번 전달하도록 수정했다.
- 비밀번호·편집 값·민감 화면의 노출을 제한하고 이미지는 영구 대화 DB에 저장하지 않는다.
- 활성 보안 창의 root가 없을 때 비활성 런처/내비게이션 바를 현재 앱으로 오인하는 문제를 수정했다.
- 기기 범위 `none`이면 변경 작업을 승인 전후·실행 직전에 차단한다.
- 최신 QS 수정: 트리 전환 중에는 최대 1.5초간 **관찰만** 재시도한다. 같은 신뢰된 알림 창이 남은 경우에만 기존 승인 목표의 두 번째 Back을 허용하고, 최대 두 번까지만 전달한다. 일반 앱에 추가 Back을 보내지 않는다.

### 터미널

- 표시용 가짜 터미널이 아닌 실제 `/system/bin/sh` 프로세스 실행.
- 기본은 앱 UID, 선택적으로 Shizuku를 통한 Shell UID 2000.
- 프로세스 생성·폴링·로그·대기·입력·종료·취소, 관리되는 백그라운드 작업.
- 실제 작업이 있으면 ForegroundService를 유지하고, 중지 시 해당 프로세스를 취소한다.
- 현재 메인 APK의 터미널은 PTY·Python 배포판·데스크톱 Linux 환경 전체를 제공하지 않는다. 앱 UID/Shell UID의 실제 접근 범위를 따른다.
- 모델 API 키를 셸 환경에 주입하지 않는다.

### 스킬·Markdown·메모리

- 실제 `SKILL.md` 생성·저장·조회·수정·삭제, 중첩 리소스, 안전한 패키지 가져오기·내보내기.
- 원본 Hermes의 스킬 문서 **210개**를 라이브러리로 묶었다. 현재 제한 내 설치 가능한 것은 **209개**이며, 하나는 단일 리소스 크기 제한을 넘는다.
- 원본 문서가 설치된다고 그 문서가 요구하는 모든 도구나 Python 런타임이 자동으로 구현되는 것은 아니다.
- `USER.md`, `MEMORY.md`, 추가 Markdown 문서를 앱의 private `memories/`에서 관리한다.
- 핵심 두 문서만 기본 문맥에 넣고, 추가 문서는 필요할 때 읽는다.
- 기존 메모리 보존·마이그레이션, 원자적 저장, 승인 중 내용 변경 방어, 경로 탈출·심볼릭 링크·인증 정보 저장 방어.
- 스킬과 메모리를 접힌 스크롤 카드 목록으로 보여 준다. 선택하면 본문을 펼치고, 새로 만들기·편집·리소스 작업은 필요할 때 열도록 변경했다.
- 모델 프롬프트와 실제 도구에 유용한 사용자 사실 및 검증된 재사용 스킬의 자동 유지 지침을 넣었다. 모든 모델이 항상 올바른 자동 저장 도구 호출을 한다는 보장은 아니다.
- 현재 메모리 한도: USER.md 1,375자, MEMORY.md 4,000자, 추가 MD 100,000자, 패키지 합계 8 MiB/256파일. 원본과 세부 계약이 완전히 같지는 않다.

### 문맥 한도·compact

- 실제 실행 경로에서 이전 60,000/240,000 문자 수 기반 제한을 제거했다.
- `/models` 메타데이터의 실제 주소·모델에 연결된 문맥/출력 한도를 우선 사용한다.
- 확인된 공식 엔드포인트 일부는 작은 공식 모델 카탈로그를 보조로 사용한다. 문서의 모델 수치는 조사 당시 기준이다.
- 미확인 모델은 **128,000토큰 추정, 미검증**으로 구분한다. 모든 API의 최대 한도를 이미 정확히 안다고 표현하면 안 된다.
- 일반 답변 출력 허용/예약은 최대 8,192토큰, 요약은 최대 4,096토큰이다. 모델 자체 최대 출력과 다르다.
- 사용 가능한 입력 예산의 88%에서 같은 모델로 구조화된 요약 체크포인트를 만든다.
- 목표·사용자 사실·완료/거절/검증 상태·남은 작업·파일/스킬/식별자를 유지한다. 현재 사용자 요청과 최근 도구 요청/응답 쌍을 보존한다.
- 원문 transcript는 SQLite에 그대로 남고 요약 상태는 별도 저장한다.
- 실제 문맥 초과로 분류된 오류에만 한 번 요약 후 모델 요청을 재전송한다. 이미 실행한 도구를 다시 실행하지 않는다.
- 큰 도구 결과도 Unicode를 버리지 않고 분할 요약하며, 최대 32회의 요약 요청 후 미완료 체크포인트 저장을 거절한다.
- `+` 메뉴의 대화 정리와 native `compactConversation` 연결.

### 웹 검색

- `web_search` 기본은 실제 휴대폰 기본 브라우저 경로다. `browser_search`도 등록했다.
- 기본 브라우저로 검색 Intent를 전달하거나 해당 브라우저에 Google 검색 URL을 전달한다.
- **브라우저는 화면에 표시되며 Hermes가 작업을 계속한다.** 보통의 Android 브라우저를 완전히 보이지 않는 headless 백그라운드 브라우저로 실행한 것은 아니다.
- 화면 도구와 접근성 서비스가 준비된 경우 정확히 그 브라우저의 새 화면을 제한된 시간 안에 읽는다.
- 요청 전달·전면 화면 관찰·검색 완료·구조화된 검색 결과는 서로 구분한다.
- `mode: "api"`를 명시하면 기존 Mwmbl/Tavily/SearXNG API 경로를 사용한다. Mwmbl은 검색 범위/최신성 제한이 있고 다른 경로에는 별도 키·엔드포인트가 필요할 수 있다.

### 오류 자동 기록과 다음 APK 검토

- 앱 시작 초기부터 네이티브 오류 및 제한된 WebView 오류 메타데이터를 저장한다.
- private `files/diagnostics/events.jsonl`, 최대 256건/256 KiB, 중복 집계·원자적 저장.
- 예외 클래스·코드 위치·허용된 단계 등의 정보만 저장한다. 키·대화·생각 본문·HTTP 본문·스크린샷·셸 출력은 저장하지 않는다.
- Android가 보유한 앱 종료 이력에서 지원되는 crash/ANR 메타데이터도 가져오는 경로가 있다. 과거 종료 당시 APK 버전을 추측하지 않는다.
- 릴리스 수집 URI: `content://dev.chanho.hermes.diagnostics/events`. 앱 자신의 UID/Root/ADB shell만 제한된 읽기를 허용하고 일반 앱·쓰기·다른 경로는 차단한다.
- 빌드 전 지정된 휴대폰의 오류를 수집하고 검토한다. 미해결 치명적 오류는 기본적으로 빌드를 차단한다.
- 수정 처리에는 실제 실행된 테스트 증거와 소스/로그 해시를 연결한다. 파일을 고쳤다고 임의로 해결됨 처리하지 않는다.
- USB가 없을 때는 명시적 사유를 기록하는 오프라인 준비 옵션이 있다. 기존 치명적 오류를 무시하거나 새 오류가 없다고 주장하지 않는다.
- 설치된 앱이 자기 코드를 자동 수정하는 시스템은 아니다. 개발자가 기록을 읽어 다음 소스/APK를 수정하는 흐름이다.

## 5. 중요한 버그 수정과 작업 경과

| 단계 | 확인된 작업/결과 |
|---|---|
| 초기 버전 | APK 설치·Android 호환성 문제, UI 구조, 모델 설정·도구 권한 등을 반복 개선. 초기 모든 실패의 원인이 완전히 특정됐다고 기록하지 않는다. |
| v0.06~v0.07 전후 | USB 실기기 설치와 접근성 연결, 실제 제스처·Shizuku 연결 흐름 검증을 진행. Root는 부여되지 않음. |
| v0.08~v0.09 | 화면 보기·보호·팝업·터미널·스킬·일반 휴대폰 조작 기능을 확장하고 실제 실행 문제를 추적. 세부 근거는 해당 버전 문서를 참조. |
| v0.10 | 팝업 제거/제스처 전달 문제 수정, 실제 계산기 탭 결과 확인, 원본 스킬 라이브러리 및 백그라운드 터미널 강화. |
| v0.11 | 공개 생각 스트리밍과 실제 경과 시간, DB3 기록 보존, 일반 기기 설정 및 앱 Intent 검증. 추가로 QS 닫기·권한 none·보안 창 root-null 문제가 발견됨. |
| v0.12 최초 후보 | DB4 순서, compact, MD 메모리, 간결한 생각 UI, 오류 기록·수집, 도구/권한 분리, 백그라운드/STOP. |
| v0.12 최종 | 실제 QS 검사에서 남아 있던 애니메이션 중 관찰 실패를 추가 수정. 최종 hash `98e5fa…`, 에뮬레이터 검증 후 S24에 업데이트 설치. |

v0.12 최초 후보 `d2e353…`의 실제 QS 실패 로그는 삭제하지 않고 남겼다. [실패 기록](../../hermes-android/docs/android-v012/native-forward-guards-d2-failed.json), [최종 성공 기록](../../hermes-android/docs/android-v012/native-forward-guards.json).

## 6. 검증 완료 범위와 미검증 범위

### 완료한 호스트 검사

- JVM 생산 코드 검사 **161개**.
- 실제 Android SDK 소스/계약 검사 **8개**.
- 진단 생산 코드의 호스트 파일 검사 **10개**.
- Python 검사 **128개**: 브라우저 UI 101개 + 수집기/전송/Manifest/버전 등 27개.
- 전체 실행·재개 실행·의도된 UI 변경에 따른 표적 재실행의 합산이다. 단일 무실패 전체 실행이라고 표현하지 않는다.
- 테스트별 PASS 및 보관된 로그 해시: [host-regression-results.json](../../hermes-android/docs/android-v012/host-regression-results.json).

### 최종 APK의 API35 에뮬레이터 검사

- 생각 UI·실제 WebView·로컬 SSE **14개**: 실제 공개 본문이 답변보다 먼저 나오며, 펼치기·뇌 아이콘·시간 증가/정지·비지원 출력 제외·재시작 보존 확인.
- 기기 범위·알림·QS·최근 앱 등의 실제 guard **17개**.
- 보안 화면 active root-null에서 읽기/캡처 거절 **2개**.
- 실제 manual compact: 표시 메시지 16개와 원본 transcript 17행의 SHA 유지, 체크포인트 저장 및 프로세스 재개 확인.
- 설치된 에뮬레이터 APK를 다시 읽어 최종 파일의 해시와 일치 확인.
- DB1/2/3→4, 진단 권한·손상 파일 거절, 실제 3라운드 순서 검사는 이전 후보 APK에서 실행했다. 해당 구현은 최종본과 같고, 최종 후보 변경은 QS 관찰 경로였다.
- [native-completion-summary.json](../../hermes-android/docs/android-v012/native-completion-summary.json).

### S24 실제 휴대폰에서 마지막으로 확인한 사항

- v0.12 `adb install -r` 성공.
- 설치본 hash/크기 일치, versionCode12/versionName0.12.
- MainActivity 시작 명령 성공과 앱 프로세스 실행.
- 제한된 오류 메타데이터 수집: 당시 0건.
- 개인 대화 DB·설정 파일·키를 추출하지 않았다.

**남은 검증:** 사용자의 실제 원격 API, 현재 S24의 모든 도구·기본 브라우저·팝업·장시간 백그라운드·제조사 설정 화면에 대한 종합 동작 검사는 끝나지 않았다. 로컬 API fixture의 성공을 실제 제공자 가용성·과금·사용자 모델의 모든 지원으로 해석하면 안 된다.

## 7. 원본 Hermes와의 차이 및 엔진 이식 실험

고정 참조: `NousResearch/hermes-agent` commit

`d795726f78e532ca31655f74656b4be63a907581`

소스 위치:

`hermes-android/engine-spike/dependencies/source/hermes-agent-d795726f78e532ca31655f74656b4be63a907581/`

초기 인벤토리는 도구 정확 이름 107개(기본 90/플러그인 17), 플러그인 manifest 102개, gateway 이름 24개, 스킬 문서 210개를 조사했다. 이것은 고정 버전 기준이며 최신 upstream 전체라는 주장이 아니다.

[HERMES_FEATURE_MATRIX.md](../../hermes-android/docs/HERMES_FEATURE_MATRIX.md)는 **초기 감사 자료**다. 그 안의 compact 없음·문자 수 제한·단일 메모리 등의 설명은 이후 v0.12에서 바뀌었으므로 현재 구현 판단에 그대로 사용하면 안 된다. 앞으로 도구별 현재 상태와 근거를 갱신해야 한다.

이전 parity 문서들에도 오래된 설명이 남아 있을 수 있다. 충돌할 때는 최종 v0.12 소스·설치 기록·검증 보고서의 실제 증거를 먼저 확인한다.

원본 전체와 아직 같지 않은 주요 영역:

- 원본 Python AIAgent 실행 및 모든 registered tool의 동일 계약.
- 원본 `delegate_task` 등의 사용자 앱 내부 멀티에이전트 엔진. 개발 중 멀티에이전트를 사용한 것과 앱 자체가 제공하는 기능은 구분한다.
- 전체 브라우저 자동화/로그인 vault, cron·scheduler, 음성·이미지·영상 생성.
- 전체 MCP/플러그인 runtime, gateway, provider OAuth·credential pool·failover.
- 세션 branch/rewind/export, 완전한 portable backup·restore, 비용 ledger, 프로필 등.
- 원본 `execute_code`/Python kernel, PTY, 데스크톱 명령과 패키지 환경.

### 별도 ARM/Python 후보

- `engine-spike/candidates/`에서 원본 엔진·Python·WebP/ELF 호환성 실험을 했다.
- Android 16/16 KiB 관련 ELF/ZIP 정렬 및 WebP를 조사하고 공식 NDK로 WebP 라이브러리를 다시 빌드했다.
- 16 KiB 배치 검사를 통과한 ARM 후보가 있으나, 실제 ARM Android 런타임에서 원본 엔진 동작을 입증한 상태는 아니다.
- 현재 검사에 사용한 에뮬레이터는 x86_64이고 후보는 arm64이므로, 후보 실행 검증에는 호환되는 arm64 환경이 필요하다.
- 이 후보를 현재 메인 APK와 혼동하거나 실험 완료만으로 production 포함 처리하지 않는다.
- 현재 메인 APK에는 그 native Python/ELF 엔진이 들어 있지 않다.
- [원본 엔진 이식 계획](../../hermes-android/engine-spike/docs/UPSTREAM_ANDROID_ENGINE_PLAN.md).

## 8. Web Hermes에서 이어갈 권장 순서

1. **작업 환경 연결:** 새 환경이 실제 `hermes-android` 소스와 문서에 접근 가능한지 확인한다. 이 MD만 전달하면 코드·스킬 리소스·APK·빌드 도구가 자동으로 전달되지는 않는다.
2. **현재 상태 재확인:** `version.json`, `dist/build-info.json`, `s24-install.json`, 최종 보고서를 먼저 읽는다. 이미 해결한 설치 문제나 완료된 UI 변경을 처음부터 다시 하지 않는다.
3. **실기기 문제 재현:** 현재 사용자가 실패하는 채팅·도구의 정확한 요청/결과를 확보하고 제한된 진단 기록부터 읽는다. 사용자 API 키를 로그나 공유 문서에 출력하지 않는다.
4. **S24 실제 종합 흐름:** API 연결 → 화면 읽기/이미지 → 앱 선택·단일 조작 → 결과 확인 → 중지 → 백그라운드 → 팝업 재개를 확인한다. 기본 브라우저 검색도 실제 화면/링크 결과까지 검증한다.
5. **원본 parity 재감사:** 오래된 feature matrix를 v0.12 현재 코드 기준으로 갱신한다. 구현/부분 구현/미구현/플랫폼 제한/설정 필요를 분리한다.
6. **원본 기능 우선순위:** 사용자에게 필요한 멀티에이전트, 파일/코드 실행, cron, 브라우저, 스킬 runtime을 선정하고 도구 계약·테스트·Android 어댑터를 구체적으로 구현한다.
7. **원본 엔진 채택 판단:** ARM 후보를 격리된 실기기 환경에서 검증한 뒤 Java 유지/원본 Python 통합의 범위를 정한다. 호환성 경고와 라이브러리 로딩·기기 브리지·중지·프로세스 수명을 같이 확인한다.
8. **디자인 최종 마감:** 화이트·블랙, 작은 생각 표시, 입력창 하단 배치, 간단한 설정, 카드 펼치기 구조를 유지하면서 사용자의 실제 참고 이미지와 비교한다.
9. **다음 APK:** 기존 오류를 먼저 수집·검토하고 변경에 맞는 검사를 실행한다. 소스 동결 후 한 담당자가 빌드·서명·정렬·해시 검사를 하고 직접 설치한다.

기능 완성·원본 완전 복제·Root 수준 제어를 확인하지 않고 약속하지 않는다. 실제 가능한 일반 사용자 작업을 하나씩 확장하고 결과 증거를 남기는 방식으로 진행한다.

## 9. 로컬 빌드·검사·휴대폰 연결

아래 명령은 현재 로컬 작업 환경 기준이다. Web Hermes 환경에서는 폴더·SDK·USB 연결 접근이 다를 수 있다.

```bash
cd /home/chanho/Desktop/hermes-pocket-source/hermes-android

python3 scripts/versioning.py check
python3 scripts/collect_diagnostics.py
python3 scripts/build.py

# 새 버전 번호가 필요한 시점에만 실행
python3 scripts/versioning.py bump

# USB 없는 준비: 사유를 남기며 기존 치명적 오류 검토는 유지
python3 scripts/build.py --offline-diagnostics "Disconnected phone; prepare build without claiming fresh phone diagnostics."
```

검사 환경:

```bash
python3 tests/run_jvm_tests.py
python3 tests/run_android_contract_tests.py
python3 tests/run_diagnostic_jvm_tests.py

# 현재 PC의 브라우저 검사용 의존성 경로. 다른 환경에서는 새로 준비.
PYTHONPATH=/tmp/hermes-v008-test-deps:tests python3 -m unittest test_model_progress test_timeline_order test_browser_diagnostics -v
```

주의: 기본 Python 3.14 환경에는 `greenlet`이 없어 브라우저 검사가 import 단계에서 실패한 적이 있다. 위 경로는 현재 PC용이므로 새 환경에서 그대로 존재한다고 가정하지 않는다. 필요한 검사만 반복하며 이미 통과한 전체 검사를 이유 없이 계속 돌리지 않는다.

휴대폰: serial `R3CX20QTRPD`. 에뮬레이터 `emulator-5554`와 동시에 연결되므로 **반드시 대상 serial을 고정한다.**

```bash
.toolchain/android-test/platform-tools/adb devices -l
.toolchain/android-test/platform-tools/adb -s R3CX20QTRPD install -r dist/hermes-pocket-v0.12.apk
.toolchain/android-test/platform-tools/adb -s R3CX20QTRPD shell am start -n dev.chanho.hermes/.MainActivity
```

사용자가 승인한 것은 현재 앱의 직접 업데이트·검증 흐름이다. 재설치 때 앱을 지우거나 데이터를 초기화하면 기존 대화·키·스킬을 잃을 수 있으므로 무조건 uninstall하지 않는다. 현재 서명을 유지해야 같은 패키지를 업데이트할 수 있다. `.signing/`의 키와 비밀번호는 공개 인계 MD/공유 저장소에 넣지 말고 기존 로컬 서명 흐름에서 다룬다.

접근성·화면 위 표시·Android 보호 권한은 실제 상태를 확인해야 한다. USB가 빠져도 일반 앱/API 작업은 PC 없이 실행하지만, USB 설치·검증은 다시 연결해야 한다. Shizuku는 재부팅 후 서비스 시작 상태를 별도로 확인해야 한다.

## 10. 다음 담당자가 먼저 읽을 파일

- [README](../../hermes-android/README.md)
- [최신 검증 보고서](../../hermes-android/docs/TEST_REPORT.md)
- [v0.12 상세 보고서](../../hermes-android/docs/android-v012/TEST_REPORT.md)
- [S24 실제 설치](../../hermes-android/docs/android-v012/s24-install.json)
- [최종 에뮬레이터 검사](../../hermes-android/docs/android-v012/native-completion-summary.json)
- [호스트 검사별 증거](../../hermes-android/docs/android-v012/host-regression-results.json)
- [문맥·요약](../../hermes-android/docs/CONTEXT_COMPACTION.md)
- [메모리 문서 계약](../../hermes-android/docs/android-v012/MEMORY_DOCUMENTS.md)
- [오류 기록](../../hermes-android/docs/ANDROID_DIAGNOSTICS.md)
- [오류 검토·빌드 흐름](../../hermes-android/docs/diagnostics/README.md)
- [초기 원본 기능 감사 — 최신 상태로 갱신 필요](../../hermes-android/docs/HERMES_FEATURE_MATRIX.md)
- [일반 휴대폰 조작 범위](../../hermes-android/docs/HERMES_ANDROID_HUMAN_CONTROL_AUDIT.md)
- [원본 Hermes 참조](../../hermes-android/docs/HERMES_REFERENCE.md)
- [ARM/Python 엔진 이식 계획](../../hermes-android/engine-spike/docs/UPSTREAM_ANDROID_ENGINE_PLAN.md)

### Web Hermes에 붙여 넣을 시작 요청

> HERMES_WEB_HANDOFF.md를 읽고 Hermes Pocket Android 프로젝트 작업을 이어가 주세요. 현재 v0.12가 S24 Ultra Android16에 설치되어 있습니다. 완료된 기능과 실제 검증 범위를 먼저 확인하고, 원본 Hermes 전체 parity가 아직 완성되지 않았다는 점을 유지해 주세요. 멀티에이전트를 파일 소유권이 겹치지 않게 활용하고, 실제 실패 재현·오류 메타데이터·테스트를 근거로 기능을 개선해 주세요. 핵심 목표는 API 채팅으로 일반 사용자가 할 수 있는 휴대폰 작업을 안정적으로 수행하는 것입니다. APK는 Gmail이 아니라 USB로 직접 업데이트하며, 기존 데이터·서명은 유지해 주세요.
