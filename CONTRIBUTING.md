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
