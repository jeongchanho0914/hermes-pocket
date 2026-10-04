# Hermes Pocket 버전 규칙

첫 배포는 **v0.01 beta**입니다. Android `versionName`은 `0.01`, `versionCode`는 `1`입니다. 과거 소스의 `0.1.0` 표기는 미배포 개발 표기였으며 새 번호 체계로 통일합니다. 이미 개인적으로 빌드한 이전 APK는 서명이나 번호가 다를 수 있으므로 업데이트 호환성을 보장하지 않습니다.

`version.json`이 유일한 버전 원본입니다. `major`는 큰 버전, `minor`는 00~99의 배포 순번, `channel`은 `beta` 또는 `stable`입니다. 이 체계는 소수점이나 Semantic Versioning이 아닙니다. 다음 배포 때마다 번호를 하나 올립니다. 버그 수정도 새 APK를 배포하면 번호를 올립니다.

| 배포 | 화면 표시 | Android versionName | Android versionCode |
|---|---|---|---|
| 최초 베타 | v0.01 | 0.01 | 1 |
| 다음 배포 | v0.02 | 0.02 | 2 |
| 아홉 번째 | v0.09 | 0.09 | 9 |
| 열 번째 | v0.10 | 0.10 | 10 |
| 0번대 마지막 | v0.99 | 0.99 | 99 |
| 자동 자리 올림 또는 정식 1버전 | v1.00 | 1.00 | 100 |
| 다음 배포 | v1.01 | 1.01 | 101 |

`versionCode = major × 100 + minor`이므로 항상 증가합니다. `v0.00`은 배포 전 단계 이름으로만 예약하며 설치용 릴리스에서는 허용하지 않습니다. Android가 허용하는 최대 versionCode는 2,100,000,000입니다. 채널만 바꿔 같은 번호를 다시 배포하지 않습니다. 베타를 정식으로 배포할 때도 `bump --channel stable` 또는 `bump --major --channel stable`로 번호를 올립니다.

```sh
python3 scripts/versioning.py show
python3 scripts/versioning.py check
# 다음 번호 미리보기 (파일 변경 없음)
python3 scripts/versioning.py bump --dry-run
# 다음 배포: v0.01 → v0.02
python3 scripts/versioning.py bump
# 큰 정식 배포: 현재 0.xx → v1.00
python3 scripts/versioning.py bump --major --channel stable
```

`bump`는 `version.json`, AndroidManifest.xml 버전 속성, Java BuildConfig 상수를 함께 갱신합니다. 수동 수정 뒤에는 `sync`를 실행합니다. `check`는 잘못된 번호, 누락된 필드, 오래된 Manifest/BuildConfig를 거부합니다. Gradle은 JSON에서 버전을 읽고 `preBuild`에서 동기화합니다. 직접 SDK 빌드도 같은 값을 사용합니다. UI는 네이티브 스냅샷의 버전을 표시하므로 별도 버전 문자열을 관리하지 않습니다.

## 업데이트와 빌드 식별

업데이트에는 **같은 패키지(`dev.chanho.hermes`), 같은 서명 키, 더 큰 versionCode**를 사용합니다. `.signing/`의 개인 키와 비밀번호는 배포 파일에 포함하지 않으며 안전한 곳에 보관합니다. 키를 잃으면 기존 설치에 덮어쓰는 업데이트를 만들 수 없습니다. 같은 번호의 APK를 재검증하는 빌드는 가능하지만, 사용자에게 새 배포할 때는 번호를 올립니다.

APK 이름은 `hermes-pocket-v0.01.apk`처럼 화면 표시 버전을 사용합니다. 배포 기록의 APK SHA-256, 서명 인증서, 소스 식별 정보로 동일 버전의 빌드도 구별합니다. APK 해시는 최종 APK 바이트의 식별값이며, 서명 시각과 빌드 환경에 따라 재빌드 해시는 달라질 수 있습니다. 파일 해시가 같다는 것은 동일 바이트라는 뜻이지, 해시를 기록했다고 재현 빌드가 보장되는 것은 아닙니다.
