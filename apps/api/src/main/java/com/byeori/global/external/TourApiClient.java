package com.byeori.global.external;

import com.byeori.global.external.dto.RegionCode;
import com.byeori.global.external.dto.TourFestivalItem;
import com.byeori.global.external.dto.TourItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TourAPI(한국관광공사_국문 관광정보 서비스, KorService2, data.go.kr 15101578) 클라이언트.
 * - serviceKey: data.go.kr "디코딩 키"를 직접 URL인코딩(build(true)) + URI 객체로 전달해
 *   RestClient의 재인코딩(%→%25, 401 원인)을 방지한다.
 * - 지역은 v4.4 법정동코드(lDongRegnCd/lDongSignguCd) 사용, 분류는 신분류체계(lclsSystm).
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

    /** 지역기반 목록(areaBasedList2). lDongSignguCd는 lDongRegnCd 동반 필수. */
    public List<TourItem> areaBasedList(String lDongRegnCd, String lDongSignguCd, int contentTypeId, int page, int rows) {
        if (!props.tourApiEnabled()) return List.of();
        try {
            URI uri = UriComponentsBuilder.fromUriString(BASE + "/areaBasedList2")
                    .queryParam("serviceKey", encKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("arrange", "A")
                    .queryParam("contentTypeId", contentTypeId)
                    .queryParam("lDongRegnCd", lDongRegnCd)
                    .queryParamIfPresent("lDongSignguCd", Optional.ofNullable(lDongSignguCd))
                    .queryParam("numOfRows", rows)
                    .queryParam("pageNo", page)
                    .build(true)
                    .toUri();
            String body = http.get().uri(uri).retrieve().body(String.class);
            return parseItems(body);
        } catch (Exception e) {
            log.warn("TourAPI areaBasedList 실패 regn={} signgu={} type={} page={}: {}",
                    lDongRegnCd, lDongSignguCd, contentTypeId, page, e.getMessage());
            return List.of();
        }
    }

    /**
     * 축제/행사 목록(searchFestival2). eventStartDate(yyyyMMdd) 이후 진행/예정 행사 검색.
     * areaBasedList2와 동일하게 법정동코드(lDongRegnCd/lDongSignguCd) 사용.
     */
    public List<TourFestivalItem> searchFestival(String eventStartDate, String lDongRegnCd, String lDongSignguCd, int page, int rows) {
        if (!props.tourApiEnabled()) return List.of();
        try {
            URI uri = UriComponentsBuilder.fromUriString(BASE + "/searchFestival2")
                    .queryParam("serviceKey", encKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("arrange", "A")
                    .queryParam("eventStartDate", eventStartDate)
                    .queryParam("lDongRegnCd", lDongRegnCd)
                    .queryParamIfPresent("lDongSignguCd", Optional.ofNullable(lDongSignguCd))
                    .queryParam("numOfRows", rows)
                    .queryParam("pageNo", page)
                    .build(true)
                    .toUri();
            String body = http.get().uri(uri).retrieve().body(String.class);
            return parseFestivals(body);
        } catch (Exception e) {
            log.warn("TourAPI searchFestival 실패 regn={} signgu={} page={}: {}",
                    lDongRegnCd, lDongSignguCd, page, e.getMessage());
            return List.of();
        }
    }

    /** 법정동 코드 조회(ldongCode2). parentRegnCd=null이면 시도 목록, 있으면 그 시도의 시군구 목록. */
    public List<RegionCode> ldongCodes(String parentRegnCd) {
        if (!props.tourApiEnabled()) return List.of();
        try {
            URI uri = UriComponentsBuilder.fromUriString(BASE + "/ldongCode2")
                    .queryParam("serviceKey", encKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("lDongListYn", "N")
                    .queryParamIfPresent("lDongRegnCd", Optional.ofNullable(parentRegnCd))
                    .queryParam("numOfRows", 100)
                    .queryParam("pageNo", 1)
                    .build(true)
                    .toUri();
            String body = http.get().uri(uri).retrieve().body(String.class);
            List<RegionCode> out = new ArrayList<>();
            for (JsonNode n : items(body)) {
                out.add(new RegionCode(text(n, "code"), text(n, "name")));
            }
            return out;
        } catch (Exception e) {
            log.warn("TourAPI ldongCode2 실패 parent={}: {}", parentRegnCd, e.getMessage());
            return List.of();
        }
    }

    private String encKey() {
        return URLEncoder.encode(props.getTourApiKey(), StandardCharsets.UTF_8);
    }

    private List<JsonNode> items(String json) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        if (json == null || json.isBlank() || json.trim().startsWith("<")) return out;
        JsonNode items = om.readTree(json).path("response").path("body").path("items").path("item");
        if (items.isArray()) items.forEach(out::add);
        else if (items.isObject()) out.add(items);
        return out;
    }

    private List<TourItem> parseItems(String json) throws Exception {
        List<TourItem> out = new ArrayList<>();
        for (JsonNode n : items(json)) {
            out.add(new TourItem(
                    text(n, "contentid"), text(n, "title"), text(n, "addr1"),
                    text(n, "mapy"), text(n, "mapx"), text(n, "firstimage"),
                    text(n, "contenttypeid"), text(n, "lclsSystm2"), text(n, "lclsSystm3"), text(n, "tel")));
        }
        return out;
    }

    private List<TourFestivalItem> parseFestivals(String json) throws Exception {
        List<TourFestivalItem> out = new ArrayList<>();
        for (JsonNode n : items(json)) {
            out.add(new TourFestivalItem(
                    text(n, "contentid"), text(n, "title"), text(n, "addr1"),
                    text(n, "mapy"), text(n, "mapx"), text(n, "firstimage"), text(n, "tel"),
                    text(n, "eventstartdate"), text(n, "eventenddate")));
        }
        return out;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull() || v.asText().isBlank()) ? null : v.asText();
    }
}
