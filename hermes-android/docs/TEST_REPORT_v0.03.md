# v0.03 검증 기록

2026-10-04. 최종 APK 1,491,735바이트, 패키지 dev.chanho.hermes, versionCode 3.
SHA256 `b079bab979b6a3fa18e19151b6a53c575ad1ec2c25550a1618e13d8c6cc02c5e`.
기존 키 유지, v1/v2/v3 서명과 리소스 정렬 검증 성공.

## 실제 설치 오류 원인

USB로 연결한 Galaxy S24 Ultra SM_S928N / Android 16의 다운로드 폴더에서
v0.01·v0.02 APK 파일은 각각 273바이트였습니다. APK 대신 Gmail이 차단한 HTML
오류 문서가 저장돼 있었습니다. 실제 문서는 [다운로드 오류](android-v003/gmail-download-error.html)에 보관했습니다.
[Gmail 공식 안내](https://support.google.com/mail/answer/6590?hl=ko)는 APK 첨부 다운로드를 차단할 수 있다고 설명합니다.

정상 v0.03 APK를 USB로 복사했고 휴대폰 파일 SHA256이 호스트와 일치했습니다.
`adb install -r --no-streaming` 성공, 설치된 버전 0.03/code3 및 앱 시작 성공을 확인했습니다.
[실물 시작 화면](android-v003/s24-startup.png).

## GUI 및 Android 검사

브라우저에서 서로 다른 39개 검증 통과: 기본 모델·생각 수준 각각 독립 선택,
초기 중앙 연결 안내, 미설정 모델 선택 차단, 모델 5행 표시와 스크롤, 화이트·블랙
레이아웃 및 제공업체 키 삭제 시 연결 상태 갱신을 포함합니다. 네이티브 bridge는 브라우저에서 테스트 대역입니다.

실제 release WebView의 에뮬레이터 검증 자료:
[초기 안내](android-v003/first-launch-checks.json),
[선택 화면](android-v003/picker-checks.json),
[재실행 기본 설정](android-v003/cold-defaults.json).

Samsung 실제 모델 도구 호출 전체는 이 버전에서 검증 완료하지 않았습니다.
후속 v0.04에서 MiMo 도구 호출 프로토콜과 기능 저장 상태를 수정·검사합니다.
