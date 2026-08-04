# 전통 테마 행사 확충 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기존 수집 데이터(KOPIS/TourAPI)에 전통 테마 태깅(A)을 하고, 서울 열린데이터 문화행사 API에서 전통 분류 행사를 추가 수집(B)한다.

**Architecture:** (A) `performances.traditional boolean` 컬럼 + 제목/장르 기반 `TraditionalTagger`를 sync 시 적용, 기존 행은 Flyway 백필. API에 `traditional` 필터 노출. (B) `SeoulEventClient`(culturalEventInfo)를 신설해 CODENAME이 전통 계열(`국악`, `축제-전통/역사`)인 행사만 `source=SEOUL`로 Performance에 upsert.

**Tech Stack:** Spring Boot 3 / JPA / Flyway / RestClient

## Global Constraints

- 스키마는 Flyway 전용 (`ddl-auto: none`) — 다음 버전 번호는 V9부터
- 전통 판정 규칙: KOPIS `genre == "국악"` → true, 그 외 제목 키워드(전통·국악·한옥·고궁·궁궐·문화재·민속·판소리·사물놀이·풍물·농악·탈춤·한복·궁중·무형유산·향교·서원·단오·세시풍속) 포함 → true
- 서울 API: `http://openapi.seoul.go.kr:8088/{KEY}/json/culturalEventInfo/{start}/{end}` — 키는 env `SEOUL_OPEN_API_KEY`, 미설정 시 skip (TOURAPI_KEY 패턴과 동일)
- 서울 수집 대상 CODENAME: `국악`, `축제-전통/역사` (traditional=true 고정)
- 검증: `./gradlew test` + 운영 컨테이너 재배포 후 `/api/v1/performances?traditional=true` 실측

### Task A1: traditional 컬럼 + 태거 + 백필
- [ ] `V9__performance_traditional.sql`: 컬럼 추가(boolean not null default false) + genre='국악'/제목 키워드 LIKE 백필
- [ ] `TraditionalTaggerTest` 작성 (국악 장르, 키워드 제목, 비전통 케이스) → 실패 확인
- [ ] `sync/TraditionalTagger.java` 구현 → 테스트 통과
- [ ] `Performance`에 `traditional` 필드 + `applyTraditional(boolean)`; `SyncService.upsertPerformance/upsertFestival`에서 적용

### Task A2: API 필터 노출
- [ ] `PerformanceRepository.search`에 `traditional` 파라미터 추가 (`:traditional is null or p.traditional = :traditional`)
- [ ] `PerformanceService.list` / `PerformanceController`에 `traditional` Boolean 파라미터
- [ ] `PerformanceResponse`에 `traditional` 필드 추가
- [ ] `./gradlew test` 통과 + 커밋

### Task B1: 서울 문화행사 수집
- [ ] `V10__performance_seoul_id.sql`: `seoul_id` 컬럼(+unique) 추가
- [ ] `global/external/SeoulEventClient.java`: culturalEventInfo 페이지 수집(JSON), CODENAME/TITLE/PLACE/STRTDATE/END_DATE/MAIN_IMG/ORG_LINK/LAT/LOT 파싱
- [ ] `Performance.fromSeoul/updateFromSeoul` + `findBySeoulId`
- [ ] `SyncService.syncSeoulEvents()`: CODENAME 전통 계열만 upsert(traditional=true, genre=CODENAME), `SyncController`/`SyncScheduler` 연결
- [ ] `SyncProperties`에 seoul key 지원, infra/.env에 `SEOUL_OPEN_API_KEY` 주입

### Task B2: 배포·검증
- [ ] API 재빌드·재기동(Flyway 마이그레이션 적용) → admin sync 트리거 → `traditional=true` 건수 확인
- [ ] PR 생성·머지
