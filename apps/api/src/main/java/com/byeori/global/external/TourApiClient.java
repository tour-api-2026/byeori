package com.byeori.global.external;

import com.byeori.global.external.dto.RegionCode;
import com.byeori.global.external.dto.TourDetail;
import com.byeori.global.external.dto.TourFestivalItem;
import com.byeori.global.external.dto.TourItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    private final ObjectMapper om = new ObjectMapper();

    /**
     * 동기화용. 대량 페이지를 도는 호출이라 여유를 준다.
     * 타임아웃이 없으면 공사 API가 응답하지 않을 때 스레드가 그대로 묶인다.
     */
    private final RestClient http = client(Duration.ofSeconds(3), Duration.ofSeconds(20));

    /**
     * 사용자 요청 경로(장소 상세)에서 쓰는 실시간 조회용. 화면이 기다리는 호출이라
     * 짧게 끊고 저장된 정보로 넘어가는 편이 낫다.
     */
    private final RestClient liveHttp = client(Duration.ofSeconds(2), Duration.ofSeconds(3));

    private static RestClient client(Duration connect, Duration read) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connect);
        f.setReadTimeout(read);
        return RestClient.builder().requestFactory(f).build();
    }

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
     * 위치기반 목록(locationBasedList2). 좌표와 반경으로 주변 장소를 실시간 조회한다.
     *
     * 지도 화면이 보고 있는 영역만 그때그때 받아오므로, 전국 데이터를 미리 저장해 둘 필요가 없다.
     * 사용자 요청 경로에서 호출되므로 짧은 타임아웃(liveHttp)을 쓰고, 실패하면 빈 목록을 돌려
     * 호출부가 저장된 데이터로 대체할 수 있게 한다.
     *
     * @param radius 미터 단위(공사 API 최대 20,000)
     * @param contentTypeId 0이면 전체 유형
     */
    public List<TourItem> locationBasedList(double lng, double lat, int radius, int contentTypeId, int rows) {
        if (!props.tourApiEnabled()) return List.of();
        try {
            URI uri = UriComponentsBuilder.fromUriString(BASE + "/locationBasedList2")
                    .queryParam("serviceKey", encKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("arrange", "E")            // E = 거리순
                    .queryParam("mapX", lng)
                    .queryParam("mapY", lat)
                    .queryParam("radius", Math.min(radius, 20000))
                    .queryParamIfPresent("contentTypeId",
                            Optional.ofNullable(contentTypeId > 0 ? contentTypeId : null))
                    .queryParam("numOfRows", rows)
                    .queryParam("pageNo", 1)
                    .build(true)
                    .toUri();
            return parseItems(liveHttp.get().uri(uri).retrieve().body(String.class));
        } catch (Exception e) {
            log.warn("TourAPI locationBasedList 실패 ({},{}) r={}: {}", lat, lng, radius, e.getMessage());
            return List.of();
        }
    }

    /**
     * 키워드 검색(searchKeyword2). 지도 검색창에서 사용자가 입력한 말로 실시간 조회한다.
     */
    public List<TourItem> searchKeyword(String keyword, int contentTypeId, int rows) {
        if (!props.tourApiEnabled() || keyword == null || keyword.isBlank()) return List.of();
        try {
            URI uri = UriComponentsBuilder.fromUriString(BASE + "/searchKeyword2")
                    .queryParam("serviceKey", encKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("arrange", "A")
                    .queryParam("keyword", URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8))
                    .queryParamIfPresent("contentTypeId",
                            Optional.ofNullable(contentTypeId > 0 ? contentTypeId : null))
                    .queryParam("numOfRows", rows)
                    .queryParam("pageNo", 1)
                    .build(true)
                    .toUri();
            return parseItems(liveHttp.get().uri(uri).retrieve().body(String.class));
        } catch (Exception e) {
            log.warn("TourAPI searchKeyword 실패 kw={}: {}", keyword, e.getMessage());
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

    /**
     * 장소 상세 정보 실시간 조회. 사용자가 장소 상세 화면을 열 때마다 호출한다.
     *
     * 동기화로 내려받는 목록에는 개요·이용시간·휴무일·문의처가 없어서, 이 값들은
     * 상세를 열 때 공사 API에서 직접 받아온다. detailCommon2로 개요와 콘텐츠 타입을
     * 얻고, 그 타입으로 detailIntro2를 불러 운영 정보를 채운다(타입마다 필드명이 달라
     * contentTypeId가 필요하다).
     *
     * 화면을 막지 않는 것이 우선이라 실패·지연은 조용히 삼키고 null을 돌려준다.
     * 호출부는 null이면 DB에 저장된 정보만으로 화면을 구성한다.
     */
    public TourDetail detail(String contentId) {
        if (!props.tourApiEnabled() || contentId == null || contentId.isBlank()) return null;
        try {
            JsonNode common = firstItem(get("/detailCommon2", b -> b.queryParam("contentId", contentId)));
            if (common == null) return null;

            String typeId = text(common, "contenttypeid");
            JsonNode intro = typeId == null ? null : firstItem(get("/detailIntro2",
                    b -> b.queryParam("contentId", contentId).queryParam("contentTypeId", typeId)));

            return new TourDetail(
                    stripTags(text(common, "overview")),
                    stripTags(text(common, "homepage")),
                    // 이용시간 필드명이 타입마다 다르다(관광지 usetime, 문화시설 usetimeculture 등).
                    intro == null ? null : stripTags(firstText(intro, "usetime", "usetimeculture", "usetimefestival", "opentimefood", "opentime")),
                    intro == null ? null : stripTags(firstText(intro, "restdate", "restdateculture", "restdatefood", "restdateshopping")),
                    intro == null ? null : stripTags(firstText(intro, "infocenter", "infocenterculture", "infocenterfood", "infocentershopping", "sponsor1tel")),
                    intro == null ? null : stripTags(firstText(intro, "parking", "parkingculture", "parkingfood", "parkingshopping")));
        } catch (Exception e) {
            log.warn("TourAPI 상세 조회 실패 contentId={}: {}", contentId, e.getMessage());
            return null;
        }
    }

    /** 공통 쿼리(인증키·앱 정보·JSON)를 붙여 GET 하고 본문을 돌려준다. */
    private String get(String path, java.util.function.UnaryOperator<UriComponentsBuilder> extra) {
        URI uri = extra.apply(UriComponentsBuilder.fromUriString(BASE + path)
                        .queryParam("serviceKey", encKey())
                        .queryParam("MobileOS", "ETC")
                        .queryParam("MobileApp", "byeori")
                        .queryParam("_type", "json"))
                .build(true).toUri();
        return liveHttp.get().uri(uri).retrieve().body(String.class);
    }

    private JsonNode firstItem(String json) throws Exception {
        List<JsonNode> list = items(json);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 후보 필드명을 순서대로 보고 처음 값이 있는 것을 돌려준다. */
    private String firstText(JsonNode n, String... names) {
        for (String name : names) {
            String v = text(n, name);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * 공사 API 텍스트에는 HTML이 섞여 온다(homepage는 &lt;a href&gt;, 휴무일·이용시간은 &lt;br&gt;).
     * 앱은 일반 텍스트로 그리므로 태그를 걷어낸다. 줄바꿈 의미가 있는 &lt;br&gt;은 개행으로 바꾼다.
     */
    private static String stripTags(String s) {
        if (s == null) return null;
        String plain = s
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]*>", " ")
                .replace("&amp;", "&").replace("&nbsp;", " ")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\n *", "\n")
                .trim();
        return plain.isBlank() ? null : plain;
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
