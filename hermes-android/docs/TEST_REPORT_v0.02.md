# v0.02 검증 보고서

검증 날짜: 2026-10-04. [v0.01 기록](TEST_REPORT_v0.01.md)은 별도로 보관합니다.

## 최종 APK

- 파일: `dist/hermes-pocket-v0.02.apk`, 1,491,735바이트
- 패키지 `dev.chanho.hermes`, versionName `0.02`, versionCode `2`
- Android 8.0 이상(minSdk26), targetSdk35
- SHA256: `bb94a35d534cec262359d34e75374668e9f56cd7c3566313318909addb47a4c0`
- 기존 서명 키 유지, v1·v2·v3 서명 검증 통과
- ZIP 무결성, 리소스 비압축·4바이트 정렬, Manifest 및 전체 소스·자산 일치 확인
- 원본 Hermes 캐릭터를 APK에 포함, 외부 이미지 요청 없음

증거: `dist/build-info.json`, `signature-verification.txt`, `legacy-signature-verification.txt`,
`apk-manifest.txt`, `build-log.txt`.

## 실제 production Java: 26개 통과

호스트 JVM에서 production Net·DirectAgent·LocalCapabilities를 컴파일하고 실제 TCP API
fixture로 실행했습니다. Android 도구·저장은 이 검사에서 테스트 대역입니다.

기존 스트리밍 종료 검증·도구 호출·거부·중단·전체 턴 문맥 보존에 더해 제공업체 카탈로그,
기본 생각 수준 9개와 실제 API 전달값, 로컬 스킬 지침, 플러그인별 도구 노출·실행 차단,
실제 모델 목록 정규화, DeepSeek의 도구 호출 이후 비공개 추론 정보 재전송을 확인했습니다.

로그: [JVM](test-v002/jvm.log).

## 브라우저 GUI: 35개 검증 통과

`test_ui.py` 12개, `test_designs.py` 12개, `test_preferences.py` 11개.
화이트·블랙 × 5개 화면 너비 × 5개 섹션의 50개 레이아웃 조합을 포함합니다.

설정 홈·하위 화면·뒤로가기, 제공업체 선택→키 저장→실제 모델 선택 흐름,
기본 모델·생각 수준 재실행 및 새 대화 유지, 설정 부분 저장 시 인증 정보 보존,
9단계 선택, ＋ 팝업·포커스·닫기, 5행 모델 목록·스크롤, 작은 화면 메뉴 하단 접근,
기존 채팅·복사·중단·기록·13개 수동 기기 도구를 확인했습니다.

전체 실행에서 32개 통과 후 화면 구조 변경에 따른 테스트 선택자·숨김 상태 검사 3개를
수정해 해당 3개를 재검증했고 모두 통과했습니다. production 수정으로 해결한 실패는 없습니다.
마지막 CSS 변경은 생각 단계 영문·한글의 시각적 간격이며 별도 화면 캡처로 확인했습니다.

로그: [전체 실행](test-v002/gui-initial.log), [해당 3개 재검증](test-v002/gui-targeted.log).
Chromium+Playwright에서 native bridge는 테스트 대역입니다. 미리보기는 실기기 실행으로 주장하지 않습니다.

## 설치 오류 조사

사용자 기기는 Galaxy S24 Ultra / Android16이며 “앱 파일에 문제가 있습니다.”가 표시됐습니다.
실제 보낸 v0.01 이메일 MIME에서 추출한 APK는 빌드 파일과 바이트·SHA256이 같았습니다.
원본 v0.01·중간 v0.02·위 SHA256의 최종 v0.02를 공식 Android16/API36 에뮬레이터에 설치했으며 모두 성공했습니다.
따라서 Samsung 실물에서 발생한 오류의 원인은 아직 특정되지 않았습니다.
v0.02 빌드는 기존 키·패키지를 유지하고 v1·v2·v3 서명과 패키지 구조 검증을 강화했습니다.

최종 APK는 Android16/API36에서 설치 성공을 확인했습니다. 해당 에뮬레이터의 OS System UI가
앱 제거 상태에서도 ANR을 반복해, 최종 기능 검증은 Android15/API35에서 수행했습니다.
실제 production WebView·Android Keystore·SQLite를 사용해 API 키 암호화 저장,
16개 모델 목록 조회, 연결 테스트·스트리밍 채팅, 요청의 high 생각 수준·코딩 스킬 지침,
기기 플러그인 비활성화와 강제 호출 거부, 스트림 중단·부분 답변 보존,
제공업체 변경 시 키·모델·생각 수준 초기화 및 기본 API 주소 고정을 확인했습니다.
강제 종료·재실행 후 저장 설정과 세션도 유지됐습니다.

최종 APK의 상세 Android 실행 결과는 [Android 검증](ANDROID_V002_VERIFICATION.md)에 기록합니다.
중간 90,903바이트 APK의 API35 기록은 `android-v002/interim-v002-api35-install.json`이며
최종 APK의 설치 증거로 사용하지 않습니다.

## 검증 범위

실제 유료 제공업체의 모델 응답, Galaxy S24 Ultra 실물 설치, 실제 Root·제조사별 접근성,
장시간 백그라운드·성능은 검증하지 않았습니다. 현재 스킬은 로컬 지침 프리셋이고 플러그인은
내장 기기 도구 모듈입니다. 원본 Hermes의 Python 엔진 전체·외부 스킬 설치·OAuth·음성·첨부는
이식되지 않았습니다. 지원 API 형식과 단계별 실제 전달값은 README에 설명했습니다.

## Gmail 전달 확인

최종 APK를 사용자의 연결된 Gmail 계정으로 보냈습니다. 메시지 ID는
`1a10389ea4215545`입니다. 보낸 메일의 raw MIME를 다시 읽어 첨부 파일의 바이트를
최종 APK와 비교했고 완전히 같았습니다. SHA256은 위 최종 APK 값과 같습니다.
이 결과는 이메일 전달 무결성 확인이며 휴대폰에서 내려받은 파일까지 검증한 것은 아닙니다.
