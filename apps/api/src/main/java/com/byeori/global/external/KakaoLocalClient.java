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
    /**
     * 한 쪽의 결과와 '끝인지' 여부.
     *
     * 끝 판정을 우리가 계산하지 않고 카카오가 준 is_end 를 그대로 전한다. 카카오는 한 쪽에
     * 15건까지, 전체 45건까지만 내준다(total_count 가 19만이어도 pageable_count 는 45다).
     * 그 상한을 우리 코드에 적어 두면 카카오가 바꿨을 때 따라가지 못한다.
     */
    public record Page(java.util.List<Place> places, boolean end) {}

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
        return searchPage(query, lat, lng, 1).places();
    }

    /**
     * 한 쪽을 받는다. page 는 1부터.
     *
     * size 는 카카오 상한인 15 로 고정한다 — 더 크게 보내면 거부된다. 더 보려면 쪽을 넘긴다.
     */
    public Page searchPage(String query, Double lat, Double lng, int page) {
        if (!enabled() || query == null || query.isBlank()) return new Page(List.of(), true);
        try {
            UriComponentsBuilder b = UriComponentsBuilder.fromUriString(URL)
                    .queryParam("query", query.strip())
                    .queryParam("page", Math.max(1, page))
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
            // is_end 가 없으면(형식이 바뀌었거나 오류) 끝으로 본다 — 모르는 채로 더 부르면
            // 같은 결과를 반복해 받는다(카카오는 상한을 넘긴 쪽에도 마지막 쪽을 돌려준다).
            boolean end = om.readTree(json).path("meta").path("is_end").asBoolean(true);
            return new Page(out, end);
        } catch (Exception e) {
            log.warn("카카오 로컬 검색 실패: {}", e.getMessage());
            return new Page(List.of(), true);
        }
    }
}
