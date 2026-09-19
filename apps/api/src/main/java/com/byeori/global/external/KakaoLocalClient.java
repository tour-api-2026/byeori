package com.byeori.global.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로컬 키워드 검색. GET https://dapi.kakao.com/v2/local/search/keyword.json
 * 인증: Authorization: KakaoAK {REST 키}. 서버에서만 호출(키 노출 금지).
 */
@Component
@Slf4j
public class KakaoLocalClient {

    private static final String URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    private final String restKey;
    private final ObjectMapper om = new ObjectMapper();
    private final RestClient http;

    /** 카카오 장소 한 곳. categoryGroup 은 FD6(음식점)·CE7(카페)·AT4(관광명소) 같은 그룹 코드. */
    public record Place(String id, String name, String categoryGroup, String categoryName,
                        String address, String phone, double lat, double lng) {}

    public KakaoLocalClient(@Value("${byeori.maps.kakao-rest-key:}") String restKey) {
        this.restKey = restKey;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(2));
        f.setReadTimeout(Duration.ofSeconds(4));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    /** 카카오 그룹 코드 → 벼리 분류. 음식점·카페 외에는 관람으로 본다. */
    public static String toByeoriCategory(String group) {
        return switch (group == null ? "" : group) {
            case "FD6" -> "맛집";
            case "CE7" -> "카페";
            default -> "문화";
        };
    }

    public boolean enabled() {
        return restKey != null && !restKey.isBlank();
    }

    /**
     * 키워드 검색. lat/lng 가 있으면 그 주변을 가까운 순으로 본다(루트의 마지막 장소 기준).
     * 실패 시 빈 목록.
     */
    public List<Place> search(String query, Double lat, Double lng) {
        if (!enabled() || query == null || query.isBlank()) return List.of();
        try {
            UriComponentsBuilder b = UriComponentsBuilder.fromUriString(URL)
                    .queryParam("query", query.strip())
                    .queryParam("size", 15);
            if (lat != null && lng != null) {
                b.queryParam("y", lat).queryParam("x", lng).queryParam("radius", 20000).queryParam("sort", "distance");
            }
            URI uri = b.encode().build().toUri();
            String json = http.get().uri(uri)
                    .header("Authorization", "KakaoAK " + restKey)
                    .retrieve()
                    .body(String.class);

            List<Place> out = new ArrayList<>();
            for (JsonNode d : om.readTree(json).path("documents")) {
                String road = d.path("road_address_name").asText("");
                out.add(new Place(
                        d.path("id").asText(),
                        d.path("place_name").asText(),
                        d.path("category_group_code").asText(""),
                        d.path("category_name").asText(""),
                        road.isBlank() ? d.path("address_name").asText("") : road,
                        d.path("phone").asText(""),
                        d.path("y").asDouble(),
                        d.path("x").asDouble()));
            }
            return out;
        } catch (Exception e) {
            log.warn("카카오 로컬 검색 실패: {}", e.getMessage());
            return List.of();
        }
    }
}
