# 벼리 실 로그인 설계 (소셜 로그인 + JWT)

작성: 2026-06-17 · 상태: 승인 → 구현

## 목표
목업 로그인을 실제 소셜 로그인(카카오·구글)으로 교체. 사용자별 데이터(리뷰·찜·일정·등록 장소)를 인증된 사용자에 귀속.

## 결정 사항 (브레인스토밍 확정)
- 제공자: **카카오 + 구글**
- 방식: **expo-auth-session(브라우저 OAuth)** — Expo Go에서 동작. 추후 네이티브 SDK로 업그레이드(백엔드 재사용).
- Refresh 토큰: **(a) 무상태 서명 토큰**(MVP). 추후 DB저장으로 무효화 지원 가능.

## 흐름
```
앱(카카오/구글) → expo-auth-session OAuth
  → 카카오: 인가코드(code) / 구글: id_token
  → POST /api/v1/auth/social {provider, code|idToken, redirectUri}
  → 백엔드 제공자 검증·프로필 조회 → users 업서트 → 우리 JWT(access+refresh)
  → 앱 secure store 저장 → 요청에 Bearer 첨부, 401시 refresh
```

## 백엔드 (apps/api)
- **User 엔티티**: 기존 `users` 테이블 매핑(id,name,email,phone,auth_provider,role,phone_verified,email_verified,language,status,...).
  - **V5 마이그레이션**: `provider_user_id`(외부ID)·`profile_image_url` 추가, `(auth_provider, provider_user_id)` UNIQUE.
- **JWT**: jjwt(HS256). access 1h, refresh 14d(무상태). 시크릿 `JWT_SECRET`(env).
- **Spring Security**: stateless. 공개=GET 조회(venues/performances/courses/search/tags). 보호=사용자 액션(리뷰/찜/일정/장소등록 쓰기, users/me). JWT 필터가 userId를 SecurityContext에 주입.
- **소셜 검증**: KakaoClient(code→token→kapi user/me), GoogleClient(id_token 검증→profile). 키는 env(`KAKAO_REST_KEY`,`KAKAO_REDIRECT_URI`,`GOOGLE_CLIENT_ID`).
- **엔드포인트**: `POST /auth/social`, `POST /auth/token/refresh`, `GET /users/me`, `POST /auth/logout`(무상태=클라이언트 토큰 폐기).
- **기존 통합**: `@RequestHeader X-Demo-User-Id`(기본2) → 인증 사용자 id로 교체(ReviewController/WishlistController/ItineraryController/VenueController). 미인증 쓰기는 401.

## 앱 (apps/app)
- 설치: `expo-auth-session`, `expo-secure-store`, `expo-crypto`.
- `lib/store/authStore.ts`(zustand): {user, accessToken, refreshToken, isLoggedIn}. 토큰은 expo-secure-store 영속, 앱 시작 시 복원.
- `lib/auth/oauth.ts`: 카카오/구글 expo-auth-session 플로우 → 백엔드 /auth/social 호출.
- axios(client.ts) 인터셉터: Authorization Bearer 첨부, 401시 refresh→재시도, 실패시 로그아웃.
- 로그인 화면 버튼 실제 연동, 마이페이지 실제 사용자/로그아웃, 게이트(비로그인 쓰기→/login 유도).

## 외부 설정 (사용자 작업, 병행)
- 카카오: 개발자센터 동일 앱 → 카카오 로그인 ON + 동의항목(닉네임/이메일) + Redirect URI 등록 + REST 키 → `infra/.env`.
- 구글: Google Cloud OAuth 클라이언트 ID(Web/Android) → 앱·백엔드 설정.

## 범위 밖(향후)
- 네이티브 카카오 SDK(B), 애플 로그인, refresh DB저장/회전, 회원탈퇴 흐름 고도화.
