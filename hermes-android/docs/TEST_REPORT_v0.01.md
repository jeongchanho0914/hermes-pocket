# v0.01 beta 검증 보고서

검증 날짜: 2026-10-04. 이 보고서는 현재 v0.01의 결과로 이전 소스 전용 보고서를 대체합니다.

## APK

- 파일: `dist/hermes-pocket-v0.01.apk`
- 패키지: `dev.chanho.hermes`, versionName `0.01`, versionCode `1`
- minSdk 26 (Android 8.0), targetSdk 35
- SHA256: `ed0f0c5837310319b9cf6512d2eb7bdbcc45d72076bd90f6c97645f961e2d1d9`
- 실제 Android API35 클래스 심볼로 javac 컴파일 → D8 DEX → zipalign → APK 서명 성공
- 독립 apksigner v2/v3 서명 검증, zipalign4, manifest 패키지·버전·실행 Activity 확인 성공
- ZIP 무결성, DEX 헤더, APK assets 원본 일치, 21개 소스 해시 일치 확인 성공
- 로컬 개발 서명: `.signing/`은 배포물에 포함하지 않음

증거: `dist/build-log.txt`, `dist/signature-verification.txt`, `dist/apk-manifest.txt`,
`dist/build-info.json`, APK SHA256 sidecar.

## Java 런타임: 17개 통과

`python3 tests/run_jvm_tests.py`는 실제 production `Net.java`, `DirectAgent.java`, `J.java`를
호스트 JVM에서 컴파일하고 실제 TCP HTTP API fixture로 연결합니다. Android 기기 도구와
저장은 테스트 대역입니다. 모델 API의 실사용 성능이나 Android 권한을 검증한 것은 아닙니다.

정상 스트림·대화 기록, 분할 도구 호출, DONE 없는 끊김, 종료 신호 오류·누락, 잘못된 인자,
전체 도구 묶음 사전 검증, 인증 실패·SSE 오류·content-type 오류, 취소, HTTP 주소 정책,
거부 후 자동 재시도 차단, 문맥 제한의 완전한 도구 묶음 보존, 메모리 수정 적용,
마지막 모델 요청의 도구 실행 제한을 검증했습니다. 최종 production 코드로 재실행해 17개 통과했습니다.

## 브라우저 모바일 UI: 23개 통과

- `test_ui.py`: 13개 통과. 최종 디자인 기준 12개 전체 재검증 후 시스템 바 색상 전달 회귀 1개 추가 통과.
- `test_designs.py`: 10개 전체 통과. 10스타일 × 5화면 너비 × 5섹션의 250개 레이아웃 조합 포함.
- 버전 표시, 10스타일의 native 시스템 바 색상·명암 전달, API만 사용하는 설정·키 초기화, 실행 횟수·문맥 설정 저장, 대화 메뉴와 검색,
  복사·Markdown, 스트리밍·스크롤·중단, 13개 수동 도구 요청 인자와 화면 선택을 확인했습니다.

Chromium+Playwright에서 실제 GUI를 사용하지만 native bridge는 테스트 대역입니다.
`docs/designs/` 화면 캡처는 브라우저 미리보기이며 Android 캡처로 주장하지 않습니다.

## 버전: 5개 통과

`test_versioning.py`: v0.09→v0.10, v0.99→v1.00 자리 올림, 채널 승격 시 번호 증가,
잘못된 버전·최대값·알 수 없는 필드 거부, Manifest/BuildConfig 동기화와 오래된 값 거부.
`versioning.py check`도 통과했습니다.

## 기존 Python 어댑터: 10개 통과 (별도)

`test_transport.py`는 보관된 `server/`의 이전 전송 코드만 검사합니다. 현재 Android 앱에서
이 서버로 연결하지 않으며, 이 결과를 Android API 추론 검증으로 계산하지 않습니다.

## Android 설치·실행: 최종 APK 검증 완료

공식 Android 15/API35 x86_64 에뮬레이터(KVM), Android WebView124에서 실제 APK를 설치했습니다.
모델은 로컬 HTTP API fixture이며 테스트에서만 adb reverse를 사용했습니다. shipping 앱에는
fixture나 PC 중계 런타임이 들어 있지 않습니다.

- 최종 APK SHA256이 `release-report.json`의 actual-tested 해시와 일치
- 설정 저장·실제 Android Keystore 키 암호화 및 API 인증 요청 확인
- `/models`와 선택 모델의 nonstream 채팅 연결 테스트 성공
- 빠른 스트리밍 채팅 3회, 최종 APK 채팅 완료 뒤 6초 대기 후 충돌 없음
- 실제 Android 기기 상태 도구 호출과 결과의 모델 API 재전송 확인
- 지연 스트림 중단, 미완료 답변 보존, 중단 후 늦은 응답 미수신 및 서비스 정리 확인
- 강제 종료·재실행 및 APK 업데이트 후 API 설정·키·대화 검색·세션 복원 유지
- 실제 승인 거부 시 미디어 볼륨 5/15 유지, 최종 APK 승인 시 요청50%로 8/15 변경
- Paper/Midnight 시스템 바 배경, 실제 소프트 키보드에서 헤더·입력창 가림 없음 확인
- 네이티브 승인 버튼 명암 수정 후 화면 확인

초기 APK에서 빠른 응답 후 foreground service 시작·종료 경쟁으로 앱 충돌을 발견했습니다.
서비스 승격 완료 신호 이후 모델 요청을 시작하도록 수정했고 실제 반복 실행으로 재검증했습니다.
Android15의 시스템 바는 WindowInsets와 배경 컨테이너로 처리합니다. 에뮬레이터의 3버튼
내비게이션 아이콘은 일부 테마에서 시스템 회색으로 표시되며 기기별 시스템 UI 차이가 있습니다.

상세 증거: [Android 검증](ANDROID_VERIFICATION.md),
`android-verification/release-report.json`, `release-logcat.txt`, 실제 Android 캡처.
이전 APK에서 확보한 핵심 루프·중단·복원 검증에 이어, 최종 APK에서는 채팅·설정 보존·
볼륨 승인 변경을 다시 검증했습니다. 최종 수정은 native 색상·insets·알림 시작 처리를 포함합니다.

## 아직 확인하지 않은 범위

유료 모델 제공자 API, 물리 휴대폰별 OEM 제한, 실제 Root 연동, 제조사별 접근성 동작,
장시간 백그라운드·ANR·성능은 아직 검증하지 않았습니다. 원본 Python Hermes 전체 엔진,
Skills·플러그인·음성·첨부·의미 기반 자동 요약은 이 베타에 구현되지 않았습니다.
