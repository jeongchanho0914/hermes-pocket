# 휴대폰 폴더와 파일 접근

v0.06 작업 소스는 Android Storage Access Framework(SAF)를 사용합니다.
실제 APK·기기 검증 결과는 해당 버전의 검증 보고서와 함께 확인하세요.

설정의 기기 도구에서 **사용할 폴더 선택**을 누르고 Android 선택기로 폴더를
직접 선택합니다. 앱은 그 폴더의 읽기·쓰기 권한만 저장하며, 선택 성공 시 파일
도구 모듈을 활성화합니다. 선택을 취소하면 기존 폴더를 유지합니다.
**접근 해제**는 저장 권한과 폴더 설정을 제거합니다. 앱 삭제 후에는 다시 선택해야 합니다.

Android 11 이상에서는 다운로드 루트, 저장소 루트, Android/data 등 일부 위치를
폴더 선택기로 허용할 수 없습니다. 제한을 우회하지 않습니다. 다운로드 안에
Hermes 같은 하위 폴더를 만든 뒤 해당 폴더를 선택할 수 있습니다.
[Android 공식 폴더 접근 안내](https://developer.android.com/training/data-storage/shared/documents-files#document-tree-access-restrictions).

| 도구 | 실제 기능 | 제한 |
| --- | --- | --- |
| `phone_list_files` | 선택한 폴더 또는 하위 폴더의 항목 목록 | 최대 200개, 초과 시 truncated 표시 |
| `phone_read_file` | 선택한 폴더의 UTF-8 텍스트·코드 읽기 | 파일 128,000바이트, 응답 최대 32,000자 |
| `phone_write_file` | 새 텍스트·코드 생성 또는 기존 내용 교체 | 내용 32,000자·128,000바이트, 사용자 승인 필요 |

PDF·이미지·음성·바이너리 내용 추출은 이 도구에 포함하지 않습니다. `.txt`, `.md`,
`.json`, `.csv`, `.log`, `.yaml`, 코드 파일 등 텍스트를 처리합니다. 하위 폴더는
이미 있어야 하며 폴더 생성·파일 삭제·이동 기능은 제공하지 않습니다.

모델에는 폴더의 실제 content URI를 전달하지 않습니다. 모든 경로는 선택한 폴더를
기준으로 한 상대 경로입니다. 절대 경로, 다른 provider URI, 상위 폴더 이동, 제어문자,
과도한 깊이·길이를 거부합니다. 앱 개인 저장소의 API 인증 정보는 이 경로로 접근하지 않습니다.

파일 교체에는 `overwrite=true`와 사용자의 승인이 모두 필요합니다. 승인 중 선택
폴더나 기존 파일이 바뀌면 저장하지 않습니다. 저장 후 실제 파일을 다시 읽어 입력
바이트와 일치한 경우에만 `verified=true`를 반환합니다. 저장은 선택한 DocumentsProvider의
동작을 사용하므로 전체 파일 교체의 원자성을 보장하지는 않습니다. 실패하면 성공으로
표시하지 않으며 파일을 다시 읽어 결과를 확인해야 합니다.

권한을 해제했거나 파일 제공자를 사용할 수 없으면 설정에서 다시 폴더를 선택하도록
안내합니다. 읽기 전용 권한이면 목록과 읽기만 가능하며 쓰기 요청은 거부합니다.
감사 기록에는 파일 경로나 본문을 남기지 않습니다.

구현: `PhoneFiles.java`, 경로 정책 `PhoneFilesPaths.java`, MainActivity의 시스템 폴더
선택기와 Store의 URI 설정. UI 상태는 `snapshot.files` 및 `files` 이벤트의
configured/readable/writable/displayName/permissionLost/message를 사용합니다.
