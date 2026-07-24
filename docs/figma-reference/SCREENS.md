# 벼리 화면 사양 (Figma 로컬 레퍼런스)

> Ralph 루프는 Figma MCP 대신 이 파일 + 같은 폴더의 PNG를 참조한다.
> 디자인 토큰: Primary `#5A6CF3`(인디고), 한복 강조 `#E5484D`(레드), 카드 라운드/소프트섀도, 폰트 Pretendard(데모는 시스템 폰트).
> 하단 5탭: 홈 / 지도 / 루트 / 검색 / 마이. BE 베이스 `http://localhost:8080/api/v1`.

## 구현 완료 (수정만)
- **홈** `app/(tabs)/index.tsx` — 검색바, 오늘의 추천 배너(performances), 맞춤 추천(venues 가로), 키워드 칩, 최근 콘텐츠
- **지도** `app/(tabs)/map.tsx` — 한복 토글, 카테고리 칩, 좌표 핀(빨강=한복할인), 미니카드
- **장소 상세** `app/venue/[id].tsx` — 히어로, 내 여행에 추가, 한복 혜택, 출처표기, 진행 행사, 코멘트 태그
- **루트 탭** `app/(tabs)/routes.tsx` + **코스 상세** `app/course/[id].tsx`
- **검색** `app/(tabs)/search.tsx`, **마이페이지** `app/(tabs)/profile.tsx`
- **로그인** `app/login.tsx`, **취향조사** `app/onboarding.tsx`(→ `09-interest-survey.png`), **즐겨찾기** `app/bookmarks.tsx`(→ `10-bookmarks.png`)

## 신규 구현 필요 (FE)
### 장소 등록 `07-venue-register.png`
폼 화면. 입력: 장소 이름*, 주소*, 카테고리*(select: 한복/메이크업/체험/카페/맛집/음식점/문화), 전화번호, 웹사이트, 운영시간, 사진 등록*(최대 5장 — 데모는 URL 입력 또는 placeholder), 장소 설명. 하단 버튼 "등록 신청 (관리자 승인 후 게시)". → `POST /venues` (source=USER, visibility=PUBLIC). 라우트 `app/venue/register.tsx`, 마이페이지 "내가 등록한 장소"/지도 "개인 등록"에서 진입.

### 여행 루트 편집 `06-itinerary-edit.png`
제목 입력(예: "제주 1박 2일 코스") + 저장 버튼, 달력(날짜 선택), 일자별("N일차 (날짜)") 일정 타임라인 — 각 항목: 장소명/주소/시간 + 삭제, 하단 "+" FAB로 항목 추가. → 여행일지 CRUD API. 라우트 `app/itinerary/[id].tsx` (+ 신규 생성).

### 마이페이지 하위
- **내 리뷰 목록** — 내가 쓴 리뷰 리스트. 라우트 `app/my/reviews.tsx`. → `GET /users/me/reviews`(데모: 인증 stub)
- **내가 등록한 장소** — source=USER 본인 장소 리스트. 라우트 `app/my/venues.tsx`.

### 리뷰 작성
장소 상세의 "코멘트 작성"에서 진입하는 별점+내용 작성 모달/화면. → `POST /reviews { targetType:'VENUE', targetId, rating, content }`.

## 신규 구현 필요 (BE) — ERD/명세 기준, by-feature
- **review**: `POST/PATCH/DELETE /reviews`, `GET /reviews?targetType=&targetId=`, `GET /users/me/reviews`. 작성/수정/삭제 시 대상 venue/performance의 `avg_rating`·`review_count` **동기 재계산**(ReviewService가 직접). 데모 인증은 헤더 `X-Demo-User-Id`(기본 2) 또는 고정 사용자.
- **wishlist**: `GET /users/me/wishlists`, `POST /wishlists`, `DELETE /wishlists` (targetType/targetId, 다형성 ContentType 값객체).
- **itinerary**: `GET /users/me/itineraries`, `POST/GET/PATCH/DELETE /itineraries`, 항목 `POST/PATCH/DELETE /itineraries/{id}/items`. CURATED 복사(sourceCourseId) 지원.
- **venue 등록/신고**: `POST/PATCH/DELETE /venues`(USER), `POST /venues/{id}/reports`.
- **content-tag**: `GET /content-tags?targetType=&targetId=`(집계), `POST/DELETE /content-tag-votes`.
- **공통**: 다형성은 DB 2컬럼 + API `targetType/targetId`, `global/content/ContentType` 값객체로 변환. 응답 봉투/페이지/에러 봉투 유지.

## 참고 문서
- `docs/byeori-tech-spec.md` — 명명규칙·파일구조·API 명세·인프라 (정본)
- `erd_final_1.txt` — 스키마 SSOT
