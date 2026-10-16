# 도보 경로 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 루트를 지도에 그릴 때 가까운 구간은 실제 걷는 길로, 먼 구간은 자동차 길로 보여준다.

**Architecture:** OSRM(도보)을 도커 컨테이너로 띄우고, 구간마다 직선거리 1km 를 기준으로
OSRM 과 기존 카카오모빌리티(자동차)를 갈라 쓴다. 계산 결과는 `(루트, 모드)` 키로 저장해
두 번째부터는 외부 호출 없이 돌려준다. OSRM 이 죽어도 카카오로, 카카오도 안 되면 직선으로
내려가 루트 화면은 절대 실패하지 않는다.

**Tech Stack:** Spring Boot 3 / JPA / Flyway, PostgreSQL 16, OSRM(ghcr.io/project-osrm/osrm-backend),
Expo SDK 54 + expo-router, 카카오맵 JS SDK(WebView)

**Spec:** `docs/superpowers/specs/2026-10-16-walking-route-design.md`

## Global Constraints

- 도보 판정 기준은 **직선거리 1,000m**. 설정 키 `byeori.route.walk-threshold-m`, 기본값 `1000`.
- OSRM 은 **도보 전용**. 자동차 경로는 기존 `KakaoMobilityClient` 를 그대로 쓴다.
- OSRM 컨테이너는 **호스트 포트를 열지 않는다.** API 가 내부망 `http://osrm:5000` 으로만 부른다.
- `osrm-routed` 인자 순서: `--mmap` 과 `--disable-feature-dataset` 은 값을 받는 옵션이라
  **데이터 경로를 맨 뒤에 두면 그것을 값으로 먹고 죽는다.** 경로를 옵션 사이에 둔다.
- `infra/docker-compose.yml` 프로젝트명은 `infra` 로 seoulRide 스택과 공유한다.
  **`--remove-orphans` 를 절대 쓰지 않는다.**
- 앱에 **npm 패키지를 추가하지 않는다.** `package.json` 이 바뀌면 fingerprint 가 달라져
  OTA 가 이미 설치된 앱에 닿지 못한다. (그래서 앱 쪽 검증은 `npx tsc --noEmit` + 수동이다.)
- 좌표 순서에 주의: OSRM URL 은 `경도,위도`, 응답 geojson 도 `[경도, 위도]`.
  우리 `RouteResponse.path` 는 `[위도, 경도]` 다.

## Review Focus

1. **OSRM 컨테이너가 죽어 있을 때** — 루트 화면이 실패하지 않고 카카오 자동차 경로로
   내려가야 한다. 지금보다 나빠지는 경우가 없어야 한다. (Task 4)
2. **두 공급자가 모두 실패할 때** — 그래도 직선과 직선거리 합계라도 돌려준다.
   사용자는 "경로를 불러오지 못했어요" 대신 최소한 방문 순서를 본다. (Task 4)
3. **OSRM 이 경로를 못 찾는 구간** — 제주↔육지처럼 걸어서 갈 수 없는 구간은
   `code != "Ok"` 로 온다. 그 구간만 자동차로 넘긴다. 전체가 실패하면 안 된다. (Task 4)
4. **같은 루트를 동시에 두 번 요청** — 저장이 유니크 제약에 걸려 500 이 나면 안 된다.
   나중 것이 앞의 것을 덮어쓰면 된다(같은 입력이면 같은 결과다). (Task 3)
5. **`mode` 에 모르는 값이 올 때** — `?mode=hello` 는 400 이 아니라 `AUTO` 로 본다.
   경로 보기는 읽기 동작이고, 오타로 화면이 깨지는 것이 더 나쁘다. (Task 5)

---

### Task 1: OSRM 컨테이너를 인프라에 올린다

전처리 산출물은 이미 `/home/hidi/osrm-data` 에 있다(3.2GB, 스파이크에서 생성).
이 태스크는 그것을 compose 로 정식 등록하고, 다시 만들 수 있는 스크립트를 남긴다.

**Files:**
- Modify: `infra/docker-compose.yml`
- Create: `scripts/build-osrm.sh`

**Interfaces:**
- Produces: 내부망 `http://osrm:5000` 에서 OSRM HTTP API. 호스트에는 열지 않는다.

- [ ] **Step 1: compose 에 서비스 추가**

`infra/docker-compose.yml` 의 `api` 서비스 아래, `volumes:` 선언 위에 넣는다.

```yaml
  # 도보 경로 계산(OSRM). 카카오모빌리티는 자동차 경로만 주므로 걷는 길은 여기서 온다.
  # 포트를 호스트에 열지 않는다 — postgres 와 같은 원칙(외부는 nginx 443만).
  osrm:
    image: ghcr.io/project-osrm/osrm-backend
    container_name: byeori-osrm
    restart: unless-stopped
    # 인자 순서 주의: --mmap 과 --disable-feature-dataset 은 값을 받는 옵션이라
    # 데이터 경로를 맨 뒤에 두면 그것을 값으로 먹고 죽는다.
    # --mmap 1 이 핵심 — 없으면 전국 데이터가 1.8GB 상주한다(있으면 11.6MB).
    command: >
      osrm-routed --algorithm mld --mmap 1 --threads 4
      /data/south-korea-latest.osrm --disable-feature-dataset=ROUTE_STEPS
    volumes:
      - /home/hidi/osrm-data:/data
    mem_limit: 512m
    networks:
      - default
```

- [ ] **Step 2: 띄워서 확인**

```bash
cd infra && docker compose up -d osrm
docker compose exec api sh -c 'wget -qO- "http://osrm:5000/route/v1/foot/126.9770,37.5796;126.9947,37.5745?overview=false"' | head -c 200
```

기대: `{"code":"Ok","routes":[{...,"duration":1980...` (경복궁→종묘, 약 33분)
`--remove-orphans` 를 쓰지 않았는지 확인한다.

- [ ] **Step 3: 전처리 스크립트 작성**

`scripts/build-osrm.sh`:

```bash
#!/usr/bin/env bash
# 도보 경로용 OSRM 데이터를 만든다. OSM 은 매일 갱신되므로 분기에 한 번쯤 돌린다.
#
# ⚠️ 돌리는 동안 Gradle·웹 빌드·로컬 API 를 띄우지 말 것.
#    extract 가 5.5GB 를 쓴다. 이 기기는 메모리가 바닥나면 커널이 아무 프로세스나
#    죽이고(전에 java 가 당했다), 그러면 postgres 나 운영 API 가 간다.
#    --memory 한도를 거는 이유가 그것이다 — 터져도 이 컨테이너만 죽는다.
set -euo pipefail
DIR="${1:-/home/hidi/osrm-data}"
IMG=ghcr.io/project-osrm/osrm-backend
PBF=south-korea-latest.osm.pbf
mkdir -p "$DIR"

echo "① 내려받기 (약 276MB)"
curl -sL -o "$DIR/$PBF" https://download.geofabrik.de/asia/south-korea-latest.osm.pbf

run() {                       # 한 단계를 한도 안에서 돌린다
  echo "② $1"
  docker run --rm --memory=5500m --memory-swap=8g -v "$DIR:/data" "$IMG" "$@"
}
# extract 는 실측 5,474MB 를 쓴다. 4GB·5GB 한도에서는 OOM 으로 죽었고,
# 스왑을 열어 주어야 통과한다.
run osrm-extract -p /opt/foot.lua "/data/$PBF"
run osrm-partition "/data/south-korea-latest.osrm"
run osrm-customize "/data/south-korea-latest.osrm"

echo "완료: $(du -sh "$DIR" | cut -f1)"
echo "컨테이너 재기동: cd infra && docker compose up -d --force-recreate osrm"
```

- [ ] **Step 4: 실행 권한과 문법 확인**

```bash
chmod +x scripts/build-osrm.sh && bash -n scripts/build-osrm.sh && echo "문법 OK"
```

데이터가 이미 있으므로 이 태스크에서 **스크립트를 실제로 돌리지는 않는다**(7~8분 + 5.5GB).

- [ ] **Step 5: 커밋**

```bash
git add infra/docker-compose.yml scripts/build-osrm.sh
git commit -m "feat(infra): 도보 경로용 OSRM 컨테이너를 올린다"
```

---

### Task 2: 구간별 이동 수단을 정하는 규칙

순수 계산이라 인프라 없이 테스트된다.

**Files:**
- Create: `apps/api/src/main/java/com/byeori/domain/itinerary/RouteModePlanner.java`
- Create: `apps/api/src/test/java/com/byeori/domain/itinerary/RouteModePlannerTest.java`

**Interfaces:**
- Produces:
  - `RouteModePlanner.distanceMeters(double lat1, double lng1, double lat2, double lng2) -> double`
  - `RouteModePlanner.legMode(String requested, double meters, int thresholdM) -> String`
    반환은 `"WALK"` 또는 `"DRIVE"`. `requested` 는 `"AUTO"|"WALK"|"DRIVE"`.

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`RouteModePlannerTest.java`:

```java
package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구간마다 걸을지 탈지 정한다.
 *
 * 기준을 1km 로 잡은 근거: 실제 루트 53개 구간의 중앙값이 706m 다. 500m 로 끊으면
 * "경주 첨성대 → 대릉원"(약 800m)처럼 관광객이 당연히 걷는 길이 차로 넘어간다.
 */
class RouteModePlannerTest {

    @Test
    @DisplayName("기준보다 가까우면 걷고, 멀면 탄다")
    void 자동_판정() {
        assertThat(RouteModePlanner.legMode("AUTO", 400, 1000)).isEqualTo("WALK");
        assertThat(RouteModePlanner.legMode("AUTO", 1500, 1000)).isEqualTo("DRIVE");
    }

    @Test
    @DisplayName("기준값 그 자체는 걷는 쪽에 넣는다 — 경계에서 차를 권할 이유가 없다")
    void 경계() {
        assertThat(RouteModePlanner.legMode("AUTO", 1000, 1000)).isEqualTo("WALK");
        assertThat(RouteModePlanner.legMode("AUTO", 1001, 1000)).isEqualTo("DRIVE");
    }

    @Test
    @DisplayName("사용자가 고르면 거리와 상관없이 그대로 따른다")
    void 사용자_선택() {
        assertThat(RouteModePlanner.legMode("WALK", 9000, 1000)).isEqualTo("WALK");
        assertThat(RouteModePlanner.legMode("DRIVE", 100, 1000)).isEqualTo("DRIVE");
    }

    @Test
    @DisplayName("모르는 값은 AUTO 로 본다 — 오타로 화면이 깨지면 안 된다")
    void 모르는_값() {
        assertThat(RouteModePlanner.legMode("hello", 400, 1000)).isEqualTo("WALK");
        assertThat(RouteModePlanner.legMode(null, 1500, 1000)).isEqualTo("DRIVE");
    }

    @Test
    @DisplayName("직선거리는 실제 거리와 맞아야 한다")
    void 거리_계산() {
        // 경복궁 → 종묘. 직선 약 1.6km (도보로는 2.7km).
        double m = RouteModePlanner.distanceMeters(37.5796, 126.9770, 37.5745, 126.9947);
        assertThat(m).isBetween(1500.0, 1750.0);
        // 같은 점이면 0
        assertThat(RouteModePlanner.distanceMeters(37.5, 127.0, 37.5, 127.0)).isZero();
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
cd apps/api && ./gradlew test --tests '*RouteModePlannerTest' --no-daemon
```
기대: 컴파일 실패 — `RouteModePlanner` 가 없다.

- [ ] **Step 3: 최소 구현**

`RouteModePlanner.java`:

```java
package com.byeori.domain.itinerary;

/**
 * 구간마다 걸을지 탈지 정한다.
 *
 * 카카오모빌리티는 자동차 경로만 준다. 고궁에서 고궁으로 걷는 동선에 차 경로를 그리면
 * 길도 시간도 틀린다(수원화성 성곽길이 "5분"으로 나온다 — 걸으면 19분이다). 그렇다고
 * 전부 도보로 두면 부산 감천문화마을→자갈치 6km 를 "73분 걸으세요"가 된다.
 *
 * 그래서 구간마다 가른다. 기준은 직선거리다 — 실제 경로 거리를 알려면 이미 호출을
 * 해야 하는데, 어느 쪽에 호출할지를 정하는 자리라서 쓸 수 없다.
 */
public final class RouteModePlanner {

    private RouteModePlanner() {}

    public static final String WALK = "WALK";
    public static final String DRIVE = "DRIVE";
    public static final String AUTO = "AUTO";

    /** 지구 반지름(m). 구면 근사로 충분하다 — 기준값과 비교하는 데만 쓴다. */
    private static final double EARTH_R = 6_371_000;

    /** 두 점 사이 직선거리(m). */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_R * 2 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * 이 구간을 어떻게 갈 것인가.
     *
     * 사용자가 WALK/DRIVE 를 고르면 거리와 상관없이 따른다 — 비 오는 날은 다 타고 싶고,
     * 날 좋은 날은 다 걷고 싶다. 모르는 값은 AUTO 로 본다. 경로 보기는 읽기 동작이라
     * 오타로 400 을 주는 것보다 그럴듯한 답을 주는 쪽이 낫다.
     */
    public static String legMode(String requested, double meters, int thresholdM) {
        if (WALK.equals(requested) || DRIVE.equals(requested)) return requested;
        return meters <= thresholdM ? WALK : DRIVE;
    }
}
```

- [ ] **Step 4: 통과를 확인한다**

```bash
cd apps/api && ./gradlew test --tests '*RouteModePlannerTest' --no-daemon
```
기대: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add apps/api/src/main/java/com/byeori/domain/itinerary/RouteModePlanner.java \
        apps/api/src/test/java/com/byeori/domain/itinerary/RouteModePlannerTest.java
git commit -m "feat(api): 구간마다 걸을지 탈지 정하는 규칙"
```

---

### Task 3: OSRM 클라이언트

**Files:**
- Create: `apps/api/src/main/java/com/byeori/global/external/OsrmClient.java`
- Create: `apps/api/src/main/java/com/byeori/global/external/dto/OsrmRoute.java`
- Create: `apps/api/src/test/java/com/byeori/global/external/OsrmClientTest.java`
- Modify: `apps/api/src/main/resources/application.yml`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `OsrmRoute(int distance, int duration, List<double[]> path)` — `path` 는 `[위도, 경도]`
  - `OsrmClient.walk(List<double[]> lngLatPoints) -> OsrmRoute` (실패하면 `null`)
  - `OsrmClient.parse(String json) -> OsrmRoute` (패키지 공개, 테스트용)

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`OsrmClientTest.java`:

```java
package com.byeori.global.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.global.external.dto.OsrmRoute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OSRM 응답 해석.
 *
 * 좌표 순서가 함정이다 — OSRM 은 geojson 규약대로 [경도, 위도] 로 주는데
 * 우리 지도는 [위도, 경도] 로 쓴다. 뒤집지 않으면 경로가 바다에 그려진다.
 */
class OsrmClientTest {

    /** 실제 응답을 줄인 것(경복궁 근처 두 점). */
    private static final String OK = """
            {"code":"Ok","routes":[{"distance":2749.1,"duration":1979.4,
              "geometry":{"type":"LineString","coordinates":[[126.9770,37.5796],[126.9800,37.5780],[126.9947,37.5745]]}}]}""";

    @Test
    @DisplayName("거리·시간을 정수로 읽는다")
    void 요약() {
        OsrmRoute r = OsrmClient.parse(OK);
        assertThat(r).isNotNull();
        assertThat(r.distance()).isEqualTo(2749);
        assertThat(r.duration()).isEqualTo(1979);
    }

    @Test
    @DisplayName("좌표를 [위도, 경도] 로 뒤집어 준다")
    void 좌표_순서() {
        OsrmRoute r = OsrmClient.parse(OK);
        assertThat(r.path()).hasSize(3);
        assertThat(r.path().get(0)[0]).isEqualTo(37.5796);   // 위도가 앞
        assertThat(r.path().get(0)[1]).isEqualTo(126.9770);  // 경도가 뒤
    }

    @Test
    @DisplayName("경로를 못 찾으면 null — 걸어서 갈 수 없는 구간이 있다(제주↔육지)")
    void 경로_없음() {
        assertThat(OsrmClient.parse("{\\"code\\":\\"NoRoute\\",\\"message\\":\\"...\\"}")).isNull();
    }

    @Test
    @DisplayName("망가진 응답에도 터지지 않는다")
    void 이상한_응답() {
        assertThat(OsrmClient.parse("")).isNull();
        assertThat(OsrmClient.parse("not json")).isNull();
        assertThat(OsrmClient.parse("{\\"code\\":\\"Ok\\",\\"routes\\":[]}")).isNull();
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
cd apps/api && ./gradlew test --tests '*OsrmClientTest' --no-daemon
```
기대: 컴파일 실패

- [ ] **Step 3: DTO 와 클라이언트 구현**

`dto/OsrmRoute.java`:

```java
package com.byeori.global.external.dto;

import java.util.List;

/**
 * OSRM 도보 경로(파싱본).
 * - distance: 총 거리(m), duration: 총 소요시간(초)
 * - path: 지도에 그릴 좌표열. 각 원소 [위도(lat), 경도(lng)] — KakaoRoute 와 달리 이미 뒤집혀 있다.
 */
public record OsrmRoute(int distance, int duration, List<double[]> path) {}
```

`OsrmClient.java`:

```java
package com.byeori.global.external;

import com.byeori.global.external.dto.OsrmRoute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 도보 경로(자체 호스팅 OSRM).
 *
 * 카카오모빌리티는 보행자 길찾기를 주지 않는다. OSRM 공개 데모 서버도 쓸 수 없다 —
 * URL 의 프로파일을 무시하고 foot·driving 이 같은 답을 준다(자동차 하나만 올려둔 것이다).
 * 그래서 직접 띄운 컨테이너를 내부망으로 부른다.
 *
 * 실패하면 null 을 준다. 부르는 쪽이 자동차 경로로 넘어가야 하기 때문이다 —
 * 이 컨테이너가 죽어도 루트 화면은 떠야 한다.
 */
@Component
@Slf4j
public class OsrmClient {

    private final String baseUrl;
    /** 화면이 기다리는 호출이라 짧게 끊고 자동차 경로로 넘어가는 편이 낫다. */
    private final RestClient http;

    public OsrmClient(@Value("${byeori.route.osrm-url:http://osrm:5000}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(1));
        f.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    private static final ObjectMapper OM = new ObjectMapper();

    /** points 는 [경도, 위도] 순서. 실패하면 null. */
    public OsrmRoute walk(List<double[]> points) {
        if (points == null || points.size() < 2) return null;
        StringJoiner sj = new StringJoiner(";");
        for (double[] p : points) sj.add(p[0] + "," + p[1]);
        String url = baseUrl + "/route/v1/foot/" + sj + "?overview=full&geometries=geojson";
        try {
            return parse(http.get().uri(url).retrieve().body(String.class));
        } catch (Exception e) {
            log.warn("OSRM 도보 경로 실패 ({}좌표): {}", points.size(), e.getMessage());
            return null;
        }
    }

    /** 응답 해석. 못 읽으면 null — 터뜨리지 않는다. */
    static OsrmRoute parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode root = OM.readTree(json);
            if (!"Ok".equals(root.path("code").asText())) return null;
            JsonNode route = root.path("routes").path(0);
            if (route.isMissingNode() || route.isNull()) return null;
            List<double[]> path = new ArrayList<>();
            for (JsonNode c : route.path("geometry").path("coordinates")) {
                // geojson 은 [경도, 위도]. 우리 지도는 [위도, 경도] 를 쓴다.
                path.add(new double[]{c.path(1).asDouble(), c.path(0).asDouble()});
            }
            if (path.isEmpty()) return null;
            return new OsrmRoute((int) route.path("distance").asDouble(),
                    (int) route.path("duration").asDouble(), path);
        } catch (Exception e) {
            log.warn("OSRM 응답 해석 실패: {}", e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 4: 설정 추가**

`application.yml` 의 `byeori:` 아래에 넣는다.

```yaml
  route:
    # 도보 경로 서버. 도커 내부망 이름이라 로컬 실행 시에는 127.0.0.1:5000 으로 덮는다.
    osrm-url: ${OSRM_URL:http://osrm:5000}
    # 이 거리(직선, m) 이내면 걷는 것으로 본다. 실제 루트 구간의 중앙값이 706m 다.
    walk-threshold-m: ${WALK_THRESHOLD_M:1000}
```

- [ ] **Step 5: 통과를 확인하고 커밋**

```bash
cd apps/api && ./gradlew test --tests '*OsrmClientTest' --no-daemon
git add apps/api/src/main/java/com/byeori/global/external/OsrmClient.java \
        apps/api/src/main/java/com/byeori/global/external/dto/OsrmRoute.java \
        apps/api/src/test/java/com/byeori/global/external/OsrmClientTest.java \
        apps/api/src/main/resources/application.yml
git commit -m "feat(api): 도보 경로 클라이언트(OSRM)"
```

---

### Task 4: 계산 결과를 저장한다

**Files:**
- Create: `apps/api/src/main/resources/db/migration/V27__itinerary_routes.sql`
- Create: `apps/api/src/main/java/com/byeori/domain/itinerary/ItineraryRoute.java`
- Create: `apps/api/src/main/java/com/byeori/domain/itinerary/ItineraryRouteRepository.java`
- Create: `apps/api/src/test/java/com/byeori/domain/itinerary/ItineraryRouteStoreTest.java`

**Interfaces:**
- Produces:
  - `ItineraryRoute` 엔티티, 복합키 `(itineraryId, mode)`
  - `ItineraryRouteRepository.findByItineraryIdAndMode(Long, String) -> Optional<ItineraryRoute>`
  - `ItineraryRoute.hashOf(List<double[]> latLngs) -> String` (SHA-256 앞 32자)

- [ ] **Step 1: 마이그레이션을 쓴다**

`V27__itinerary_routes.sql`:

```sql
-- 계산한 경로를 저장해 다시 쓴다.
--
-- 지금은 루트를 열 때마다 카카오를 부르고 최대 1초를 기다린다. 경로는 방문지를 고치기
-- 전까지 변하지 않으므로 한 번 계산해 두면 그 뒤로는 외부 호출이 없다.
--
-- 키에 mode 가 들어가는 이유: 자동/걷기/차 는 답이 다르다. 한 번 계산한 모드는 다시
-- 눌러도 즉시 나온다.
--
-- stops_hash 가 자동 무효화 장치다. 요청이 오면 지금 방문지로 해시를 만들어 비교하고,
-- 다르면 다시 계산한다. 루트를 고치면 해시가 달라지므로 갱신을 잊을 수 없다.
create table itinerary_routes (
    itinerary_id bigint      not null references itineraries(id) on delete cascade,
    mode         varchar(10) not null,              -- AUTO | WALK | DRIVE
    stops_hash   varchar(64) not null,
    distance_m   int         not null,
    duration_s   int         not null,
    path         jsonb       not null,              -- [[위도, 경도], ...]
    legs         jsonb       not null,              -- [{distance, duration, mode}, ...]
    computed_at  timestamp   not null default now(),
    primary key (itinerary_id, mode)
);
```

- [ ] **Step 2: 실패하는 테스트를 쓴다**

`ItineraryRouteStoreTest.java`:

```java
package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 저장된 경로를 언제 다시 쓰고 언제 버리는가.
 *
 * 해시로 가른다. 방문지와 순서가 그대로면 경로도 그대로이므로 저장된 것을 쓰고,
 * 하나라도 바뀌면 다시 계산한다. 갱신을 잊을 수 없는 구조가 낫다.
 */
class ItineraryRouteStoreTest {

    @Test
    @DisplayName("같은 방문지·순서면 같은 해시")
    void 같은_입력() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        List<double[]> b = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        assertThat(ItineraryRoute.hashOf(a)).isEqualTo(ItineraryRoute.hashOf(b));
    }

    @Test
    @DisplayName("순서가 바뀌면 다른 해시 — 끌어서 순서를 바꾸면 경로도 달라진다")
    void 순서가_바뀌면() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        List<double[]> b = List.of(new double[]{37.6, 127.1}, new double[]{37.5, 127.0});
        assertThat(ItineraryRoute.hashOf(a)).isNotEqualTo(ItineraryRoute.hashOf(b));
    }

    @Test
    @DisplayName("방문지가 늘면 다른 해시")
    void 방문지가_늘면() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        List<double[]> b = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1},
                                   new double[]{37.7, 127.2});
        assertThat(ItineraryRoute.hashOf(a)).isNotEqualTo(ItineraryRoute.hashOf(b));
    }

    @Test
    @DisplayName("빈 목록도 터지지 않는다")
    void 빈_목록() {
        assertThat(ItineraryRoute.hashOf(List.of())).isNotBlank();
    }
}
```

- [ ] **Step 3: 실패를 확인한다**

```bash
cd apps/api && ./gradlew test --tests '*ItineraryRouteStoreTest' --no-daemon
```
기대: 컴파일 실패

- [ ] **Step 4: 엔티티와 저장소 구현**

`ItineraryRoute.java`:

```java
package com.byeori.domain.itinerary;

import jakarta.persistence.*;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 계산해 둔 경로. 방문지가 그대로면 다시 쓰고, 바뀌면 버린다. */
@Entity
@Table(name = "itinerary_routes")
@IdClass(ItineraryRoute.Key.class)
@Getter
public class ItineraryRoute {

    /** (루트, 모드) 복합키. 모드마다 답이 달라 따로 저장한다. */
    public static class Key implements Serializable {
        private Long itineraryId;
        private String mode;
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(itineraryId, k.itineraryId) && Objects.equals(mode, k.mode);
        }
        @Override public int hashCode() { return Objects.hash(itineraryId, mode); }
    }

    @Id private Long itineraryId;
    @Id private String mode;

    private String stopsHash;
    private int distanceM;
    private int durationS;

    @JdbcTypeCode(SqlTypes.JSON) private String path;
    @JdbcTypeCode(SqlTypes.JSON) private String legs;

    private LocalDateTime computedAt;

    protected ItineraryRoute() {}

    public ItineraryRoute(Long itineraryId, String mode, String stopsHash,
                          int distanceM, int durationS, String path, String legs) {
        this.itineraryId = itineraryId;
        this.mode = mode;
        update(stopsHash, distanceM, durationS, path, legs);
    }

    public void update(String stopsHash, int distanceM, int durationS, String path, String legs) {
        this.stopsHash = stopsHash;
        this.distanceM = distanceM;
        this.durationS = durationS;
        this.path = path;
        this.legs = legs;
        this.computedAt = LocalDateTime.now();
    }

    public boolean matches(String hash) { return stopsHash != null && stopsHash.equals(hash); }

    /**
     * 방문지 좌표열의 지문.
     *
     * 좌표를 소수점 6자리로 끊어 잇는다. 그보다 잘게 보면 저장 과정의 반올림 차이로
     * 같은 루트가 다른 해시가 되어, 바뀐 것이 없는데도 매번 다시 계산한다.
     */
    public static String hashOf(List<double[]> latLngs) {
        StringBuilder sb = new StringBuilder();
        for (double[] p : latLngs) sb.append(String.format("%.6f,%.6f;", p[0], p[1]));
        try {
            byte[] d = MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format("%02x", d[i]));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", e);
        }
    }
}
```

`ItineraryRouteRepository.java`:

```java
package com.byeori.domain.itinerary;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRouteRepository
        extends JpaRepository<ItineraryRoute, ItineraryRoute.Key> {

    Optional<ItineraryRoute> findByItineraryIdAndMode(Long itineraryId, String mode);
}
```

- [ ] **Step 5: 통과를 확인하고 커밋**

```bash
cd apps/api && ./gradlew test --tests '*ItineraryRouteStoreTest' --no-daemon
git add apps/api/src/main/resources/db/migration/V27__itinerary_routes.sql \
        apps/api/src/main/java/com/byeori/domain/itinerary/ItineraryRoute.java \
        apps/api/src/main/java/com/byeori/domain/itinerary/ItineraryRouteRepository.java \
        apps/api/src/test/java/com/byeori/domain/itinerary/ItineraryRouteStoreTest.java
git commit -m "feat(api): 계산한 경로를 저장해 다시 쓴다"
```

---

### Task 5: 경로 계산을 구간별로 바꾼다

이 태스크가 핵심이다. 구간마다 공급자를 고르고, 실패하면 내려가고, 결과를 저장한다.

**Files:**
- Modify: `apps/api/src/main/java/com/byeori/domain/itinerary/ItineraryService.java:118-159`
- Modify: `apps/api/src/main/java/com/byeori/domain/itinerary/dto/ItineraryDtos.java:85-96`
- Modify: `apps/api/src/main/java/com/byeori/domain/itinerary/ItineraryController.java:56-60`
- Create: `apps/api/src/main/java/com/byeori/domain/itinerary/RouteProperties.java`
- Create: `apps/api/src/test/java/com/byeori/domain/itinerary/RouteFallbackTest.java`

**Interfaces:**
- Consumes: `RouteModePlanner.legMode`, `OsrmClient.walk`, `KakaoMobilityClient.directions`,
  `ItineraryRouteRepository`, `ItineraryRoute.hashOf`
- Produces:
  - `RouteProperties.getWalkThresholdM() -> int` (기본 1000)
  - `RouteLeg(int distance, int duration, String mode)` — 필드가 하나 늘었다
  - `RouteResponse(int distance, int duration, String mode, List<RouteStop> stops,
     List<RouteLeg> legs, List<double[]> path)` — `priority` 자리가 `mode` 로 바뀐다
  - `ItineraryService.route(Long userId, Long id, String mode) -> RouteResponse`

- [ ] **Step 1: 내려가는 길을 검증하는 테스트를 쓴다**

`RouteFallbackTest.java`:

```java
package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.domain.itinerary.dto.ItineraryDtos.RouteLeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 한 구간을 어떻게든 채운다.
 *
 * OSRM 컨테이너가 죽어도, 카카오가 쿼터를 다 써도, 루트 화면은 떠야 한다.
 * 지금보다 나빠지는 경우가 있으면 안 된다.
 */
class RouteFallbackTest {

    @Test
    @DisplayName("도보가 실패하면 자동차로 내려간다")
    void 도보_실패() {
        RouteLeg leg = ItineraryService.legOrFallback(
                null,                   // OSRM 실패
                new int[]{1500, 300},   // 카카오 성공
                1200);
        assertThat(leg.mode()).isEqualTo("DRIVE");
        assertThat(leg.distance()).isEqualTo(1500);
    }

    @Test
    @DisplayName("둘 다 실패하면 직선이라도 준다 — 빈 화면보다 낫다")
    void 둘_다_실패() {
        RouteLeg leg = ItineraryService.legOrFallback(null, null, 1200);
        assertThat(leg.mode()).isEqualTo("STRAIGHT");
        assertThat(leg.distance()).isEqualTo(1200);   // 직선거리
        assertThat(leg.duration()).isPositive();       // 걷는 속도로 어림한다
    }
}
```

- [ ] **Step 2: 실패를 확인한다**

```bash
cd apps/api && ./gradlew test --tests '*RouteFallbackTest' --no-daemon
```
기대: 컴파일 실패 — `legOrFallback` 이 없고 `RouteLeg` 에 `mode` 가 없다.

- [ ] **Step 3: DTO 를 고친다**

`ItineraryDtos.java` 에서 `RouteLeg` 와 `RouteResponse` 를 바꾼다.

```java
    /** 구간별 거리·시간과 이동 수단. mode 는 WALK | DRIVE | STRAIGHT. */
    public record RouteLeg(int distance, int duration, String mode) {}

    /**
     * 경로 응답.
     * - distance: 총 거리(m), duration: 총 소요시간(초)
     * - mode: 요청한 모드(AUTO|WALK|DRIVE). 구간별 실제 수단은 legs[].mode 를 본다.
     * - stops: 좌표가 있어 경로에 포함된 방문지(순서대로)
     * - legs: 구간별 거리/시간/수단
     * - path: 지도에 그릴 polyline 좌표열, 각 원소 [위도(lat), 경도(lng)]
     */
    public record RouteResponse(
            int distance, int duration, String mode,
            List<RouteStop> stops, List<RouteLeg> legs, List<double[]> path) {}
```

- [ ] **Step 4: 내려가는 길을 구현한다**

`ItineraryService.java` 에 추가한다(테스트가 부를 수 있게 `static`).

```java
    /** 걷는 속도(m/s). 직선으로 내려갈 때 시간을 어림하는 데만 쓴다. 약 5km/h. */
    private static final double WALK_MPS = 1.4;

    /**
     * 한 구간을 어떻게든 채운다.
     *
     * OSRM 이 죽어도, 카카오가 실패해도 루트 화면은 떠야 한다. 마지막에는 직선이라도
     * 준다 — 방문 순서와 대략의 거리는 그것만으로도 쓸모가 있다.
     */
    static RouteLeg legOrFallback(OsrmRoute walk, int[] driveSummary, double straightM) {
        if (walk != null) return new RouteLeg(walk.distance(), walk.duration(), "WALK");
        if (driveSummary != null) return new RouteLeg(driveSummary[0], driveSummary[1], "DRIVE");
        return new RouteLeg((int) straightM, (int) (straightM / WALK_MPS), "STRAIGHT");
    }
```

- [ ] **Step 5: 통과를 확인한다**

```bash
cd apps/api && ./gradlew test --tests '*RouteFallbackTest' --no-daemon
```
기대: BUILD SUCCESSFUL

- [ ] **Step 6: `route()` 를 구간별로 다시 쓴다**

`ItineraryService.route` 의 본문(좌표 수집 이후)을 바꾼다. 좌표 수집 부분은 그대로 둔다.

```java
        if (coords.size() < 2) {
            throw new BadRequestException("ROUTE_TOO_FEW_POINTS",
                    "경로를 그리려면 좌표가 있는 방문지가 2곳 이상이어야 합니다.");
        }

        // 모르는 값은 AUTO 로 본다 — 경로 보기는 읽기 동작이라 오타로 400 을 주지 않는다.
        String wanted = "WALK".equals(mode) || "DRIVE".equals(mode) ? mode : "AUTO";

        // 저장해 둔 것이 아직 맞으면 그대로 쓴다. 외부 호출이 한 번도 없다.
        List<double[]> latLngs = coords.stream()
                .map(c -> new double[]{c[1], c[0]}).toList();
        String hash = ItineraryRoute.hashOf(latLngs);
        var saved = routeRepo.findByItineraryIdAndMode(id, wanted).orElse(null);
        if (saved != null && saved.matches(hash)) {
            return fromSaved(saved, wanted, stops);
        }

        int threshold = props.getWalkThresholdM();
        List<RouteLeg> legs = new ArrayList<>();
        List<double[]> path = new ArrayList<>();
        int totalM = 0, totalS = 0;

        for (int i = 0; i < coords.size() - 1; i++) {
            double[] a = coords.get(i), b = coords.get(i + 1);   // [경도, 위도]
            double straight = RouteModePlanner.distanceMeters(a[1], a[0], b[1], b[0]);
            String legWanted = RouteModePlanner.legMode(wanted, straight, threshold);

            OsrmRoute walk = null;
            int[] driveSummary = null;
            List<double[]> legPath = null;

            if ("WALK".equals(legWanted)) {
                walk = osrmClient.walk(List.of(a, b));
                if (walk != null) legPath = walk.path();
            }
            if (walk == null) {   // 도보를 안 고를 수도, 실패했을 수도 있다
                KakaoRoute kr = mobilityClient.directions(List.of(a, b), "RECOMMEND");
                if (kr != null) {
                    driveSummary = new int[]{kr.distance(), kr.duration()};
                    legPath = kr.path().stream().map(p -> new double[]{p[1], p[0]}).toList();
                }
            }
            if (legPath == null) {   // 둘 다 실패 — 직선으로 잇는다
                legPath = List.of(new double[]{a[1], a[0]}, new double[]{b[1], b[0]});
            }

            RouteLeg leg = legOrFallback(walk, driveSummary, straight);
            legs.add(leg);
            totalM += leg.distance();
            totalS += leg.duration();
            // 구간이 이어지는 지점은 한 번만 넣는다
            path.addAll(i == 0 ? legPath : legPath.subList(1, legPath.size()));
        }

        save(id, wanted, hash, totalM, totalS, path, legs);
        return new RouteResponse(totalM, totalS, wanted, stops, legs, path);
```

`save` 와 `fromSaved` 는 Jackson 으로 `path`·`legs` 를 JSON 문자열로 바꿔 넣고 꺼낸다.
같은 루트를 동시에 두 번 요청해도 500 이 나면 안 되므로, 저장은 **있으면 덮어쓰기**로 한다
(같은 입력이면 같은 결과이므로 어느 쪽이 이겨도 된다).

```java
    private void save(Long id, String mode, String hash, int m, int s,
                      List<double[]> path, List<RouteLeg> legs) {
        try {
            String pj = om.writeValueAsString(path), lj = om.writeValueAsString(legs);
            routeRepo.findByItineraryIdAndMode(id, mode).ifPresentOrElse(
                    r -> r.update(hash, m, s, pj, lj),
                    () -> routeRepo.save(new ItineraryRoute(id, mode, hash, m, s, pj, lj)));
        } catch (Exception e) {
            // 저장에 실패해도 경로는 돌려준다. 다음 요청에 다시 계산될 뿐이다.
            log.warn("경로 저장 실패 itinerary={} mode={}: {}", id, mode, e.getMessage());
        }
    }
```

- [ ] **Step 7: 설정 클래스와 서비스 배선을 추가한다**

`RouteProperties.java` (`domain/itinerary` 아래):

```java
package com.byeori.domain.itinerary;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 경로 설정. 기준값을 빌드 없이 바꿔 볼 수 있어야 해서 설정으로 뺀다. */
@Component
@ConfigurationProperties(prefix = "byeori.route")
public class RouteProperties {

    /** 이 거리(직선, m) 이내면 걷는 것으로 본다. 실제 루트 구간의 중앙값이 706m 다. */
    private int walkThresholdM = 1000;

    public int getWalkThresholdM() { return walkThresholdM; }
    public void setWalkThresholdM(int v) { this.walkThresholdM = v; }
}
```

`ItineraryService` 에 필드 넷을 더하고 생성자에 받는다(이 클래스는 명시적 생성자를 쓴다).

```java
    private final OsrmClient osrmClient;
    private final ItineraryRouteRepository routeRepo;
    private final RouteProperties props;
    private final ObjectMapper om = new ObjectMapper();
```

생성자 인자 끝에 `OsrmClient osrmClient, ItineraryRouteRepository routeRepo,
RouteProperties props` 를 더하고 대입한다. 기존 인자 순서는 건드리지 않는다.

저장된 것을 응답으로 되돌리는 `fromSaved`:

```java
    /** 저장해 둔 것을 응답 모양으로 되돌린다. 못 읽으면 null 을 주어 다시 계산하게 한다. */
    private RouteResponse fromSaved(ItineraryRoute r, String mode, List<RouteStop> stops) {
        try {
            List<double[]> path = om.readValue(r.getPath(), new TypeReference<List<double[]>>() {});
            List<RouteLeg> legs = om.readValue(r.getLegs(), new TypeReference<List<RouteLeg>>() {});
            return new RouteResponse(r.getDistanceM(), r.getDurationS(), mode, stops, legs, path);
        } catch (Exception e) {
            log.warn("저장된 경로를 읽지 못했다 itinerary={} mode={}: {}",
                    r.getItineraryId(), mode, e.getMessage());
            return null;
        }
    }
```

`route()` 의 재사용 분기는 null 을 걸러야 한다.

```java
        if (saved != null && saved.matches(hash)) {
            RouteResponse cached = fromSaved(saved, wanted, stops);
            if (cached != null) return cached;   // 못 읽으면 아래로 내려가 다시 계산한다
        }
```

`import com.fasterxml.jackson.core.type.TypeReference;` 와
`import com.fasterxml.jackson.databind.ObjectMapper;` 를 더한다.

- [ ] **Step 8: 컨트롤러의 파라미터를 바꾼다**

`ItineraryController.java:56-60`:

```java
    /** 저장해 둔 경로가 아직 맞으면 그대로 준다. mode: AUTO(기본)|WALK|DRIVE. */
    @GetMapping("/itineraries/{id}/route")
    public ApiResponse<RouteResponse> route(@AuthenticationPrincipal Long userId,
                                            @PathVariable Long id,
                                            @RequestParam(defaultValue = "AUTO") String mode) {
        return ApiResponse.ok(service.route(userId, id, mode));
    }
```

- [ ] **Step 9: 전체 테스트를 돌린다**

```bash
cd apps/api && ./gradlew test --no-daemon
```
기대: BUILD SUCCESSFUL. 기존 테스트가 `RouteLeg`/`RouteResponse` 변경으로 깨지면 함께 고친다.

- [ ] **Step 10: 커밋**

```bash
git add apps/api/src
git commit -m "feat(api): 가까운 구간은 걷고 먼 구간은 타는 경로를 준다"
```

---

### Task 6: 운영에서 실제로 달라지는지 확인한다

코드가 아니라 **확인**이 산출물인 태스크다. 배포 전에 로컬 인스턴스로 잰다.

**Files:** 없음(검증만)

- [ ] **Step 1: 빌드하고 로컬에서 띄운다**

```bash
cd apps/api && ./gradlew bootJar --no-daemon
cd /home/hidi/dev/byeori/infra && set -a && . ./.env && set +a
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/byeori SERVER_PORT=8081 \
OSRM_URL=http://127.0.0.1:5000 \
java -Xmx512m -jar ../apps/api/build/libs/api-0.0.1-SNAPSHOT.jar &
```

OSRM 컨테이너가 호스트 포트를 안 열므로, 로컬 검증용으로만 `-p 127.0.0.1:5000:5000` 을
붙여 따로 띄우거나 `OSRM_URL` 을 컨테이너 IP 로 준다.

- [ ] **Step 2: 세 모드를 비교한다**

```bash
# 관리자로 로그인해 토큰을 얻고, 스톱이 2곳 이상인 루트 id 로
for M in AUTO WALK DRIVE; do
  curl -s -H "Authorization: Bearer $T" \
    "http://127.0.0.1:8081/api/v1/itineraries/$ID/route?mode=$M" |
  python3 -I -c "
import sys,json,collections
d=json.load(sys.stdin)['data']
print(f\"{d['mode']:6} 총 {d['distance']:6}m {d['duration']//60:4}분  좌표 {len(d['path']):4}개  \"
      f\"{dict(collections.Counter(l['mode'] for l in d['legs']))}\")"
done
```

기대: `AUTO` 는 `{'WALK': n, 'DRIVE': m}` 처럼 섞이고, `WALK` 는 전부 WALK,
`DRIVE` 는 전부 DRIVE. 그리고 세 모드의 총 거리·시간이 서로 다르다.

- [ ] **Step 3: 저장이 먹는지 확인한다**

같은 요청을 두 번 보내 두 번째가 눈에 띄게 빠른지 보고, DB 를 확인한다.

```bash
docker exec byeori-postgres psql -U byeori -d byeori -c \
  "select itinerary_id, mode, distance_m, duration_s, left(stops_hash,8) from itinerary_routes"
```

기대: 모드마다 한 줄씩.

- [ ] **Step 4: OSRM 이 죽었을 때를 확인한다 — 가장 중요하다**

```bash
docker stop byeori-osrm
curl -s -H "Authorization: Bearer $T" \
  "http://127.0.0.1:8081/api/v1/itineraries/$ID/route?mode=WALK&_=$(date +%s)" |
  python3 -I -c "import sys,json; d=json.load(sys.stdin); print('성공' if d['success'] else d['error'])"
docker start byeori-osrm
```

기대: **성공**. 구간이 전부 `DRIVE` 로 내려가 있어야 한다. 실패하면 Task 5 로 돌아간다.

> 저장된 결과가 돌아오면 검증이 안 되므로, 먼저 해당 모드 행을 지우거나
> 방문지를 하나 바꿔 해시를 깨뜨린 뒤에 확인한다.

- [ ] **Step 5: 로컬 인스턴스를 정리한다**

```bash
ps -eo pid,comm,args --no-headers | awk '$2=="java" && /SNAPSHOT.jar/ {print $1}' | xargs -r kill
```

`pkill -f` 를 쓰지 않는다 — 패턴이 자기 셸에 걸려 같이 죽는다(이 레포에서 두 번 겪었다).

---

### Task 7: 지도가 실제 경로를 그린다

**Files:**
- Modify: `apps/app/src/lib/api/itineraries.ts:62-69,119-121`
- Modify: `apps/app/src/lib/hooks/queries.ts:258`
- Modify: `apps/app/src/lib/map/kakaoMapSource.ts:142-164`
- Modify: `apps/app/src/app/(tabs)/map.tsx:61-63,294-306,400-427`

**Interfaces:**
- Consumes: `RouteResponse` 의 `mode`, `legs[].mode`, `path`

- [ ] **Step 1: 타입과 호출을 고친다**

`itineraries.ts`:

```typescript
export type RouteLeg = {
  distance: number;            // 구간 거리(m)
  duration: number;            // 구간 시간(초)
  mode: 'WALK' | 'DRIVE' | 'STRAIGHT';
};

export type ItineraryRoute = {
  distance: number;            // 총 거리(m)
  duration: number;            // 총 소요시간(초)
  mode: string;                // 요청한 모드(AUTO|WALK|DRIVE)
  stops: RouteStop[];          // 경로에 포함된 방문지(순서대로)
  legs: RouteLeg[];            // 구간별 거리/시간/이동수단
  path: [number, number][];    // polyline 좌표열 [위도, 경도]
};

export function fetchItineraryRoute(id: number, mode = 'AUTO'): Promise<ItineraryRoute> {
  return unwrap<ItineraryRoute>(api.get<ApiEnvelope<ItineraryRoute>>(`/itineraries/${id}/route`, { params: { mode } }));
}
```

`queries.ts:258` 의 `useItineraryRouteQuery` 도 `priority` 를 `mode` 로 바꾸고
쿼리 키에 넣는다(모드를 바꾸면 다시 받아야 한다).

```typescript
export function useItineraryRouteQuery(id: number, mode = 'AUTO') {
  return useQuery({
    queryKey: ['itinerary-route', id, mode],
    queryFn: () => fetchItineraryRoute(id, mode),
    enabled: !!id,
  });
}
```

- [ ] **Step 2: 지도가 `path` 를 그리게 한다**

`kakaoMapSource.ts` 의 `window.drawRoute` 에서 직선 부분을 바꾼다.

```javascript
  // 여행 경로: 서버가 계산한 도로/보도 경로선 + 번호 마커.
  // data={path:[[lat,lng]...], stops:[...], legs:[{mode}...]}
  window.drawRoute = function(data){
    if(!ready){ pendingRoute=data; return; }
    window.clearRoute();
    clearArr(venueOv); clearArr(kakaoOv);
    var stops=(data.stops||[]).slice().sort(function(a,b){ return a.order-b.order; });
    var path=data.path||[];
    // 전에는 path 를 받아놓고 버린 뒤 방문지를 직선으로 이었다. 이제 실제 경로를 그린다.
    // 걷는 구간은 실선, 타는 구간은 점선으로 구분한다.
    if(path.length>1){
      var legs=data.legs||[];
      var walkOnly = legs.length>0 && legs.every(function(l){ return l.mode==='WALK'; });
      var ll=path.map(function(p){ return new kakao.maps.LatLng(p[0],p[1]); });
      var line=new kakao.maps.Polyline({path:ll,strokeWeight:5,strokeColor:segColor(0),
        strokeOpacity:0.9,strokeStyle: walkOnly ? 'solid' : 'shortdash'});
      line.setMap(map); routeLines.push(line);
    } else {
      // 경로가 없으면(둘 다 실패) 방문지를 직선으로 잇는다 — 빈 지도보다 낫다.
      var sl=stops.map(function(s){ return new kakao.maps.LatLng(s.lat,s.lng); });
      for(var i=0;i<sl.length-1;i++){
        var seg=new kakao.maps.Polyline({path:[sl[i],sl[i+1]],strokeWeight:4,
          strokeColor:segColor(i),strokeOpacity:0.7,strokeStyle:'shortdash'});
        seg.setMap(map); routeLines.push(seg);
      }
    }
    stops.forEach(function(s,i){
      var pos=new kakao.maps.LatLng(s.lat,s.lng);
      var html='<div class="pinR" style="background:'+segColor(i)+'">'+(s.order+1)+'</div>';
      var ov=new kakao.maps.CustomOverlay({position:pos,content:html,yAnchor:1});
      ov.setMap(map); routeOv.push(ov);
    });
    var b=new kakao.maps.LatLngBounds();
    (path.length>1?path:stops.map(function(s){return [s.lat,s.lng];}))
      .forEach(function(p){ b.extend(new kakao.maps.LatLng(p[0],p[1])); });
    if(routeOv.length) map.setBounds(b);
  };
```

- [ ] **Step 3: 모드 칩을 붙인다**

`map.tsx` 에서 상태를 두고 배너에 칩 세 개를 넣는다.

```tsx
  const [routeModeSel, setRouteModeSel] = useState<'AUTO'|'WALK'|'DRIVE'>('AUTO');
  const routeQuery = useItineraryRouteQuery(
    routeMode ? (itineraryId as number) : 0, routeModeSel,
  );
```

배너 안, 요약 텍스트 아래에 넣는다.

```tsx
  <View style={styles.routeModes}>
    {([['AUTO','자동'],['WALK','걷기'],['DRIVE','차']] as const).map(([v, label]) => (
      <Pressable key={v} hitSlop={6} onPress={() => setRouteModeSel(v)}
        style={[styles.routeModeChip, routeModeSel === v && styles.routeModeChipOn]}>
        <Text style={[styles.routeModeText, routeModeSel === v && styles.routeModeTextOn]}>
          {label}
        </Text>
      </Pressable>
    ))}
  </View>
```

`drawRoute` 로 넘기는 payload 에 `legs` 를 추가한다(`map.tsx:294-299`).

```tsx
  const payload = JSON.stringify({
    path: routeQuery.data.path,
    stops: routeQuery.data.stops,
    legs: routeQuery.data.legs,
  });
```

- [ ] **Step 4: 타입 검사**

```bash
cd apps/app && npx tsc --noEmit
```
기대: 출력 없음. 앱에는 테스트 러너를 넣지 않는다(`package.json` 이 바뀌면 OTA 가 막힌다).

- [ ] **Step 5: 웹으로 눈으로 확인한다**

웹 빌드를 올려 루트 보기를 연다. 확인할 것:
- 선이 건물을 뚫지 않고 길을 따라간다
- 자동/걷기/차 를 누르면 선과 요약 숫자가 바뀐다
- 걷기만인 경로는 실선, 섞인 경로는 점선이다

- [ ] **Step 6: 커밋**

```bash
git add apps/app/src
git commit -m "feat(app): 지도가 직선 대신 실제 경로를 그린다"
```
