# Hermes Pocket v0.13 작업 결과 및 인계

2026년 10월 4일. 성능 우선 Android 구현. **v0.13 베타를 S24 Ultra에 업데이트 설치하고 설치본 해시·버전·앱 실행을 확인했다. 원본 Hermes 전체 기능이 완성된 배포는 아니다.**

## 배포

- 프로젝트: `/home/chanho/Desktop/hermes-pocket-source/hermes-android`
- APK: `dist/hermes-pocket-v0.13.apk`
- 패키지: `dev.chanho.hermes` / versionCode 13
- 크기: 7,099,970 bytes
- SHA-256: `ce76dfbf9835b895e010ca46b02994676e58da03bf58526f26f9574e4ea342ad`
- 설치: `adb -s R3CX20QTRPD install -r` / 2026-10-04 17:28 KST
- 기존 서명 유지. uninstall/데이터 초기화 없음. 기존 대화·설정·키의 내용을 추출하거나 비교하지 않았다.
- 설치된 base.apk의 SHA/크기가 빌드와 일치. 앱 시작 명령 Status ok 및 프로세스 실행 확인.
- 상세 근거: `docs/android-v013/s24-install.json`, `dist/build-info.json`, `dist/signature-verification.txt`.

## 이번에 구현·통합한 기능

**독립 작업 실행:** 사용자 채팅과 별도로 최대 2개의 읽기 전용 모델 작업을 병렬 실행하고 최대 8개까지 실행/대기한다. 개별 HTTP 연결·대화·중단·결과를 분리하고 실제 결과를 저장한다. 모델/API 사용 비용은 별도로 발생할 수 있다. worker 자체의 화면·브라우저·셸·MCP·재귀 위임은 허용하지 않는다.

**채널과 수명:** 사용자 대화, 단일 기기 제어, 독립 분석 작업을 내부 채널로 분리했다. 새로운 TCP 포트를 열지 않았다. 실제 활성 작업이 있는 동안 ForegroundService를 유지하고 중지 요청이 실제로 정리될 때까지 소유권을 유지한다. 재시작 후 완료 결과는 보존하고 미완료 작업은 interrupted로 남기며 자동 재실행하지 않는다.

**CLI 느낌:** `you ›`, `hermes ›`와 실제 도구 타임라인을 유지하며 `/help`, `/status`, `/jobs`, `/bg`, `/result`, `/cancel`, `/stop`, `/plan`, `/tools`, `/skills`, `/memory`, `/compact`, `/new`를 연결했다. 로컬 명령은 가짜 모델 응답을 만들지 않는다. 작업 상태 갱신이 입력 중인 글을 지우거나 오래된 응답으로 최신 상태를 덮지 않도록 수정했다.

**최소 팝업:** 기본 접힘 상태의 작은 상태 표시와 중지/닫기, 필요할 때 입력을 펼치는 구조다. 음성 전용 인터페이스는 이번 구현에 포함되지 않았다.

**실제 기본 브라우저:** browser_search/open/snapshot과 web_search/web_fetch의 기본 경로를 실제 기본 브라우저로 연결했다. API 경로는 명시적 mode:api에서만 사용한다. 요청 전달·브라우저 전면 관찰·요청 페이지의 로드 완료·검색 결과 추출을 구분한다. 일반 브라우저가 보이지 않는 headless 브라우저가 된 것은 아니다.

**화면 조작 개선:** act_on_screen은 앱 패키지와 정확한 리소스 ID/라벨로, 실행 직전에 관찰한 요소 하나를 선택한다. 애매하거나 없는 대상은 누르지 않는다. 기존 승인·보호·45초 snapshot 제한을 유지했다. OBSERVATION_REQUIRED를 통해 오래된 좌표를 재사용하지 말고 새 화면을 읽도록 명시적으로 복구한다. 속도 개선율 수치는 측정하지 않았다.

## 검증 결과와 범위

| 검사 | 실제 결과 | 범위 |
|---|---:|---|
| JVM 생산 로직 | 189개 통과 | 기존 161개 + 작업16 + 선택/복구12 |
| Android SDK 생산 코드 계약 | 8개 통과 | 실제 Android 전체 소스 컴파일 및 계약 검증 |
| 진단 JVM | 10개 통과 | 진단 저장/수집 보호 |
| Python/브라우저 UI | 135개 통과 | 실제 브라우저 DOM/UI와 수집·버전 등 |
| 실제 에뮬레이터 CLI/작업 | 14개 통과 | 실제 WebView, 로컬 SSE, 작업2+채팅 동시 실행, 취소, 결과 재조회 |
| 에뮬레이터 실제 의미 기반 클릭 | 미검증 | 접근성 서비스 미연결로 전제 조건 검사 실패 |
| S24 배포 | 설치·해시·버전·프로세스 확인 | 실제 사용자 원격 API/모든 도구의 종합 검증과 다름 |

처음부터 한 번에 모두 통과한 것이 아니다. 기존 화면 도구 개수 기대값 두 곳을 새 도구에 맞게 고쳤고, 실제 Android에서 발견한 cli.js 로컬 자산 허용 누락도 고쳤다. 호스트 전체 성공 후 최종 앱 변경은 MainActivity의 cli.js 허용 한 줄이며, 실제 Android 재컴파일/계약 검사와 최종 APK의 WebView CLI 실행으로 확인했다. 이전 후보의 실패 기록은 `candidate-997528785a5a/` 및 초기 JVM 로그에 남겼다.

`native-verification.json`의 전체 allPassed는 false다. 작업/CLI/재조회14개는 통과했지만 접근성 클릭 검사의 전제가 충족되지 않았기 때문이다. 이 전체 실패를 임의로 성공으로 바꾸지 않았다.

설치 직후 수집된 오류 기록은 2개이며 모두 이전 v0.12 기록이다. 수집 시점 v0.13 기록은 0개였다. 이전 오류를 삭제하거나 완전히 해결됐다고 처리하지 않았다. 이 결과는 장시간 사용의 무오류 보증이 아니다.

## 원본 Python 엔진 실기기 실험

현재 메인 APK와 분리된 `dev.chanho.hermes.enginecandidate.fullwebp`를 S24에서 실행했다. Python3.14, 원본 AIAgent import, 세션·메모리·스킬 메타데이터, Pydantic, PNG/JPEG/WebP/움직이는 WebP 검사에 성공했다. 원본 스키마90개가 등록됐지만 90개 실행 성공을 의미하지 않는다. 실제 기기 페이지 크기는4096 bytes다.

원본 에이전트 대화는 테스트 서버 문맥32768이 원본 최소64000보다 작아 실패했고, 독립 코드 실행은 math 확장 모듈이 없어 실패했다. 수정 스크립트는 작성했으나 재빌드 도구 호출이 차단되어 실행하지 못했다. 따라서 Python/execute_code를 메인 APK에 포함하지 않았다. 시험 앱은 실행 종료 후 강제 중지했고 생산 앱의 개인 데이터에 접근하지 않았다.

근거: `docs/android-v013/s24-arm-candidate.json`. 대기 중 수정: `scripts/repair_arm_probe_v013.py`(미실행). 검사에 사용한 후보 APK SHA는43e6a835a7755cde5ec1c0d0df7feefd32c073550853ab8c048dbda755764693이다.

## 아직 완료하지 않은 부분

실제 S24에서 원격 모델 API·기본 브라우저 결과 읽기·새 의미 기반 클릭·장시간 백그라운드·팝업을 함께 사용하는 종합 검증이 남아 있다. 전체 Python AIAgent 통합, execute_code/PTY, MCP·플러그인 실행 전체, cron, gateway, OAuth/failover, 음성·이미지·영상 생성, 완전한 세션 분기/백업·복원은 이 배포에 구현 완료되지 않았다.

다음 작업은 `docs/android-v013/PERFORMANCE_ARCHITECTURE.md`의 실제/미구현 계약과 `RELEASE_STATUS.json`부터 읽는다. 원본 v0.12 인계 문서는 당시 이력으로 보존하며 새 버전 판단에는 이 문서와 실제 검증/설치 기록을 우선한다. 문서 개수·도구 등록·설치 성공을 실행 성공으로 바꾸어 표현하지 않는다.

## 바로 쓰는 명령 예시

```text
/help
/status
/bg 지금 제공한 설계 설명에서 오류 처리와 동시성 문제를 분석해줘
/jobs
/result 작업ID
/cancel 작업ID
/stop
```

작업ID는 /jobs에서 확인한 실제 값을 사용한다. 독립 작업에 필요한 문맥은 명시적으로 포함해야 하며, 인터넷 탐색이나 화면 조작이 필요한 작업은 먼저 기본 대화에서 실행한다.
