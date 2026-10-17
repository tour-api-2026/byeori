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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
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
 * 실패하면 좌표 없는 결과를 준다(절대 던지지 않는다). 부르는 쪽이 자동차 경로로
 * 넘어가야 하기 때문이다 — 이 컨테이너가 죽어도 루트 화면은 떠야 한다.
 */
@Component
@Slf4j
public class OsrmClient {

    private static final ObjectMapper OM = new ObjectMapper();

    private final String baseUrl;
    private final RestClient http;

    /** 화면이 기다리는 호출이라 짧게 끊고 자동차 경로로 넘어가는 편이 낫다. */
    public OsrmClient(@Value("${byeori.route.osrm-url:http://osrm:5000}") String baseUrl) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(1));
        f.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    /**
     * 도보 경로를 물어본 결과.
     *
     * null 하나로는 두 가지를 구분할 수 없어서 둔다. OSRM 에 닿지 못한 것(타임아웃·연결
     * 거부)과, 닿았는데 **그 구간에 걸어갈 길이 없는 것**(NoRoute·NoSegment — OSRM 은
     * 이것도 400 으로 준다)은
     * 전혀 다른 일이다. 앞쪽은 남은 구간을 묻지 말아야 하고(차단기), 뒤쪽은 그 구간만
     * 차로 넘기면 끝이다 — 섞어 세면 걸어서 갈 수 없는 구간 하나 때문에 뒤 구간이 전부
     * 차로 가고, 그 결과는 저장도 못 해 매번 외부 호출을 다시 한다.
     */
    public record WalkResult(OsrmRoute route, boolean serverFailed) {

        /** OSRM 에 닿지 못했거나 응답을 읽을 수 없었다. 차단기를 걸 이유가 된다. */
        public static final WalkResult FAILED = new WalkResult(null, true);

        /** OSRM 은 멀쩡한데 이 구간에 걸어갈 길이 없다. 이 구간만 차로 넘긴다. */
        public static final WalkResult NO_ROUTE = new WalkResult(null, false);

        static WalkResult of(OsrmRoute route) { return new WalkResult(route, false); }
    }

    /** points 는 [경도, 위도] 순서. 절대 던지지 않는다 — 실패도 결과로 준다. */
    public WalkResult walk(List<double[]> points) {
        // 좌표가 모자라면 OSRM 을 부를 것도 없다. 서버 탓이 아니므로 차단기를 걸지 않는다.
        if (points == null || points.size() < 2) return WalkResult.NO_ROUTE;
        try {
            StringJoiner sj = new StringJoiner(";");
            for (double[] p : points) sj.add(p[0] + "," + p[1]);
            // overview=simplified 를 쓴다. full 은 같은 구간(경복궁→종묘)에 148점을 주는데
            // simplified 는 48점이다. 이 지도의 확대 수준에서 눈에 보이는 차이는 없고,
            // 그 좌표열은 응답·jsonb 칼럼·웹뷰 LatLng 루프를 그대로 통과한다.
            // mode=WALK 로 긴 구간을 물으면 full 은 수만 점이 될 수 있다.
            String url = baseUrl + "/route/v1/foot/" + sj + "?overview=simplified&geometries=geojson";
            // 4xx 를 예외로 받지 않는다. OSRM 은 경로를 못 찾을 때도 **400** 으로 답하는데,
            // 기본 설정은 4xx 에서 던지므로 그 답이 아래 catch 로 떨어져 전부 장애가 된다 —
            // 걸어서 갈 수 없는 구간 하나가 남은 구간을 모두 차로 보내 버린다. 상태를
            // 직접 보고 가른다.
            ResponseEntity<String> res = http.get().uri(url).retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> { })
                    .toEntity(String.class);
            return classify(res.getStatusCode().value(), res.getBody());
        } catch (Exception e) {
            log.warn("OSRM 도보 경로 실패 ({}좌표): {}", points.size(), e.getMessage());
            return WalkResult.FAILED;
        }
    }

    /**
     * 상태 코드와 본문으로 결과를 가른다.
     *
     * OSRM 은 경로를 못 찾은 것도 400 으로 알린다. 그중 NoRoute(갈 길이 없다)와
     * NoSegment(좌표 근처에 길이 없다)만 "이 구간은 못 걷는다"는 사실이고, 나머지
     * (InvalidQuery·InvalidOptions·InvalidValue·TooBig…)는 **우리가 잘못 물은 것**이다.
     * 뒤쪽은 지형이 아니라 우리 쪽 버그라, 차단기와 경고 로그가 남아야 한다.
     *
     * 패키지 공개로 둔 이유는 테스트가 실제 상태 코드를 넣어 보기 위해서다.
     */
    static WalkResult classify(int status, String body) {
        if (status >= 200 && status < 300) return parse(body);
        String code = readCode(body);
        if ("NoRoute".equals(code) || "NoSegment".equals(code)) return WalkResult.NO_ROUTE;
        log.warn("OSRM 이 질의를 거절했다 status={} code={}", status, code);
        return WalkResult.FAILED;
    }

    /** 오류 본문에서 code 만 꺼낸다. 못 읽으면 null — 그때는 우리 쪽 문제로 본다. */
    private static String readCode(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            return OM.readTree(body).path("code").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 200 응답 해석. 못 읽어도 터뜨리지 않는다. */
    static WalkResult parse(String json) {
        if (json == null || json.isBlank()) return WalkResult.FAILED;
        try {
            JsonNode root = OM.readTree(json);
            // code != "Ok" 는 장애가 아니다 — 걸어서 갈 수 없는 구간에 OSRM 이 주는 정상 답이다.
            if (!"Ok".equals(root.path("code").asText())) return WalkResult.NO_ROUTE;
            JsonNode route = root.path("routes").path(0);
            if (route.isMissingNode() || route.isNull()) return WalkResult.NO_ROUTE;
            List<double[]> path = new ArrayList<>();
            for (JsonNode c : route.path("geometry").path("coordinates")) {
                // geojson 은 [경도, 위도]. 우리 지도는 [위도, 경도] 를 쓴다.
                path.add(new double[]{c.path(1).asDouble(), c.path(0).asDouble()});
            }
            // Ok 인데 쓸 좌표가 없는 응답도 이 구간을 못 걷는 것으로 본다. 서버는 답하고
            // 있으므로 차단기를 걸어 남은 구간까지 차로 보낼 이유가 없다.
            if (path.isEmpty()) return WalkResult.NO_ROUTE;
            return WalkResult.of(new OsrmRoute((int) route.path("distance").asDouble(),
                    (int) route.path("duration").asDouble(), path));
        } catch (Exception e) {
            // 읽을 수 없는 응답은 서버 쪽이 망가진 것으로 본다(끊긴 본문·프록시 오류).
            log.warn("OSRM 응답 해석 실패: {}", e.getMessage());
            return WalkResult.FAILED;
        }
    }
}
