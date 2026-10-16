package com.byeori.global.external;

import com.byeori.global.external.dto.KakaoRoute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오모빌리티 길찾기 클라이언트.
 * POST https://apis-navi.kakaomobility.com/v1/waypoints/directions
 * 인증: Authorization: KakaoAK {REST 키}. 서버에서만 호출(키 노출 금지).
 *
 * 경유지를 함께 넘길 수 있는 엔드포인트지만, 지금 부르는 쪽은 **구간마다 두 점**을
 * 넘긴다 — 구간별로 걷거나 타기를 정하므로 차 구간만 따로 물어야 하기 때문이다.
 */
@Component
@Slf4j
public class KakaoMobilityClient {

    private static final String URL = "https://apis-navi.kakaomobility.com/v1/waypoints/directions";

    private final String restKey;
    private final RestClient http;
    private final ObjectMapper om = new ObjectMapper();

    /**
     * 타임아웃을 반드시 건다(연결 1초, 읽기 3초).
     *
     * RestClient.create() 는 둘 다 무한이다. 예전에는 루트 하나에 이 호출이 한 번이었지만
     * 지금은 **차 구간마다** 한 번이고, 그것도 경로를 저장하는 쓰기 트랜잭션 안에서 돈다.
     * 카카오가 응답하지 않으면 요청 스레드와 커넥션 풀의 커넥션이 함께 묶인 채로 끝없이
     * 기다린다 — 루트 한 화면이 DB 커넥션을 말려 버릴 수 있다. 짧게 끊고 직선으로
     * 내려가는 편이 낫다(OsrmClient 와 같은 값).
     */
    public KakaoMobilityClient(@Value("${byeori.maps.kakao-rest-key:}") String restKey) {
        this.restKey = restKey;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(1));
        f.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public boolean enabled() {
        return restKey != null && !restKey.isBlank();
    }

    /**
     * 경로 계산. points는 [경도, 위도] 순서의 좌표 목록(첫=출발, 끝=도착, 중간=경유지).
     * priority: RECOMMEND(추천)|TIME(최단시간)|DISTANCE(최단거리).
     * 실패 시 null.
     *
     * 세 점 이상을 넘기면 가운데를 경유지로 올린다. 지금 부르는 쪽은 두 점만 넘기지만,
     * 이 분기를 지우면 가운데 점이 조용히 사라져 엉뚱한 경로가 나온다.
     */
    public KakaoRoute directions(List<double[]> points, String priority) {
        if (!enabled() || points == null || points.size() < 2) return null;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("origin", xy(points.get(0)));
            body.put("destination", xy(points.get(points.size() - 1)));
            if (points.size() > 2) {
                List<Map<String, Object>> wps = new ArrayList<>();
                for (double[] p : points.subList(1, points.size() - 1)) wps.add(xy(p));
                body.put("waypoints", wps);
            }
            body.put("priority", priority == null ? "RECOMMEND" : priority);

            String json = http.post().uri(URL)
                    .header("Authorization", "KakaoAK " + restKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parse(json);
        } catch (Exception e) {
            log.warn("카카오 길찾기 실패 ({}좌표): {}", points.size(), e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> xy(double[] lngLat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", lngLat[0]); // 경도
        m.put("y", lngLat[1]); // 위도
        return m;
    }

    private KakaoRoute parse(String json) throws Exception {
        JsonNode route = om.readTree(json).path("routes").path(0);
        int resultCode = route.path("result_code").asInt(-1);
        if (resultCode != 0) {
            log.warn("카카오 길찾기 result_code={} msg={}", resultCode, route.path("result_msg").asText());
            return null;
        }
        JsonNode summary = route.path("summary");
        int distance = summary.path("distance").asInt();
        int duration = summary.path("duration").asInt();

        // 구간별 요약(sections[].distance/duration)은 읽지 않는다. 구간마다 따로 부르므로
        // section 이 하나뿐이고, 그 값은 위 summary 와 같다.
        List<double[]> path = new ArrayList<>();
        for (JsonNode section : route.path("sections")) {
            for (JsonNode road : section.path("roads")) {
                JsonNode vertexes = road.path("vertexes"); // [경도,위도, 경도,위도, ...]
                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    path.add(new double[]{vertexes.get(i).asDouble(), vertexes.get(i + 1).asDouble()});
                }
            }
        }
        return new KakaoRoute(distance, duration, path);
    }
}
