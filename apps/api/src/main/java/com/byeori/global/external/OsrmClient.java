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

    /** points 는 [경도, 위도] 순서. 실패하면 null. */
    public OsrmRoute walk(List<double[]> points) {
        if (points == null || points.size() < 2) return null;
        try {
            StringJoiner sj = new StringJoiner(";");
            for (double[] p : points) sj.add(p[0] + "," + p[1]);
            String url = baseUrl + "/route/v1/foot/" + sj + "?overview=full&geometries=geojson";
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
