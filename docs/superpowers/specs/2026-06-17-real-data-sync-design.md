# 벼리 실데이터 동기화 설계 (TourAPI · KOPIS)

작성: 2026-06-17 · 상태: 골격 구현 단계(키 대기)

## 목표
시드/picsum 목데이터를 **실제 공공데이터**로 교체해 사용자에게 보여줄 카탈로그(장소·공연)를 채운다. 사용자 모으기 위한 콘텐츠 기반.

## 결정 사항 (브레인스토밍 확정)
- **접근 = A안(ETL): 외부 API → 우리 Postgres 적재**, 앱은 우리 DB만 읽음.
  - 근거: 리뷰·찜·여행일정·한복혜택·코스가 전부 우리 `venue id`로 조인됨. 속도·쿼터·장애내성·정규화.
- **소스**
  - 장소(관광지/문화시설/**음식점=카페·맛집**) = **TourAPI** "한국관광공사_국문 관광정보 서비스_GW" (data.go.kr `15101578`), 하나의 serviceKey.
    - 오퍼레이션: `areaBasedList2`(목록), `detailCommon2`(이미지·개요), `detailIntro2`(운영시간 등). `contentTypeId`: 12 관광지, 14 문화시설, 39 음식점, 15 축제공연행사.
  - 공연/행사 = **KOPIS** OpenAPI (kopis.or.kr) — 공연목록/상세.
  - **카카오 Local = "내 주변" 실시간 검색 전용(저장 X, 약관상 캐싱 제한)**. 적재 소스로 쓰지 않음.
- **한복 혜택 / 전통찻집 특화** = 외부 소스 없음 → **수동 큐레이션**(시드/관리자 지정 플래그). 추천 코스·리뷰도 앱 자체.
- **커버리지** = 주요 관광도시(서울·경주·전주·부산·제주 등) 우선 → 확장.
- **출처 표기 의무** 유지(TourAPI/KOPIS) — 앱에 이미 표시 중.

## 데이터 모델 (기존 재사용)
- `Venue`: 이미 `source`(TOURAPI/KOPIS/USER/MANUAL), `tourContentId`, `kopisId`, `syncedAt`, 표시필드 보유. **업서트 키 = `tourContentId`**.
- `Performance`: 동일하게 `kopisId`, `tourContentId`, `syncedAt` 보유. **업서트 키 = `kopisId`**.
- 신규 테이블 없음(골격 단계). `sync_logs`는 향후 확장.

## 컴포넌트 (백엔드 `apps/api`)
- `global/external/SyncProperties` — 환경변수 `TOURAPI_KEY`, `KOPIS_KEY`, base URL, area 목록.
- `global/external/TourApiClient` — RestClient로 areaBasedList/detail 호출, DTO 파싱.
- `global/external/KopisClient` — RestClient로 공연목록/상세 호출(XML).
- `domain/sync/VenueSyncMapper` / `PerformanceSyncMapper` — 외부 DTO → 엔티티 매핑(카테고리/좌표/이미지/출처).
- `domain/sync/SyncService` — 지역×contentType 순회 → 업서트(tourContentId/kopisId 기준). KOPIS 공연 업서트.
- `domain/sync/SyncScheduler` — `@Scheduled` 일 1회(키 없으면 skip).
- `domain/sync/SyncController` — `POST /api/v1/admin/sync/trigger` 수동 백필(인증 도입 전까지 임시 개방, 추후 ROLE_ADMIN 가드).
- `ApiApplication` — `@EnableScheduling`.

## 카테고리 매핑(초안, 실제 응답 보며 보정)
- contentTypeId 12 관광지 → `문화`, 14 문화시설 → `문화`, 28 레포츠 → `체험`, 39 음식점 → `맛집`(cat3가 카페/찻집 A05020900이면 `카페`).
- KOPIS 장르 → 그대로 또는 `공연`.

## 동기화 흐름
1. (백필/스케줄) 각 areaCode + contentTypeId로 `areaBasedList2` 페이지 순회
2. 항목별 `detailCommon2`/`detailIntro2`로 이미지·운영시간 보강(선택)
3. `tourContentId`로 기존 venue 조회 → 있으면 update, 없으면 insert, `syncedAt` 갱신
4. KOPIS 공연목록 → `kopisId` 업서트
5. 결과 로그(SLF4J)

## 비기능 / 운영
- 키는 **백엔드 환경변수**(컨테이너 `byeori-api` env). 앱(.env)엔 넣지 않음.
- 레이트리밋 대비: 호출 간 throttle, 페이지 크기 제한.
- 키 미설정 시 동기화 skip(앱은 기존 데이터로 계속 동작).

## 범위 밖(향후)
- `sync_logs` 테이블 + 관리자 대시보드, 동기화 통계
- 관리자 인증(ROLE_ADMIN) — 실로그인 작업에서 같이
- 이미지 없는 항목 보강/중복 제거 고도화
