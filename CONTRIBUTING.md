# 기여 가이드 · Contributing

벼리(Byeori) 저장소의 **Git · GitHub 협업 규칙**입니다. 모든 작업은 이 규칙을 따릅니다.
(전체 개발 표준은 [`docs/byeori-tech-spec.pdf`](docs/byeori-tech-spec.pdf) §1.7 참고)

---

## 브랜치 전략 — GitHub Flow

`main`은 **항상 배포 가능한 상태**를 유지하는 보호 브랜치입니다. 모든 작업은 짧은 수명의 토픽 브랜치에서 진행하고 **Pull Request → 리뷰 → Squash 머지**로 합칩니다. `main`에 머지되면 CI가 자동 배포합니다.

```
main ──●─────────────●──────────────●──▶   (보호 · 자동 배포)
        \           /  \            /
         feature/…  ●    fix/…     ●         (토픽 브랜치, PR 머지 후 삭제)
```

- `main` **직접 push 금지** (브랜치 보호 규칙으로 강제)
- 운영 긴급 수정은 `hotfix/`를 `main`에서 분기

---

## 브랜치 명명 규칙

형식: **`<type>/<이슈번호>-<요약>`**

- 소문자 **kebab-case**, 영문 사용 (공백·대문자·한글 금지)
- 요약은 2~4단어, 단어 구분은 `-`, type과 요약 구분은 `/`
- 이슈번호는 있으면 앞에 붙이고, 없으면 생략
- 개인 식별이 필요하면 `<type>/<이름>/<요약>`도 허용

```
feature/12-hanbok-filter
fix/45-review-rating-cache
docs/branch-convention
chore/seed-tour-api-sync
hotfix/89-login-token-expiry
```

| type | 용도 | 분기 기준 |
|---|---|---|
| `feature/` | 기능 개발 | `main` |
| `fix/` | 버그 수정 | `main` |
| `hotfix/` | 운영 긴급 수정 | `main` |
| `refactor/` | 동작 변경 없는 구조 개선 | `main` |
| `chore/` | 설정·빌드·의존성·시드 | `main` |
| `docs/` | 문서 | `main` |
| `test/` | 테스트 추가·보강 | `main` |

---

## 커밋 메시지 — Conventional Commits

형식: **`<type>(<scope>): <요약>`** — 모노레포이므로 `scope`로 영역을 명시합니다.

- `type`: `feat` `fix` `refactor` `chore` `docs` `test` `style` `perf`
- `scope`: `api` `app` `web` `infra` `docs` (생략 가능)

```
feat(api): 한복 혜택 필터 쿼리 추가
fix(app): 리뷰 평점 캐시 미갱신 수정
docs: 브랜치 명명 규칙 추가
```

---

## Pull Request · 머지

- PR 제목도 **Conventional Commits** 형식을 따릅니다.
- 머지 전략은 **Squash and merge** (커밋 히스토리를 단정하게 유지).
- **최소 1인 리뷰 승인** 후 머지합니다.
- 머지 후 토픽 브랜치는 **자동 삭제**됩니다.
- `main`은 force push·브랜치 삭제·머지 커밋이 차단되어 있습니다(선형 히스토리 강제).

---

## 작업 흐름 요약

```bash
git switch main && git pull
git switch -c feature/12-hanbok-filter   # 규칙대로 분기
# ... 작업 & 커밋: feat(api): ...
git push -u origin feature/12-hanbok-filter
gh pr create                              # PR 생성 → 리뷰 → Squash 머지 → 브랜치 자동 삭제
```

---

## 릴리스 · 버전관리

배포 경로가 **둘**이고, 무엇이 바뀌었는지에 따라 갈립니다.

| 바뀐 것 | 경로 | 반영 시간 | 사용자 행동 |
|---|---|---|---|
| JS · 에셋만 | **OTA** — `main` 머지 시 자동 | 몇 분 | 앱 재시작 |
| 네이티브 | **스토어 빌드** — `v*` 태그 push | 심사 포함 수시간~ | 스토어에서 업데이트 |

네이티브가 바뀌면 `runtimeVersion`(fingerprint)이 달라져 **OTA가 기존 설치본에 닿지 않습니다.** `ota-update.yml`의 fingerprint 가드가 이를 잡아 CI를 실패시킵니다 — 그 실패는 버그가 아니라 "스토어 빌드가 필요하다"는 신호입니다.

fingerprint가 바뀌는 조건:

- `package.json`에 네이티브 코드를 가진 패키지 추가·버전 변경
- `app.json`의 `plugins` · `android` · `ios` · `permissions` 변경
- `app.config.js` 로직 변경
- **`eas.json` 변경** — `submit` 섹션만 고쳐도 해시가 바뀐다(2026-10-03 실측)
- Expo SDK 업그레이드

따라서 `eas.json`을 손대면 그 뒤로는 새 스토어 빌드가 나오기 전까지 OTA가 막힙니다. 설정만 바꾸는 커밋이라도 마찬가지입니다.

### 버전 축 4개

```
version        1.0.0        사람이 읽는 이름. 스토어 표시. app.json(git)에서 관리
versionCode    6            Play가 신·구 빌드를 판정하는 유일한 값. EAS가 자동 증가. 되돌릴 수 없음
runtimeVersion 9a0b09c4…    네이티브 호환 경계. OTA 도달 범위를 결정. 자동 계산
updateId       01a0c46e-…   OTA 한 건의 식별자. gitCommitHash가 함께 기록됨
```

- `version`은 **스토어 제출할 때만** 올립니다. OTA로는 올리지 않습니다.
- `versionCode`는 손대지 않습니다 (`appVersionSource: remote` + `autoIncrement`).

### 스토어 릴리스 절차

```bash
# 1. fingerprint 확인 — 바뀌었다면 스토어 빌드가 필요하다는 뜻
cd apps/app && npx expo-updates fingerprint:generate --platform android

# 2. app.json의 version 올림 (versionCode는 건드리지 않음) → 커밋 → PR → main 머지

# 3. 머지된 커밋에 태그를 달면 CI가 빌드 + Play 내부 테스트 트랙 제출까지 수행
git switch main && git pull
git tag -a v1.3.0 -m "Play 제출"
git push origin v1.3.0

# 4. Play Console에서 내부 테스트 → 프로덕션 트랙으로 수동 승격
```

태그와 `app.json`의 `version`이 다르면 워크플로가 빌드 전에 실패합니다. 태그는 `v1.3.0` 또는 `v1.3.0+7`(versionCode 병기) 형식을 씁니다.

제출 없이 파이프라인만 점검하려면 Actions에서 **Store Release** 워크플로를 수동 실행하고 `submit`을 `false`로 둡니다.

### 필요한 시크릿

| 시크릿 | 용도 |
|---|---|
| `EXPO_TOKEN` | EAS 빌드·업데이트 인증 (expo.dev → Access Tokens) |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play 제출용 Google 서비스 계정 키 JSON 전문 |

서비스 계정은 Google Cloud에서 만들고, Play Console '사용자 및 권한'에서 초대해 **앱 출시** 권한을 줍니다. 키 파일은 CI가 시크릿에서 임시로 만들고 잡이 끝나면 지웁니다 — 저장소에 두지 않습니다.

> Google Play Developer API는 **신규 앱의 첫 AAB를 올릴 수 없습니다.** Play Console에서 수동으로 한 번 업로드해 앱을 활성화한 뒤부터 이 파이프라인이 동작합니다.

### OTA 롤백

```bash
eas update:list --branch production            # 되돌릴 대상 확인
eas update:republish --group <이전 group id>    # 이전 업데이트 재발행 (권장)
eas update:roll-back-to-embedded               # 빌드에 박힌 번들로 복귀 (최후 수단)
```

위험한 변경은 일부에게만 먼저 내보낼 수 있습니다.

```bash
eas update --channel production --rollout-percentage 10
eas update:revert-update-rollout               # 문제가 있으면 되돌린다
```
