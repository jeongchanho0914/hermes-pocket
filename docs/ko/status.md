# 프로젝트 현황

[← README로](../../README.ko.md) · [English](../en/status.md) · **한국어**

2026-10-04 v0.13 인계 문서 기준 **v0.13 베타** 스냅샷입니다. (원문: [handoff/HERMES_WEB_HANDOFF_V013.md](../handoff/HERMES_WEB_HANDOFF_V013.md))

## 릴리스

| 항목 | 값 |
|---|---|
| 패키지 | `dev.chanho.hermes` |
| 버전 | v0.13 베타, `versionCode` 13 |
| Android | minSdk 26 (Android 8.0), targetSdk 35 |
| APK 크기 | 7,099,970 bytes |
| APK SHA-256 | `ce76dfbf9835b895e010ca46b02994676e58da03bf58526f26f9574e4ea342ad` |
| 설치 기기 | 갤럭시 S24 Ultra, 제거 없이 업데이트 설치 (기존 데이터 유지) |
| 확인한 것 | 설치본 해시·크기·버전이 빌드와 일치, 앱 시작 및 프로세스 실행 |

## 동작하는 것

- 독립 백그라운드 작업: 동시 2개, 대기 포함 8개. 작업별 HTTP 연결·대화·중단·결과 저장. 모델·API 비용은 별도 발생.
- 채팅 UI, 도구 루프, 승인, 메모리, 스킬, 터미널, 접근성 제어, 웹 검색.
- 원본 Hermes 스킬 210개 포함, 209개 가져오기 가능.

## 검증의 정직한 기록

| 검사 | 결과 | 비고 |
|---|---|---|
| S24 설치·해시·버전·실행 | ✅ | 사용자 종합 테스트와는 다름 |
| 백그라운드 작업 / CLI / 재조회 검사 | ✅ 14개 통과 | |
| 에뮬레이터 의미 기반 클릭 | ⚠️ 미검증 | 접근성 서비스 미연결로 전제 조건 실패 |
| 전체 네이티브 검증 `allPassed` | ❌ `false` | 성공으로 바꾸지 않고 그대로 보존 |
| 설치 후 수집된 오류 | 2건, 모두 v0.12 기록 | v0.13 기록은 0건. 장시간 무오류 보증은 아님 |

처음부터 모두 통과하지는 않았습니다. 새 도구에 맞게 도구 개수 기대값 두 곳을 고쳤고, 실제 Android에서 발견한 `cli.js` 로컬 자산 허용 누락을 수정했습니다. 실패한 후보의 기록도 남겨 두었습니다.

## 기기 내 Python 엔진 실험

메인 APK와 분리된 시험 앱(`dev.chanho.hermes.enginecandidate.fullwebp`)을 S24에서 실행했습니다. Python 3.14, 원본 `AIAgent` import, 세션·메모리·스킬 메타데이터, Pydantic, PNG/JPEG/WebP 검사에 성공했고 원본 스키마 90개가 등록됐습니다. 이것이 90개 도구가 실행된다는 뜻은 **아닙니다**.

실패: 에이전트 대화(테스트 서버 문맥 32768이 원본 최소 64000보다 작음), 독립 코드 실행(`math` 확장 모듈 없음). 수정 스크립트(`scripts/repair_arm_probe_v013.py`)는 작성했지만 **실행하지 못했습니다**. 그래서 Python과 `execute_code`는 메인 APK에 포함하지 않았습니다.

## 아직 하지 않은 것

- 실제 S24에서 원격 모델 API·기본 브라우저 결과 읽기·의미 기반 클릭·장시간 백그라운드·팝업을 함께 쓰는 종합 검증
- 전체 Python `AIAgent`, `execute_code`/PTY, MCP·플러그인 실행, cron, gateway, OAuth/failover, 음성, 이미지·영상 생성, 완전한 세션 분기와 백업·복원
- Root: UID 0을 얻지 못했습니다. Shizuku의 Shell UID 2000은 Root와 다릅니다.

## 다음 작업 시작점

`hermes-android/docs/android-v013/PERFORMANCE_ARCHITECTURE.md`와 `RELEASE_STATUS.json`부터 읽으세요. 문서 개수나 도구 등록보다 실제 설치·테스트 기록을 우선합니다.
