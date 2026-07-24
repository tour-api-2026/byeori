# Play Store 출시 + 자동 OTA 파이프라인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 벼리 앱을 Play Store에 올릴 수 있게 production 빌드를 AAB로 전환하고, `main` push 시 자동 OTA 발행 + 인앱 자동 적용을 구현한다.

**Architecture:** (1) `eas.json` production 프로필을 AAB로 전환, (2) `expo-updates` 기반 자동 적용 훅을 루트 레이아웃에 연결, (3) GitHub Actions가 `main` push 시 fingerprint를 검사한 뒤 `eas update --channel production`을 발행.

**Tech Stack:** Expo ~54 / expo-updates ~29 / EAS CLI / GitHub Actions

**Spec:** `docs/superpowers/specs/2026-07-19-play-store-ota-design.md`

## Global Constraints

- 앱 디렉토리: `apps/app` (모노레포 — CI의 working-directory 주의)
- 훅 파일명은 kebab-case: `src/hooks/use-auto-update.ts` (기존 `use-color-scheme.ts` 컨벤션)
- `runtimeVersion.policy = "fingerprint"`, 채널명은 `production` (eas.json과 일치, 변경 금지)
- OTA 훅 가드: `__DEV__ === true` 또는 `Updates.isEnabled === false`면 no-op
- 포그라운드 재체크 스로틀: 30분
- 프로젝트에 테스트 러너 없음 → 각 태스크 검증은 `npx tsc --noEmit` + 실기기(preview 채널) 검증으로 대체
- `EXPO_PUBLIC_*` 값(eas.json에 이미 커밋된 공개값)은 CI의 `eas update` 시에도 동일하게 주입해야 함

---

### Task 1: production 빌드를 AAB로 전환

**Files:**
- Modify: `apps/app/eas.json:40-42` (production의 `android.buildType`)

**Interfaces:**
- Produces: Play Store 제출 가능한 production 프로필. Task 4(수동 절차)의 `eas build --profile production`이 이 설정을 사용.

- [x] **Step 1: buildType 변경**

`apps/app/eas.json`의 production 프로필에서:

```json
      "android": {
        "buildType": "app-bundle"
      }
```

(development/preview 프로필의 `"apk"`는 그대로 둔다.)

- [x] **Step 2: JSON 유효성 확인**

Run: `node -e "JSON.parse(require('fs').readFileSync('apps/app/eas.json','utf8')); console.log('OK')"`
Expected: `OK`

- [x] **Step 3: Commit**

```bash
git add apps/app/eas.json
git commit -m "feat(app): production 빌드를 AAB(app-bundle)로 전환 — Play Store 제출용"
```

---

### Task 2: 인앱 자동 OTA 적용 훅

**Files:**
- Create: `apps/app/src/hooks/use-auto-update.ts`
- Modify: `apps/app/src/app/_layout.tsx` (RootLayout 상단에 훅 호출 1줄 + import)

**Interfaces:**
- Produces: `useAutoUpdate(): void` — 인자 없음, 반환 없음. RootLayout에서만 호출.

- [x] **Step 1: 훅 작성**

`apps/app/src/hooks/use-auto-update.ts`:

```ts
import * as Updates from 'expo-updates';
import { useEffect, useRef } from 'react';
import { AppState } from 'react-native';

// 포그라운드 복귀 시 재체크 최소 간격
const CHECK_INTERVAL_MS = 30 * 60 * 1000;

async function applyUpdateIfAvailable() {
  const update = await Updates.checkForUpdateAsync();
  if (!update.isAvailable) return;
  await Updates.fetchUpdateAsync();
  await Updates.reloadAsync();
}

// OTA 자동 적용: 콜드 스타트 + 포그라운드 복귀(30분 스로틀) 시
// 새 번들을 받아 즉시 reload한다. 실패는 조용히 넘기고 다음 체크에서 재시도.
export function useAutoUpdate() {
  const lastCheckedAt = useRef(0);

  useEffect(() => {
    if (__DEV__ || !Updates.isEnabled) return;

    const check = () => {
      const now = Date.now();
      if (now - lastCheckedAt.current < CHECK_INTERVAL_MS) return;
      lastCheckedAt.current = now;
      applyUpdateIfAvailable().catch(() => {});
    };

    check();

    const sub = AppState.addEventListener('change', (state) => {
      if (state === 'active') check();
    });
    return () => sub.remove();
  }, []);
}
```

- [x] **Step 2: RootLayout에 연결**

`apps/app/src/app/_layout.tsx`에 import 추가:

```ts
import { useAutoUpdate } from '@/hooks/use-auto-update';
```

`RootLayout` 함수 본문 첫 줄(`const [client] = useState(...)` 위)에:

```ts
  useAutoUpdate();
```

- [x] **Step 3: 타입체크·린트 통과 확인**

Run: `cd apps/app && npx tsc --noEmit && npm run lint`
Expected: 에러 0건

- [x] **Step 4: Commit**

```bash
git add apps/app/src/hooks/use-auto-update.ts apps/app/src/app/_layout.tsx
git commit -m "feat(app): OTA 자동 적용 훅 — 시작/포그라운드 시 감지 후 즉시 reload"
```

---

### Task 3: GitHub Actions — main push 시 자동 OTA

**Files:**
- Create: `.github/workflows/ota-update.yml`

**Interfaces:**
- Consumes: GitHub 시크릿 `EXPO_TOKEN` (Task 4에서 사용자가 등록)
- Produces: `main` push(경로 `apps/app/**`) 시 production 채널 OTA 발행. 네이티브 fingerprint가 최신 production 빌드와 다르면 실패로 알림.

- [x] **Step 1: 워크플로 작성**

`.github/workflows/ota-update.yml`:

```yaml
name: OTA Update (production)

on:
  push:
    branches: [main]
    paths: ['apps/app/**']

jobs:
  ota:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: apps/app
    env:
      # eas.json production 프로필과 동일한 공개 env — update 번들에도 박혀야 함
      EXPO_PUBLIC_API_URL: https://byeori.seoulride.site/api/v1
      EXPO_PUBLIC_KAKAO_JS_KEY: c67499ab00bd0367a2de44ba3990169d
      EXPO_PUBLIC_KAKAO_NATIVE_KEY: 7f769acfbde8a4f7d9dedba42e0580a4
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: apps/app/package-lock.json

      - uses: expo/expo-github-action@v8
        with:
          eas-version: latest
          token: ${{ secrets.EXPO_TOKEN }}

      - run: npm ci

      # 네이티브 변경 감지: 로컬 fingerprint ≠ 최신 production 빌드의 runtimeVersion이면
      # 이 OTA는 기존 설치자에게 도달하지 않는다 → 실패 처리로 "새 스토어 빌드 필요" 알림
      - name: Fingerprint check (native change detection)
        run: |
          LOCAL_FP=$(npx expo-updates fingerprint:generate --platform android | jq -r '.hash')
          BUILD_RTV=$(eas build:list --platform android --channel production --limit 1 --non-interactive --json | jq -r '.[0].runtimeVersion // empty')
          echo "local=$LOCAL_FP latest-build=$BUILD_RTV"
          if [ -z "$BUILD_RTV" ]; then
            echo "::notice::production 빌드가 아직 없음 — fingerprint 검사 건너뜀"
          elif [ "$LOCAL_FP" != "$BUILD_RTV" ]; then
            echo "::error::네이티브 변경 감지 — 이 OTA는 기존 빌드에 도달하지 않습니다. eas build --profile production 후 스토어 제출이 필요합니다."
            exit 1
          fi

      - name: Publish OTA update
        run: eas update --channel production --message "${{ github.event.head_commit.message }}" --non-interactive
```

- [x] **Step 2: YAML 유효성 확인**

Run: `node -e "const yaml=require('js-yaml')" 2>/dev/null || python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ota-update.yml')); print('OK')"`
Expected: `OK`

- [x] **Step 3: Commit**

```bash
git add .github/workflows/ota-update.yml
git commit -m "ci: main push 시 production 채널 자동 OTA 발행 + 네이티브 변경 감지"
```

---

### Task 4: 사용자 수동 절차 (코드 외 — 체크리스트)

코드 태스크가 아니라 hidi가 직접 수행하는 절차. 순서대로:

- [ ] **4-1. EXPO_TOKEN 시크릿 등록**
  1. https://expo.dev/accounts/hidi_16/settings/access-tokens 에서 토큰 발급
  2. GitHub `tour-api-2026/byeori` → Settings → Secrets and variables → Actions → `EXPO_TOKEN` 등록

- [ ] **4-2. 서명 키 확인**

  ```bash
  cd apps/app && eas credentials
  ```
  Android → production → Keystore가 EAS 관리인지 확인. 로컬 `@hidi_16__app.jks`는 백업으로 보관.

- [ ] **4-3. production AAB 빌드**

  ```bash
  cd apps/app && eas build --profile production --platform android
  ```
  완료 후 expo.dev 빌드 페이지에서 `.aab` 다운로드.

- [ ] **4-4. Play Console 앱 생성 + 첫 업로드 (수동 필수)**
  1. https://play.google.com/console → 앱 만들기 (`com.byeori.app`, 한국어)
  2. 내부 테스트 트랙에 AAB 업로드
  3. 스토어 등록정보: 스크린샷·설명·**개인정보처리방침 URL**(위치 권한 사용으로 필수)·데이터 보안 설문·콘텐츠 등급
  4. 내부 테스트로 검증 후 프로덕션 승격

- [ ] **4-5. (첫 출시 후, 선택) eas submit 연결**
  Google Cloud 서비스 계정 JSON 발급 → `eas.json`의 `submit.production.serviceAccountKeyPath` 연결. 이후 재빌드 제출은 `eas build` → `eas submit -p android`.

---

### Task 5: E2E 검증 (preview 채널)

**Files:** 없음 (검증 전용)

**Interfaces:**
- Consumes: Task 2의 훅이 포함된 빌드

- [ ] **Step 1: preview 빌드 생성·설치**

```bash
cd apps/app && eas build --profile preview --platform android
```
Expected: APK 링크 생성 → 폰에 설치.

- [ ] **Step 2: JS 변경 후 preview 채널로 OTA 수동 발행**

눈에 보이는 사소한 JS 변경(예: 마이페이지 문구) 후:

```bash
cd apps/app && eas update --channel preview --message "OTA 검증"
```

- [ ] **Step 3: 자동 적용 확인**

폰에서 앱 완전 종료 → 재실행 → 잠시 후 자동 reload되며 변경이 보이는지 확인.
백그라운드로 보냈다가 복귀했을 때도 (30분 경과 시) 적용되는지 확인.
Expected: 사용자 조작 없이 변경 반영.

- [ ] **Step 4: 검증용 변경 되돌리고 Commit**

```bash
git checkout -- apps/app/src  # 검증용 문구 변경 폐기
```

---

## 운영 요약 (완성 후)

| 변경 종류 | 할 일 | 사용자 반영 |
|---|---|---|
| JS/이미지/스타일 | `main` 머지 | 실행/포그라운드 복귀 시 자동 적용 |
| 네이티브 변경 | CI 실패 알림 → `eas build` → 콘솔 업로드(또는 `eas submit`) | Play 자동 업데이트 |
