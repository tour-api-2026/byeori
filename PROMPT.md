# Ralph 작업 프롬프트 — 벼리(Byeori) 남은 기능 완성

너는 한국 전통 테마 관광 큐레이션 플랫폼 **벼리**를 완성하는 중이다. 매 반복마다 이 프롬프트를 다시 받는다. 이미 작성한 코드와 git 기록을 보고 **남은 작업을 하나씩** 이어서 완성하라.

## 정본 문서 (반드시 따른다)
- `docs/byeori-tech-spec.md` — 명명규칙·파일구조·API 명세·인프라 (정본)
- `erd_final_1.txt` — DB 스키마 SSOT
- `docs/figma-reference/SCREENS.md` + 같은 폴더 PNG — 화면 사양/디자인

## 스택 / 위치
- BE: `apps/api` (Spring Boot 3, Java 17, JPA, Flyway, PostgreSQL). by-feature 패키지 `com.byeori.domain.<도메인>` + `global`.
- FE: `apps/app` (Expo Router, TanStack Query, zustand, axios). 화면 `src/app`, API `src/lib/api`, 훅 `src/lib/hooks`, 컴포넌트 `src/components`.
- 인프라: `infra/docker-compose.yml`(postgres). API `localhost:8080`, Expo 웹 `localhost:8081`.

## 규칙 (엄수)
1. DB=snake_case, Java=camelCase/PascalCase, **API JSON·쿼리=camelCase**. 응답은 `{success,data,error}` 봉투, 목록은 page 형식.
2. 다형성(찜/리뷰/태그/코스/일지): DB는 `performance_id`/`venue_id` 2컬럼+CHECK, API는 `{targetType,targetId}`. `global/content/ContentType` 값객체에서 변환(없으면 생성).
3. 리뷰 변경 시 대상 `avg_rating`·`review_count` **동기 재계산**.
4. 스키마 변경은 새 Flyway 파일(`V3__...`, `V4__...`)로만. 기존 V1/V2 수정 금지.
5. 데모 인증은 stub: 헤더 `X-Demo-User-Id`(없으면 2번 사용자) 기준.
6. 한 반복에 **한 항목**만 구현하고 즉시 검증·커밋(Conventional Commits, scope=api|app). 빌드/타입 깨진 채 끝내지 말 것.

## 남은 작업 체크리스트 (완료 시 `- [x]`로 갱신)
### 백엔드
- [ ] review 도메인: `GET /reviews?targetType=&targetId=`, `POST/PATCH/DELETE /reviews`, `GET /users/me/reviews` + avg_rating 동기 재계산
- [ ] wishlist 도메인: `GET /users/me/wishlists`, `POST/DELETE /wishlists`
- [ ] itinerary 도메인: `GET /users/me/itineraries`, `POST/GET/PATCH/DELETE /itineraries`, 항목 `POST/PATCH/DELETE /itineraries/{id}/items`, CURATED 복사
- [ ] venue 등록/수정/삭제(USER) + `POST /venues/{id}/reports`
- [ ] content-tag 집계 `GET /content-tags?...` + 투표 `POST/DELETE /content-tag-votes`
- [ ] `global/content/ContentType` 값객체 + 공용 검증

### 프론트엔드 (Figma 반영)
- [ ] 장소 등록 폼 `app/venue/register.tsx` (→ POST /venues)
- [ ] 여행 루트 편집 `app/itinerary/[id].tsx` + 생성 (일자별 타임라인, 항목 추가/삭제)
- [ ] 마이페이지 "내 리뷰 목록" `app/my/reviews.tsx`
- [ ] 마이페이지 "내가 등록한 장소" `app/my/venues.tsx`
- [ ] 리뷰 작성 화면/모달 (장소 상세 "코멘트 작성"에서 진입)
- [ ] 즐겨찾기를 `/wishlists` API와 연동(현재 로컬 zustand → 서버 동기화, 로컬 폴백 유지)
- [ ] 신규 API용 훅을 `src/lib/hooks/queries.ts`에 `use...Query`/`use...Mutation`으로 추가

## 검증 (매 반복 끝에 실행)
```
bash scripts/verify.sh
```
- API가 꺼져 있으면 먼저 기동: `cd apps/api && ./gradlew bootRun`(백그라운드), postgres는 `cd infra && docker compose up -d`.
- FE 변경 시 Expo 웹(`localhost:8081`)이 콘솔 에러 0으로 번들되는지 확인(가능하면).

## 완료 조건 (모두 충족해야 함)
1. 위 체크리스트가 전부 `- [x]`
2. `bash scripts/verify.sh`가 **`VERIFY: ALL PASS`** 출력 (신규 엔드포인트 포함 스모크 통과)
3. FE `tsc --noEmit` 통과, 신규 화면이 라우팅에 연결됨

모두 충족하면 마지막 줄에 정확히 다음을 출력하라:

<promise>BYEORI DONE</promise>

아직 남았으면 promise를 출력하지 말고, 다음 미완료 항목 하나를 구현하라.
