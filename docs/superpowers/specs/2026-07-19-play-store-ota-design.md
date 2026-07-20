# 설계: Google Play 출시 + 자동 OTA(EAS Update) 파이프라인

- 날짜: 2026-07-19
- 대상: `apps/app` (Expo SDK 56, React Native 0.81)
- 상태: 사용자 승인됨

## 목표

1. 벼리 앱을 Google Play Store에 출시한다 (Android, `com.byeori.app`).
2. JS/에셋 변경은 `main` 브랜치 push만으로 자동 OTA 배포되고, 사용자 앱에서 **자동으로 다운로드 후 즉시 적용**된다.
3. 네이티브 변경이 섞인 커밋은 CI가 감지해 "새 스토어 빌드 필요"를 알린다.

## 전제 (이미 갖춰진 것)

- EAS Update 도입 완료: `updates.url`, `runtimeVersion.policy = fingerprint`, 채널 development/preview/production (커밋 925f616)
- `appVersionSource: remote` + production `autoIncrement: true` → versionCode 자동 증가
- Google Play Console 개발자 계정 보유 (기존 등록)
- GitHub 레포: `tour-api-2026/byeori`, 기존 CI 없음

## 섹션 1 — 스토어 출시 준비 (1회 작업)

**코드 변경**: `eas.json` production 프로필 `android.buildType`을 `"apk"` → `"app-bundle"`.
Play Store는 AAB만 받는다. development/preview는 내부 설치용 APK 유지.

**수동 절차** (명령은 안내, 실행·콘솔 작업은 사용자):

1. `eas credentials`로 서명 키가 EAS 관리 키스토어인지 확인 (로컬 `@hidi_16__app.jks`는 백업으로 유지)
2. `eas build --profile production --platform android` → AAB 생성
3. Play Console에서 앱 생성 → **첫 AAB는 콘솔에 수동 업로드** (최초 1회는 `eas submit` 불가)
4. 스토어 등록정보: 스크린샷, 설명, 개인정보처리방침 URL(위치 권한 사용으로 필수), 데이터 보안 설문, 콘텐츠 등급
5. 내부 테스트 트랙 확인 후 프로덕션 승격

## 섹션 2 — `eas submit` 연결 (선택, 첫 출시 후)

네이티브 변경으로 재빌드 시 콘솔 수동 업로드를 없애기 위해:

- Google Cloud 서비스 계정 JSON 발급 → Play Console API 접근 권한 부여
- `eas.json`의 `submit.production.serviceAccountKeyPath`에 연결
- 이후 스토어 업데이트는 `eas build` → `eas submit -p android` 두 명령

이번 구현에서는 설정 틀만 잡고 서비스 계정 발급은 첫 출시 후로 미룬다.

## 섹션 3 — 인앱 자동 OTA 적용 (코드)

새 훅 `src/hooks/useAutoUpdate.ts` + `src/app/_layout.tsx`에서 1줄 호출.

동작:

- **콜드 스타트**: `Updates.checkForUpdateAsync()` → 있으면 `fetchUpdateAsync()` → `reloadAsync()` 즉시 적용
- **포그라운드 복귀**(AppState `active`): 동일 체크, 단 최소 간격 30분 스로틀
- 가드: `__DEV__` 또는 `Updates.isEnabled === false`(Expo Go/dev client)면 no-op
- 에러는 조용히 무시 — 업데이트 실패가 앱 사용을 막으면 안 됨. 다음 기회에 재시도

트레이드오프: 포그라운드 복귀 시 즉시 reload는 화면이 한 번 재시작된다. 조회 중심 앱이라 수용.
훅 내부에 적용 로직을 격리해 나중에 "안내 후 적용" UX로 바꾸기 쉽게 한다.

## 섹션 4 — CI: `main` push 시 자동 OTA (GitHub Actions)

`.github/workflows/ota-update.yml`:

- **트리거**: `main` push, `apps/app/**` 경로 변경 시에만
- **스텝**: checkout → Node 셋업 → `npm ci`(working-directory `apps/app`) → `expo/expo-github-action`으로 EAS CLI 인증 → `eas update --channel production --message "<커밋 메시지>" --non-interactive`
- **네이티브 변경 경고**: `eas fingerprint:compare`로 현재 커밋 fingerprint와 최신 production 빌드를 비교. 불일치 시 워크플로 실패 처리 — 이 OTA는 기존 설치자에게 도달하지 않으므로 "새 스토어 빌드 필요" 신호
- **사전 준비 1회**: expo.dev 액세스 토큰 발급 → GitHub 시크릿 `EXPO_TOKEN` 등록

주의: `eas.json`의 `EXPO_PUBLIC_*` env는 빌드 프로필용이므로, CI의 `eas update` 실행 시에도 동일 env를 주입해 번들에 올바른 API URL/카카오 키가 박히게 한다.

## 섹션 5 — 운영 플로우 (완성 후)

| 변경 종류 | 할 일 | 사용자 반영 |
|---|---|---|
| JS/이미지/스타일 | `main` 머지 | 다음 실행 또는 포그라운드 복귀 시 자동 적용 |
| 네이티브(라이브러리·권한·아이콘) | `eas build` → `eas submit`/콘솔 업로드 → Play 심사 | Play 자동 업데이트 |

fingerprint 정책 덕에 호환 안 되는 OTA가 구 빌드에 배달되는 사고는 구조적으로 차단된다.

## 에러 처리

- OTA 다운로드/적용 실패: expo-updates가 임베디드(또는 마지막 정상) 번들로 폴백. 훅은 에러를 삼키고 다음 체크에 재시도
- CI에서 update 발행 실패: 워크플로 실패로 표면화, 재실행으로 복구
- fingerprint 불일치: CI 실패로 알림 (위 섹션 4)

## 검증 계획

1. **OTA 훅**: preview 빌드(APK) 설치 → JS 변경 후 `eas update --channel preview` 수동 발행 → 콜드 스타트·포그라운드 복귀 각각에서 자동 reload 확인
2. **CI**: 워크플로 머지 후 사소한 JS 변경을 main에 push → Actions 로그에서 발행 확인 → production 빌드에서 수신 확인 (출시 후)
3. **fingerprint 경고**: 네이티브 영향 변경을 임시로 넣어 CI 실패 확인 후 되돌림

## 범위 밖

- iOS / App Store
- "안내 후 적용" UX (훅 구조만 대비)
- 스토어 빌드·제출의 완전 자동화 (수동 명령 유지)
