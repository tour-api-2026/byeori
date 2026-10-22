# 비공개 테스트 14일 — 출시 순서

Google Play 신규 개인 개발자 계정은 **비공개 테스트(closed testing)를 14일간** 돌려야
프로덕션을 신청할 수 있다. 그 기간에 **업데이트 이력이 2회 이상** 남아 있어야 한다.

## 먼저 알아야 할 것: OTA 는 세지 않는다

**OTA 로 아무리 많이 내보내도 Play 의 업데이트 이력은 0 이다.**

Play 가 세는 것은 트랙에 올라간 **AAB 의 versionCode** 뿐이다. OTA(`ota-update.yml`)는
JS 번들만 바꾸고 Play 는 그 존재를 모른다. 둘은 전혀 다른 경로다.

| | 무엇이 바뀌나 | Play 이력 | 언제 |
|---|---|---|---|
| OTA (`ota-update.yml`) | JS 번들만 | **안 남는다** | `main` 에 푸시할 때마다 자동 |
| 스토어 릴리스 (`store-release.yml`) | AAB 전체, versionCode 증가 | **남는다** | `v*` 태그를 밀 때 |

## 3~4일에 한 번, 태그를 민다

```bash
# 다음 versionCode 를 먼저 본다 — 태그의 +N 을 거기에 맞추면 나중에 태그만 보고
# 어느 빌드인지 알 수 있다. (이 값은 EAS 가 원격에서 관리한다)
cd apps/app && npx eas build:version:get --platform android --non-interactive

git checkout main && git pull
git tag v1.0.0+11     # 다음 versionCode 가 11 이면
git push origin v1.0.0+11
```

끝이다. `store-release.yml` 이 받아서 production 프로필로 AAB 를 빌드하고 비공개 테스트
트랙에 제출한다. versionCode 는 EAS 가 원격에서 자동으로 올린다
(`eas.json` 의 `appVersionSource: "remote"` + `autoIncrement: true`).

워크플로가 끝나면 Actions 요약에 version · versionCode · **트랙**이 찍힌다. 트랙이
테스터가 받는 트랙과 같은지 확인한다.

### 다음 날 할 일

안내받은 링크에서 **설치 이력**에 업데이트가 남았는지 본다. 테스터 기기가 실제로
새 버전을 받아야 이력이 생긴다 — 올리기만 하고 아무도 업데이트하지 않으면 안 잡힌다.

## 왜 `+N` 인가 — app.json 의 version 을 올리지 않는다

태그는 서로 겹칠 수 없는데 버전명은 `1.0.0` 그대로 두고 싶다. 워크플로의 태그 검사가
`+` 뒤를 떼고 비교하므로 `v1.0.0+11`, `v1.0.0+12` 가 전부 `1.0.0` 으로 통과한다.

`N` 은 아무 숫자나 되지만 **versionCode 와 맞추는 편이 낫다.** Play 에 올라간 것은
versionCode 로만 구분되므로, 태그가 그 번호를 달고 있으면 "이 릴리스가 어느 커밋인지"를
되짚을 수 있다. 맞추지 않아도 빌드는 된다(`v1.0.0+8` 로 versionCode 11 이 나갈 수 있다).

**`app.json` 의 `version` 은 올리지 않는다.** `runtimeVersion` 이 `fingerprint` 정책이라
version 을 바꾸면 런타임 버전이 갈린다:

```
version=1.0.0 → 9bb26f36c40e63a63589bf5accff648c9032b8f0
version=1.0.1 → 6829490569e32c4c6ebfb489e9c05a8f30647a75
```

갈리면 **이미 깔려 있는 앱이 OTA 를 더 받지 못한다.** 모두가 새 빌드로 갈아타기 전까지
옛 설치는 그 자리에 멈춘다. Play 이력은 versionCode 로 세므로 버전명을 올릴 이유도 없다.

버전명을 정말 올려야 할 때(프로덕션 출시 등)는 그 대가를 알고 올린다.

### 한 번은 어차피 갈린다 — 트랙을 바꿨기 때문

`eas.json` 도 fingerprint 입력이다. `app.json` 만이 아니다. 트랙을 `internal` 에서
`alpha` 로 바꾼 것만으로 런타임이 갈린다:

```
track=internal → 9bb26f36c40e63a63589bf5accff648c9032b8f0   ← v7 이 쓰던 런타임
track=alpha    → 2a60a46685db52ab4089e1cb1b846d20cb536ae3   ← 앞으로의 런타임
```

그래서 **이미 깔린 v7 은 이 변경 이후의 OTA 를 받지 못한다.** 테스터가 새 빌드를 깔면
그때부터 다시 받는다.

비공개 테스트 트랙으로 올리는 것은 요건이라 선택지가 없고, 어차피 테스터 전원이
3~4일마다 새 빌드로 갈아탈 참이라 손해는 짧다. 다만 **이 변경을 머지한 날 첫 태그를
바로 민다.** 머지만 해 두면 그날부터 OTA 가 아무에게도 닿지 않는다 — 새 런타임을 가진
빌드가 아직 없고, 옛 런타임은 더 이상 대상이 아니기 때문이다.

## 트랙 이름이 맞는지

`eas.json` 의 `submit.production.android.track` 이 `alpha` 다.

| Play Console 표기 | EAS track 값 |
|---|---|
| 내부 테스트 | `internal` |
| **비공개 테스트** | **`alpha`** |
| 공개 테스트 | `beta` |
| 프로덕션 | `production` |

비공개 테스트 트랙을 **추가로 만들어 이름을 따로 붙였다면** 그 이름을 그대로 쓴다
(Play 는 비공개 트랙을 여러 개 만들 수 있다). 트랙이 틀리면 빌드는 성공하는데 테스터에게
가지 않고 이력도 안 잡히므로, 첫 릴리스 뒤 Play Console 에서 꼭 확인한다.

## 14일 계획

기간 내 2회 이상이 요건이지만 **3~4일 간격으로 4회**를 권한다. 한 번 실패해도 여유가 있다.

| | 할 일 |
|---|---|
| D+0 | `v1.0.0+11` — 첫 릴리스 |
| D+1 | 설치 이력 확인 |
| D+4 | `v1.0.0+12` |
| D+5 | 설치 이력 확인 |
| D+8 | `v1.0.0+13` |
| D+12 | `v1.0.0+14` |
| D+14 | 프로덕션 신청 |

(마지막 프로덕션 빌드가 versionCode 10 이었으므로 다음이 11 이다. 2026-10-04 이후
모든 변경은 OTA 로만 나갔다 — Play 기준으로는 그때부터 업데이트가 없다.)

릴리스마다 실제로 바뀐 내용이 있는 편이 낫다. 지금은 OTA 로만 나간 변경이 쌓여 있어
(i18n 2단계 등) 그대로 빌드해도 내용이 있는 릴리스가 된다.

## 처음 한 번만: 시크릿 두 개

`store-release.yml` 은 GitHub 시크릿 두 개를 쓴다. 둘 다 빌드 **전에** 검사하므로
없으면 versionCode 를 쓰지 않고 멈춘다(되돌릴 수 없는 번호를 낭비하지 않는다).

- `EXPO_TOKEN` — expo.dev → Access Tokens. **등록되어 있다**(첫 실행에서 통과).
- `PLAY_SERVICE_ACCOUNT_JSON` — Google Cloud 서비스 계정 JSON 전문.

### 서비스 계정 키 만들기

Play Console 의 "API 액세스" 에서 시작하지 않는다. **Google Cloud Console 에서 계정을
만들고, Play Console 에서는 권한만 준다.** (Expo 공식 가이드와 같은 순서다 —
https://github.com/expo/fyi/blob/main/creating-google-service-account.md)

#### Google Cloud Console 에서

1. 프로젝트가 없으면 만든다 — https://console.cloud.google.com/projectcreate
2. **IAM 및 관리자 → 서비스 계정** (IAM & Admin → Service Accounts) →
   **서비스 계정 만들기** (Create Service Account).
3. 이름은 용도를 알아볼 수 있게 적는다(예: `play-release`). ID·설명은 선택이다.
   **Cloud 쪽 역할(role)은 주지 않아도 된다** — 권한은 Play Console 에서 준다.
   **만들고 닫기** (Create and close).
4. 목록에서 그 계정의 **이메일 주소를 복사한다.** 아래 7번에서 쓴다.
5. 그 계정의 점 세 개 메뉴 → **키 관리** (Manage keys) → **키 추가 → 새 키 만들기** →
   **JSON** → **만들기**. 파일이 받아진다. 다시 받을 수 없으니 잘 둔다.
6. **Google Play Android Developer API 를 켠다.** API 라이브러리에서 그 이름을 찾아
   **사용** (Enable) 을 누른다. 이걸 빼먹으면 제출이 권한 오류로 죽는다.

#### Play Console 에서

7. **사용자 및 권한** (Users and permissions) → **새 사용자 초대** (Invite new users).
   4번에서 복사한 **서비스 계정 이메일**을 넣는다.
8. **앱 권한** 탭에서 이 앱을 고른다(모든 앱에 주려면 **계정 권한** 탭을 쓴다).
9. 권한을 체크한다. 괄호 안은 영문 표기다.

   - 앱 액세스: **앱 정보 보기(읽기 전용)** (View app information (read-only))
   - 초안 앱: **초안 앱 수정 및 삭제** (Edit and delete draft apps)
   - 출시: **테스트 트랙에 앱 출시** (Release apps to testing tracks) ← **이게 핵심이다**
   - 출시: **테스트 트랙 관리 및 테스터 목록 수정** (Manage testing tracks and edit tester lists)
   - 출시: **프로덕션 출시, 기기 제외, Play 앱 서명 사용** (Release to production...)
   - 스토어 등록정보: **스토어 등록정보 관리** (Manage store presence)

   "관리자(모든 권한)" 는 필요 없다. 재무 데이터·주문·리뷰 답글도 필요 없다.
   **초대** 를 누른다.

   **테스트 트랙에 앱 출시** 가 없으면 빌드는 20분 돌고 제출에서 죽는다. 그때 쓴
   versionCode 는 돌아오지 않는다.

#### GitHub 에

10. Settings → Secrets and variables → Actions → **New repository secret.**
    이름 `PLAY_SERVICE_ACCOUNT_JSON`, 값은 5번에서 받은 **JSON 파일 내용 전문**
    (파일 경로가 아니라 `{` 로 시작하는 내용 전체).

워크플로가 `client_email` 과 `private_key` 가 있는지 먼저 확인하므로, 내용을 잘못
붙였으면 빌드 전에 멈춘다.

> Expo 가이드는 마지막에 `eas submit` 으로 키를 EAS 에 올리라고 하는데, 이 레포는
> 그 방식이 아니다. 워크플로가 GitHub 시크릿에서 키를 받아 `play-service-account.json`
> 으로 떨어뜨리고 `eas.json` 의 `serviceAccountKeyPath` 가 그 파일을 가리킨다. 그래서
> EAS 대시보드에 키를 올릴 필요가 없다.

> 권한을 막 준 직후에는 Play API 가 아직 모를 수 있다. 제출이 권한 오류로 죽으면
> 설정을 의심하기 전에 조금 기다렸다가 다시 돌려 본다.

### 다시 돌리기

시크릿을 넣은 뒤에는 태그를 새로 밀 필요 없이 실패한 실행을 다시 돌리면 된다.

```bash
gh run rerun <실행 ID> --failed
```

점검만 하고 싶으면 Actions 에서 `workflow_dispatch` 로 `submit=false` 를 주면 빌드까지만
돈다(versionCode 는 그래도 올라간다 — 되돌릴 수 없으니 자주 돌리지 않는다).
