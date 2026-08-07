package com.byeori.global.external;

import com.byeori.global.external.dto.SeoulEventItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 서울 열린데이터광장 문화행사(culturalEventInfo) 클라이언트.
 * URL은 path-segment 방식: /{KEY}/json/culturalEventInfo/{start}/{end}/{CODENAME}
 * CODENAME(분류명)을 경로로 넘기면 서버 측에서 필터링된다 (예: 국악, 축제-전통/역사).
 */
@Component
@Slf4j
public class SeoulEventClient {

    private static final String BASE = "http://openapi.seoul.go.kr:8088";

    private final SyncProperties props;
    private final RestClient http = RestClient.create();
    private final ObjectMapper om = new ObjectMapper();

    public SeoulEventClient(SyncProperties props) {
        this.props = props;
    }

    /** codename 분류의 행사 목록(start~end 행 범위, 1-base). 실패 시 빈 목록. */
    public List<SeoulEventItem> events(String codename, int start, int end) {
        if (!props.seoulEnabled()) return List.of();
        try {
            String encoded = URLEncoder.encode(codename, StandardCharsets.UTF_8);
            URI uri = URI.create("%s/%s/json/culturalEventInfo/%d/%d/%s"
                    .formatted(BASE, props.getSeoulKey(), start, end, encoded));
            String body = http.get().uri(uri).retrieve().body(String.class);
            return parse(body);
        } catch (Exception e) {
            log.warn("서울 문화행사 조회 실패 codename={} {}~{}: {}", codename, start, end, e.getMessage());
            return List.of();
        }
    }

    private List<SeoulEventItem> parse(String body) throws Exception {
        JsonNode root = om.readTree(body).path("culturalEventInfo");
        String code = root.path("RESULT").path("CODE").asText("");
        // INFO-000 정상, INFO-200 데이터 없음
        if (!code.startsWith("INFO-000")) {
            if (!code.startsWith("INFO-200")) log.warn("서울 문화행사 응답 코드: {}", code.isEmpty() ? body : code);
            return List.of();
        }
        List<SeoulEventItem> items = new ArrayList<>();
        for (JsonNode row : root.path("row")) {
            items.add(new SeoulEventItem(
                    text(row, "CODENAME"), text(row, "TITLE"), text(row, "PLACE"),
                    text(row, "STRTDATE"), text(row, "END_DATE"),
                    text(row, "MAIN_IMG"), text(row, "ORG_LINK"), text(row, "HMPG_ADDR"),
                    text(row, "LAT"), text(row, "LOT")));
        }
        return items;
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return (v == null || v.isBlank()) ? null : v;
    }
}
