package com.byeori.global.external;

import com.byeori.global.external.dto.TourItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TourAPI(한국관광공사_국문 관광정보 서비스_GW, data.go.kr 15101578) 클라이언트.
 * serviceKey는 data.go.kr "디코딩 키"를 환경변수로 넣는다(빌더가 인코딩하므로 이중인코딩 방지).
 */
@Component
@Slf4j
public class TourApiClient {

    private static final String BASE = "https://apis.data.go.kr/B551011/KorService2";

    private final SyncProperties props;
    private final RestClient http = RestClient.create();
    private final ObjectMapper om = new ObjectMapper();

    public TourApiClient(SyncProperties props) {
        this.props = props;
    }

    /** 지역기반 목록(areaBasedList2). */
    public List<TourItem> areaBasedList(int areaCode, Integer sigunguCode, int contentTypeId, int page, int rows) {
        if (!props.tourApiEnabled()) return List.of();
        try {
            String url = UriComponentsBuilder.fromUriString(BASE + "/areaBasedList2")
                    .queryParam("serviceKey", props.getTourApiKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("arrange", "A")
                    .queryParam("areaCode", areaCode)
                    .queryParamIfPresent("sigunguCode", Optional.ofNullable(sigunguCode))
                    .queryParam("contentTypeId", contentTypeId)
                    .queryParam("numOfRows", rows)
                    .queryParam("pageNo", page)
                    .encode()
                    .build()
                    .toUriString();
            String body = http.get().uri(url).retrieve().body(String.class);
            return parseItems(body);
        } catch (Exception e) {
            log.warn("TourAPI areaBasedList 실패 area={} type={} page={}: {}", areaCode, contentTypeId, page, e.getMessage());
            return List.of();
        }
    }

    private List<TourItem> parseItems(String json) throws Exception {
        List<TourItem> out = new ArrayList<>();
        if (json == null || json.isBlank() || json.trim().startsWith("<")) return out; // 에러 시 XML 반환될 수 있음
        JsonNode items = om.readTree(json).path("response").path("body").path("items").path("item");
        if (items.isArray()) {
            for (JsonNode n : items) out.add(toItem(n));
        } else if (items.isObject()) {
            out.add(toItem(items));
        }
        return out;
    }

    private TourItem toItem(JsonNode n) {
        return new TourItem(
                text(n, "contentid"), text(n, "title"), text(n, "addr1"),
                text(n, "mapy"), text(n, "mapx"), text(n, "firstimage"),
                text(n, "contenttypeid"), text(n, "cat3"), text(n, "tel"));
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull() || v.asText().isBlank()) ? null : v.asText();
    }
}
