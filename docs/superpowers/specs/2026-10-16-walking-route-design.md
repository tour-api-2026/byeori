# 도보 경로 설계

루트를 지도에 그릴 때 직선 대신 **실제 걷는 길**을 보여준다.

## 왜

지금 지도는 방문지를 직선으로 잇는다. 그런데 서버는 이미 카카오모빌리티로 도로 경로를
계산해 `path` 로 내려보내고 있고, 지도 코드가 그걸 **받아놓고 버린다.**

```js
// apps/app/src/lib/map/kakaoMapSource.ts
// 도로 경로(data.path)는 쓰지 않고, 장소(stops)를 방문 순서대로 직선으로 연결한다.
```

그러나 그 `path` 를 그대로 그려도 절반만 맞는다. **카카오모빌리티는 자동차 경로만 준다.**
벼리의 루트는 고궁에서 고궁으로 걷는 동선이 본체인데, 차 경로를 그리면 길도 시간도 틀린다.

| 구간 | 걸어서 | 차로 |
|---|---|---|
| 인사동 → 조계사 | **462m · 5.5분** | 790m · 4.4분 |
| 수원화성 장안문 → 팔달문 | **1,608m · 19.3분** | 1,774m · 5.0분 |
| 경복궁 → 종묘 | **2,749m · 33.0분** | 2,617m · 6.7분 |

인사동에서 조계사는 걸어서 462m인데 차로는 일방통행을 돌아 790m다. 그리고 화성 성곽길을
지금 앱은 "5분"이라고 안내한다 — 실제로 걸으면 19분이다.

**카카오는 보행자 길찾기 API를 공개하지 않는다.** 그래서 도보 경로는 다른 데서 와야 한다.

## 무엇을 쓰는가 — OSRM 자체 호스팅

OpenStreetMap 데이터로 경로를 계산하는 OSRM을 컨테이너로 띄운다. 검토한 대안들:

| | 도보 경로 | 비용 | 판단 |
|---|---|---|---|
| 카카오모빌리티 (지금) | ❌ 자동차만 | 쿼터 | 시간이 5배 틀림 |
| OSRM 공개 데모 서버 | ❌ **자동차만** | 무료·불안정 | 아래 참고 |
| **OSRM 자체 호스팅** | ✅ | 메모리 9MB | **채택** |

데모 서버(`router.project-osrm.org`)는 URL의 프로파일을 무시한다. `foot`·`walking`·
`driving`·`bike` 네 가지가 글자 하나까지 같은 답을 준다 — 자동차 프로파일 하나만 올려둔
것이다. 경복궁→종묘를 평균 30.5km/h로 계산한다. 쓸 수 없다.

osmnx(파이썬)도 검토했으나 상시 서비스용이 아니다. 서울 하나만으로 NetworkX 그래프가
1.5~3GB를 차지하고, 전국은 이 기기의 10GB 한도 안에서 불가능하다. 같은 OSM 데이터를
쓰되 OSRM이 맞다.

## 실측 — 스파이크에서 잰 값

전처리(2026-10-15, `south-korea-latest.osm.pbf` 276MB):

| 단계 | 최대 메모리 | 비고 |
|---|---|---|
| `osrm-extract -p foot.lua` | **5,474MB** | **스왑 필수** |
| `osrm-partition` | 2,399MB | |
| `osrm-customize` | 1,157MB | |
| 산출물 | | 2.9GB |

extract 는 4GB·5GB 한도에서 두 번 OOM 으로 죽었다. `--memory=5500m --memory-swap=8g`
로 통과했다. 전체 7~8분.

구동(`osrm-routed`):

| 설정 | 상시 메모리 | 기동 | 응답 |
|---|---|---|---|
| 기본 | 538.7MB (수도권 기준) | 0.90초 | 2.9ms |
| **`--mmap 1`** | **4.7MB** | **0.66초** | **2.2ms** |
| 전국 + `--mmap 1` | **11.6MB** (질의 후 39.9MB) | 0.79초 | 5~49ms |

`--mmap 1` 이 핵심이다. 파일을 그대로 매핑해 읽은 페이지만 올라오고, 그것도 커널이
회수할 수 있는 페이지 캐시다. 없으면 전국 기준 1.8GB가 상주한다.

동시성은 문제가 아니다. 수도권 기준 **동시 500건 성공 500/500, 중앙값 25.7ms,
초당 1,301건**. 루트 생성이 하루 0.33건인 서비스에서 한 대로 충분하다.

전국 8개 지역(서울·전주·경주·부산·안동·제주·강릉·수원) 전부 4.7~5.0km/h 로 정상
계산된다.

## 설계

### ① 인프라

```yaml
  osrm:
    image: ghcr.io/project-osrm/osrm-backend
    container_name: byeori-osrm
    restart: unless-stopped
    command: >
      osrm-routed --algorithm mld --mmap 1 --threads 4
      /data/south-korea-latest.osrm --disable-feature-dataset=ROUTE_STEPS
    volumes:
      - /home/hidi/osrm-data:/data
    mem_limit: 512m
    networks: [default]
```

포트를 호스트에 열지 않는다. API가 도커 내부망에서 `http://osrm:5000` 으로만 부른다 —
Postgres 와 같은 원칙이다(외부는 nginx 443 만).

**인자 순서에 주의.** `--mmap` 과 `--disable-feature-dataset` 둘 다 값을 받는 옵션이라,
경로를 맨 뒤에 두면 그것을 값으로 먹고 죽는다. 경로를 옵션 사이에 둔다.

`--disable-feature-dataset=ROUTE_STEPS` 는 턴바이턴 안내 데이터를 올리지 않는다.
우리는 선만 그리므로 필요 없다.

**데이터는 도커 볼륨이 아니라 바인드 마운트로 둔다.** 이 환경의 도커 볼륨은 윈도우
C 드라이브의 가상 디스크(`docker_data.vhdx`) 안에 쌓이는데, 그 파일은 한 번 커지면
자동으로 줄지 않는다. 바인드 마운트는 WSL 디스크(여유 818GB)에 두고 셸로 직접 확인·교체할
수 있다. 어차피 전처리가 이 기기에서 도는 이상 경로가 묶이는 것은 같다.

### ② 전처리 — `scripts/build-osrm.sh`

```
pbf 내려받기 → extract(foot) → partition → customize → /home/hidi/osrm-data
```

OSM 데이터는 매일 갱신되므로 분기에 한 번쯤 다시 돌린다. **돌리는 동안 Gradle·웹 빌드를
띄우면 안 된다** — extract 가 5.5GB를 쓰고, 이 기기는 전에 커널 OOM 으로 꺼진 적이 있다.
스크립트에 그 경고와 `--memory` 한도를 박아 둔다. 한도를 거는 이유는 터졌을 때 커널이
postgres 나 api 를 고르지 않게 하려는 것이다(실제로 스파이크에서 두 번 터졌고, 운영
서비스는 멀쩡했다).

### ③ 이동 수단 판정

구간마다 **직선거리 1km** 를 기준으로 가른다.

```
직선거리 ≤ 1km  →  OSRM 도보
직선거리 >  1km  →  카카오 자동차 (이미 있는 코드)
```

실제 루트 53개 구간의 분포로 정한 값이다.

| 거리 | 구간 수 | 1km 기준 |
|---|---|---|
| 500m 이내 | 16 (30%) | 걷기 |
| 500m ~ 1km | 19 (36%) | 걷기 |
| 1 ~ 3km | 16 (30%) | 차 |
| 3km 초과 | 2 (4%) | 차 |

중앙값이 706m 다. 500m 로 끊으면 "경주 첨성대 → 대릉원", "북촌한옥마을 → 창덕궁"처럼
관광객이 당연히 걷는 길이 차로 넘어간다. 1km 면 그것들이 들어오고, 부산 감천문화마을→
자갈치(6km, 걸으면 73분) 같은 구간은 차로 간다.

기준값은 설정으로 뺀다(`byeori.route.walk-threshold-m`). 빌드 없이 바꿔 볼 수 있어야 한다.

**자동차 경로는 카카오를 그대로 쓴다.** 국내 도로 사정(일방통행·실시간)에 더 강하고 이미
작동한다. OSRM 에 car 프로파일을 더 얹으면 2.9GB 와 전처리 7분이 또 든다. OSRM 은 도보만
담당한다.

### ④ 사용자 선택

기본값이 늘 맞을 수는 없다. 비가 오면 다 타고 싶고, 날이 좋으면 다 걷고 싶다.

```
자동(기본)   가까우면 걷기, 멀면 차
걷기         전부 도보
차           전부 차량
```

지도 위 칩으로 바꾼다. 지도에는 **도보 구간과 차량 구간을 다른 선으로** 그려, 어디를
걷고 어디를 타는지 한눈에 보이게 한다.

### ⑤ 계산 결과를 저장한다

지금은 루트를 **열 때마다** 카카오를 부르고 최대 1초를 기다린다. 경로는 루트를 고치기
전까지 변하지 않으므로 저장해 두고 다시 쓴다.

```sql
create table itinerary_routes (
    itinerary_id bigint not null references itineraries(id) on delete cascade,
    mode         varchar(10) not null,       -- AUTO | WALK | DRIVE
    stops_hash   varchar(64) not null,       -- 방문지·순서로 만든 지문
    distance_m   int    not null,
    duration_s   int    not null,
    geometry     text   not null,            -- polyline6
    legs         jsonb  not null,            -- 구간별 거리·시간·이동수단
    computed_at  timestamp not null,
    primary key (itinerary_id, mode)
);
```

`stops_hash` 가 자동 무효화 장치다. 요청이 오면 지금 방문지로 해시를 만들어 저장된 것과
비교한다 — 다르면 다시 계산하고, 같으면 그대로 돌려준다. **루트를 고치면 해시가 달라지므로
갱신을 잊을 수 없다.** 모드를 키에 넣는 이유는 모드마다 답이 다르기 때문이다. 한 번 계산한
모드는 다시 눌러도 즉시 나온다.

`geometry` 는 `polyline6` 으로 받는다. 좌표 배열보다 작고 지도 쪽에서 풀기 쉽다.

### ⑥ API

```
GET /api/v1/itineraries/{id}/route?mode=AUTO|WALK|DRIVE
```

지금 있는 `priority` 와 같은 자리에 붙는다. 기본값 `AUTO`.

### ⑦ 지도

`drawRoute` 가 `data.path` 를 실제로 그린다. 구간별 이동 수단에 따라 선을 달리한다.

## 손대는 곳

```
infra/docker-compose.yml                    osrm 서비스
scripts/build-osrm.sh                       전처리 (신규)
apps/api  global/external/OsrmClient.java   신규
          domain/itinerary/ItineraryService 구간 판정 + 저장/재사용
          db/migration/V27__itinerary_routes.sql
apps/app  src/lib/map/kakaoMapSource.ts     path 그리기
          루트 화면                          모드 칩
```

## 어떻게 확인하는가

| | |
|---|---|
| 구간 판정 | 1km 경계를 넘나드는 좌표로 WALK/DRIVE 가 갈리는지 |
| 해시 무효화 | 방문지를 바꾸면 다시 계산되고, 안 바꾸면 저장된 것을 쓰는지 |
| 모드별 저장 | AUTO 계산 후 WALK 를 부르면 따로 계산되고, 다시 AUTO 면 재사용되는지 |
| OSRM 장애 | 컨테이너가 죽어 있을 때 **루트 화면이 실패하지 않고** 카카오로 넘어가는지 |
| 전국 | 서울·전주·경주·부산·제주 좌표로 경로가 나오는지 |

마지막 두 줄이 중요하다. OSRM 이 죽어도 루트는 보여야 한다 — 도보가 안 되면 기존 카카오
경로로, 그것도 안 되면 직선으로 내려간다. 지금보다 나빠지는 경우가 없어야 한다.

## 구현하며 틀린 것으로 드러난 전제

위 §③ 과 계획서는 "제주↔육지처럼 걸어서 갈 수 없는 구간은 OSRM 이 `code != "Ok"` 로
알려 준다"고 적었다. **이 데이터에서는 거짓이다.** 전국 단일 그래프에 물어보면:

    제주 성산 → 서울 경복궁    code=Ok  586km  82시간
    울릉도 좌표 → 서울         code=Ok  404km  50시간

OSRM 은 가장 가까운 도로에 좌표를 붙인 뒤(snapping) 육지를 돌아가는 길을 지어낸다.
`NoRoute`·`NoSegment` 는 실제로는 거의 오지 않고, 관측된 400 응답은 전부 우리가 잘못
보낸 경우(`InvalidValue`, `InvalidOptions`)였다.

그래서 "갈 수 없는 구간"을 막는 것은 OSRM 의 응답이 아니라 **거리 상한 두 개**다 —
AUTO 의 1km 기준과, 명시적 WALK 의 50km 상한(`byeori.route.walk-max-m`). 상한이
없었다면 사용자에게 "82시간 걸어가세요"가 표시됐을 것이다.

`OsrmClient` 가 NO_ROUTE 와 FAILED 를 가르는 것은 그대로 값을 한다 — 드물게라도
그 응답이 오면 그 구간만 차로 넘기고 나머지 구간의 도보를 잃지 않는다.

## 남겨 두는 것

- **`/table/v1/foot/`** — 장소 간 소요시간 행렬. "가장 덜 걷는 순서"로 방문 순서를 자동
  정렬하는 데 쓸 수 있다. 카카오로는 못 하던 것이지만 이번 범위는 아니다.
- **car 프로파일 자체 호스팅** — 카카오 쿼터를 완전히 없앨 수 있으나, 국내 도로는 카카오가
  낫다고 보아 미룬다.
