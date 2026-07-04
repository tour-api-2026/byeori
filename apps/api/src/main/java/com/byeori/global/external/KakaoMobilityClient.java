package com.byeori.global.external;

import com.byeori.global.external.dto.KakaoRoute;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오모빌리티 길찾기(여러 경유지) 클라이언트.
 * POST https://apis-navi.kakaomobility.com/v1/waypoints/directions
 * 인증: Authorization: KakaoAK {REST 키}. 서버에서만 호출(키 노출 금지).
 */
@Component
@Slf4j
public class KakaoMobilityClient {

    private static final String URL = "https://apis-navi.kakaomobility.com/v1/waypoints/directions";

    private final String restKey;
    private final RestClient http = RestClient.create();
    private final ObjectMapper om = new ObjectMapper();

    public KakaoMobilityClient(@Value("${byeori.maps.kakao-rest-key:}") String restKey) {
        this.restKey = restKey;
    }

    public boolean enabled() {
        return restKey != null && !restKey.isBlank();
    }

    /**
     * 경로 계산. points는 [경도, 위도] 순서의 좌표 목록(첫=출발, 끝=도착, 중간=경유지).
     * priority: RECOMMEND(추천)|TIME(최단시간)|DISTANCE(최단거리).
     * 실패 시 null.
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

        List<double[]> path = new ArrayList<>();
        List<int[]> legs = new ArrayList<>();
        for (JsonNode section : route.path("sections")) {
            for (JsonNode road : section.path("roads")) {
                JsonNode vertexes = road.path("vertexes"); // [경도,위도, 경도,위도, ...]
                for (int i = 0; i + 1 < vertexes.size(); i += 2) {
                    path.add(new double[]{vertexes.get(i).asDouble(), vertexes.get(i + 1).asDouble()});
                }
            }
            // pathEnd: 이 구간이 끝나는 path 인덱스(exclusive) — 지도에서 구간별로 선을 나눠 그릴 때 사용
            legs.add(new int[]{section.path("distance").asInt(), section.path("duration").asInt(), path.size()});
        }
        return new KakaoRoute(distance, duration, path, legs);
    }
}
