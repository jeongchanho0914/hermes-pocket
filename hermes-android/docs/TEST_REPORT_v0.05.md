# v0.05 실제 검증

2026-10-04. APK 1,512,215 bytes, versionCode 5, SHA-256 `7cc7e0c63dd2908860f311c05cc2a2261f29cab9ba121fbb14c759ebef876dce`. 기존 서명 유지, v1/v2/v3 검증 통과.

## 갤럭시 S24 Ultra / Android 16

v0.05 USB 업데이트 설치와 콜드 실행 성공. 기존 설정을 유지했습니다. 실제 설치 패키지 versionName 0.05, versionCode 5, debuggable 없음. APK에 네이티브 `.so`와 엔진 테스트 코드가 없습니다.

사용자가 보낸 스크린샷은 별도 `dev.chanho.hermes.engineprobe`의 JSON 출력 뒤 Android 앱 호환성 팝업입니다. 디버그 테스트 앱의 16KB ELF 정렬 검사 경고이며, 본 앱 v0.05의 설치 실패가 아닙니다. 테스트 앱을 제거했습니다. 실제 S24 커널은 4KB입니다. 이 결과로 원본 엔진 후보의 모든 기기 16KB 지원을 주장하지 않습니다. [공식 Android 문서](https://developer.android.com/guide/practices/page-sizes).

기존 실제 MiMo 도구 호출 오류 수정·플러그인 저장 검증은 [v0.04 보고서](TEST_REPORT_v0.04.md)를 참조하세요. 물리 기기 instrumentation 사용을 중단했으며 테스트 종료가 본 앱을 닫는 보조 앱도 제거했습니다. 접근성 서비스는 사용자가 직접 켠 뒤 Android 설정에서 활성화를 확인했습니다. Root 명령은 현재 확인되지 않았습니다.

## API 35 에뮬레이터

실제 chrome://crash 렌더러 종료 후 WebView 교체·브리지 복구, 같은 Activity/AgentRuntime 유지 성공. stopAll 이후 수동 기기 상태 실행에서 Net 취소 상태 초기화와 실제 audit 완료 확인. [기록](android-v005/lifecycle.json).

메모리 변경 승인 거부 시 미변경, 승인 저장; 실제 SQLite 대화 검색; SKILL.md 저장/조회/선택; 웹 키 저장/삭제와 모델 키 유지; 키 없는 Tavily 호출의 명시적 거부를 확인했습니다. [검증](android-v005/local-agent.json). 실제 모델 테스트 API 요청에 메모리·스킬·생각 수준과 20개 도구 스키마가 전달됩니다. [요청 검증](android-v005/wire-assertions.json). 상용 Tavily 검색 성공으로 주장하지 않습니다.

실제 입력창 터치로 IME를 연 뒤 새 대화 버튼을 누르면 IME 닫힘·포커스 해제·입력 초기화 성공. [기록](android-v005/newchat-ime.json).

GUI 회귀 49개 통과. 브라우저 AndroidBridge는 테스트 대역입니다. 이전 production Java/TCP 검사 42개 통과.

## 배포와 남은 범위

개인 Drive의 정상 APK 파일 크기와 소유자 전용 권한을 확인하고, 다운로드 링크를 사용자 본인 Gmail로 전송했습니다. USB 설치 성공과 Drive 업로드 메타데이터 확인을 별도로 구분합니다.

설정 홈 7개 항목, 도구/고급 설정 하위 화면, 사이드바 하단 둥근 설정 버튼, 화이트/블랙, 입력창 하단 배치, 스트리밍 스크롤과 초안 복원 개선을 포함합니다.

별도 실제 원본 Hermes Python 엔진의 S24 memory→skill_view→최종 응답 3회 도구 루프는 통과했습니다. [실제 결과](../engine-spike/evidence/s24-engine-conversation-fixed.json). 그러나 그 엔진은 v0.05에 포함되지 않으며 네이티브 라이브러리 16KB 호환성과 Android 도구 연결이 추가로 필요합니다. 휴대폰 전체를 자유롭게 제어하는 최종 목표에 아직 도달하지 않았습니다.
