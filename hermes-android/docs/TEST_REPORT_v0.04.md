# v0.04 실제 검증

2026-10-04. APK 1,508,119바이트, SHA256 `ab0a2400fe571bde90ca26194a1b5908ab2d42443016582c7cd0d669c1f0989d`, versionCode4, 기존 서명 유지.

## 갤럭시 S24 Ultra / Android 16

v0.03에서 실제 MiMo 도구 호출 이름이 `get_device_statenull`로 저장돼 있었습니다.
Android JSONObject의 명시적 null을 문자열로 이어 붙인 것이 실제 원인이었습니다.
[이전 호출 기록](android-v003/s24-own-tool-trace.txt).

v0.04는 누락·JSON null은 무시하고 문자열만 이어 붙입니다. 실제 Android framework 검증과
같은 MiMo 요청의 `get_device_state` 호출·최종 응답을 확인했습니다.
[실제 MiMo](android-v004/s24-mimo-tool-call.json), [framework JSON](android-v004/s24-framework-json.json).
기기 모델 SM-S928N·배터리100% 응답이 실제 도구 결과와 일치했습니다.

앱 목록158개 조회·미디어 볼륨67% 실제 검증 성공. 밝기 변경은 실기기에서 승인이 거부되어
실행 성공으로 주장하지 않습니다. 접근성은 비활성화, Root는 미확인 상태이므로
해당 도구의 성공도 주장하지 않습니다.
[볼륨](android-v003/s24-volume.json), [밝기 거부](android-v004/s24-brightness.json).

USB 업데이트 후 실제 MiMo 요청을 검증하고, 사용자가 요청한 기존 앱 제거·새 설치도 성공했습니다.
사용자가 API를 다시 설정한 뒤 실제 플러그인 초안/저장/복원 상태를 확인했습니다.
네이티브 도구 수20 → 기기 플러그인 끄고 저장14 → 다시 저장20이 일치합니다.
[실기기 플러그인](android-v004/s24-plugin-persistence.json).

물리 기기 검사용 instrumentation이 테스트 종료 시 대상 앱 프로세스를 종료해
사용자가 앱 닫힘을 경험했습니다. 이후 검사 방식을 중단하고 두 보조 앱을 제거했습니다.
당시 Galaxy crash 버퍼에는 Hermes의 충돌 기록이 없었습니다.
원래 앱의 렌더러 종료 복구는 후속 v0.05에서 별도로 보완·검증합니다.

## 자동 검사

production Java/TCP42개, 브라우저43개 통과. 하단 설명 제거·키보드 배치의 별도2개 통과.
실제 SKILL.md 파일 저장·선택, 메모리/스킬 변경 승인·경합 보호, Tavily API 요청과
JSON null 스트림 회귀 검증을 포함합니다. 브라우저의 Android bridge는 테스트 대역입니다.
상용 Tavily 키로 실제 검색한 결과는 검증하지 않았습니다.
[로그](android-v004/hermes-v004-expanded-jvm.log).

## 전달

Gmail 다운로드 파일273바이트 HTML이 설치 실패 원인이었습니다. APK 첨부 대신 개인Drive에
정상 APK를 올렸고, 메타데이터의 파일 크기와 소유자만 접근하는 권한을 확인했습니다.
Drive 원시 바이트 재다운로드까지 확인한 것으로 주장하지 않습니다.

이 APK는 휴대폰용 자체 Java 에이전트와20개 도구이며, 원본 Python Hermes 전체는 아직
프로덕션에 포함되지 않습니다. 실제 원본 엔진 내장 검증은 `engine-spike/`에서 분리해 진행합니다.
