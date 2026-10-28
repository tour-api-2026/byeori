package com.byeori.global.external;

import com.byeori.global.external.dto.RegionCode;
import com.byeori.global.external.dto.TourBasic;
import com.byeori.global.external.dto.TourDetail;
import com.byeori.global.external.dto.TourFestivalItem;
import com.byeori.global.external.dto.TourItem;
import com.byeori.global.i18n.Lang;
import com.byeori.global.external.dto.TourSyncItem;
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

    /**
     * 공사 다국어판. 국문판(KorService2)과 **별도 서비스**이고 contentId 체계도 다르다
     * (경복궁: 국문 126508 / 영문 264337). 인증키는 같은 계정의 것을 그대로 쓴다 —
     * 활용신청만 서비스별로 하면 된다.
     */
    private static final String MULTILANG_BASE = "https://apis.data.go.kr/B551011/";

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

    /**
     * 다국어판 지역기반 목록. 지역을 지정하지 않고 전체를 받는다(한 언어가 1만 5천 건 남짓).
     *
     * 돌려주는 TourItem 의 contentId 는 **다국어판 쪽 id** 다. 국문판 장소와 잇는 일은
     * MultilangSyncService 가 이름·좌표로 한다.
     */
    public List<TourItem> multilangList(Lang lang, int page, int rows) {
        if (!props.tourApiEnabled()) return List.of();
        String service = switch (lang) {
            case EN -> "EngService2";
            case JA -> "JpnService2";
            case ZH -> "ChsService2";
            case KO -> null;
        };
        if (service == null) return List.of();
        try {
            URI uri = UriComponentsBuilder.fromUriString(MULTILANG_BASE + service + "/areaBasedList2")
                    .queryParam("serviceKey", encKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("arrange", "A")
                    .queryParam("numOfRows", rows)
                    .queryParam("pageNo", page)
                    .build(true)
                    .toUri();
            return parseItems(http.get().uri(uri).retrieve().body(String.class));
        } catch (Exception e) {
            log.warn("TourAPI 다국어 목록 실패 lang={} page={}: {}", lang, page, TourApiError.reasonOf(e));
            return List.of();
        }
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
                    lDongRegnCd, lDongSignguCd, contentTypeId, page, TourApiError.reasonOf(e));
            return List.of();
        }
    }

    /**
     * 동기화 목록(areaBasedSyncList2). 공사가 로컬 저장용으로 제공하는 오퍼레이션이다.
     *
     * areaBasedList2 로 전량을 매번 다시 받으면 1회 약 350건을 호출해야 하는데, 이쪽은
     * modifiedtime 이후 변경분만 받아 하루 3건이면 끝난다. showflag 로 공사가 내린
     * 콘텐츠도 알 수 있어 삭제 반영이 가능하다.
     *
     * @param modifiedTime yyyyMMdd. 이 날짜 이후 변경분만. null 이면 전량.
     */
    public List<TourSyncItem> syncList(int contentTypeId, String modifiedTime, int page, int rows) {
        if (!props.tourApiEnabled()) return List.of();
        try {
            URI uri = UriComponentsBuilder.fromUriString(BASE + "/areaBasedSyncList2")
                    .queryParam("serviceKey", encKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("arrange", "C")   // C = 수정일순
                    .queryParamIfPresent("contentTypeId",
                            Optional.ofNullable(contentTypeId > 0 ? contentTypeId : null))
                    .queryParamIfPresent("modifiedtime", Optional.ofNullable(modifiedTime))
                    .queryParam("numOfRows", rows)
                    .queryParam("pageNo", page)
                    .build(true)
                    .toUri();
            String body = http.get().uri(uri).retrieve().body(String.class);
            List<TourSyncItem> out = new ArrayList<>();
            for (JsonNode n : items(body)) {
                out.add(new TourSyncItem(
                        new TourItem(text(n, "contentid"), text(n, "title"), text(n, "addr1"),
                                text(n, "mapy"), text(n, "mapx"), text(n, "firstimage"),
                                text(n, "contenttypeid"), text(n, "lclsSystm2"),
                                text(n, "lclsSystm3"), text(n, "tel")),
                        text(n, "showflag"), text(n, "modifiedtime")));
            }
            return out;
        } catch (Exception e) {
            // 사유를 예외 메시지에 담는다. 이게 sync_logs 에 남아, 잠깐 끊긴 것과
            // 인증키가 죽은 것을 나중에 구별할 수 있게 한다.
            String reason = TourApiError.reasonOf(e);
            log.warn("TourAPI syncList 실패 type={} since={} page={}: {}",
                    contentTypeId, modifiedTime, page, reason);
            throw new IllegalStateException("동기화 목록 조회 실패: " + reason, e);
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
                    lDongRegnCd, lDongSignguCd, page, TourApiError.reasonOf(e));
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
            log.warn("TourAPI ldongCode2 실패 parent={}: {}", parentRegnCd, TourApiError.reasonOf(e));
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
    /**
     * 상세 화면용 기본 정보(detailCommon2). 우리 DB에 없는 장소를 띄울 때 쓴다.
     * 저장분이 있으면 굳이 부르지 않는다(그쪽 값이 이미 화면에 있다).
     */
    public TourBasic basic(String contentId) {
        if (!props.tourApiEnabled() || contentId == null || contentId.isBlank()) return null;
        try {
            JsonNode c = firstItem(get("/detailCommon2", b -> b.queryParam("contentId", contentId)));
            if (c == null) return null;
            return new TourBasic(text(c, "contentid"), text(c, "contenttypeid"), text(c, "title"),
                    text(c, "addr1"), text(c, "firstimage"), text(c, "mapx"), text(c, "mapy"),
                    text(c, "tel"), text(c, "lclsSystm2"), text(c, "lclsSystm3"));
        } catch (Exception e) {
            log.warn("TourAPI 기본 조회 실패 contentId={}: {}", contentId, e.getMessage());
            return null;
        }
    }

    /**
     * 공사 다국어판의 번역된 설명. 없으면 null.
     *
     * 목록(multilangList)에는 설명이 없어서 장소 하나씩 불러야 한다. 그래서 화면이 열릴 때
     * 필요한 것만 받는다(DetailTranslator) — 1만 5천 건을 미리 받으면 하루 호출 한도
     * 1,000건으로 보름이 걸린다.
     *
     * 같은 detailCommon2 지만 **서비스가 달라 contentId 도 다르다.** 국문 번호를 그대로
     * 넣으면 엉뚱한 장소가 오거나 빈 응답이 온다. 넣는 번호는 venues.tour_content_id_* 에
     * 적어 둔 다국어판 번호다(MultilangSyncService 가 찾아 둔다).
     */
    public String multilangOverview(Lang lang, String contentId) {
        if (!props.tourApiEnabled() || contentId == null || contentId.isBlank()) return null;
        String service = switch (lang) {
            case EN -> "EngService2";
            case JA -> "JpnService2";
            case ZH -> "ChsService2";
            case KO -> null;
        };
        if (service == null) return null;
        try {
            URI uri = UriComponentsBuilder.fromUriString(MULTILANG_BASE + service + "/detailCommon2")
                    .queryParam("serviceKey", encKey())
                    .queryParam("MobileOS", "ETC")
                    .queryParam("MobileApp", "byeori")
                    .queryParam("_type", "json")
                    .queryParam("contentId", contentId)
                    .build(true)
                    .toUri();
            JsonNode item = firstItem(http.get().uri(uri).retrieve().body(String.class));
            String overview = item == null ? null : stripTags(text(item, "overview"));
            return (overview == null || overview.isBlank()) ? null : overview;
        } catch (Exception e) {
            log.warn("TourAPI 다국어 설명 조회 실패 lang={} contentId={}: {}",
                    lang, contentId, TourApiError.reasonOf(e));
            return null;
        }
    }

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

    /**
     * 응답에서 item 들을 꺼낸다.
     *
     * 전에는 XML 로 오면(본문이 '<' 로 시작) 그냥 빈 목록을 돌려줬다. 그런데 게이트웨이는
     * 실패를 XML 로 알려 준다 — 그래서 인증키가 죽은 응답이 '항목 0건'과 구별되지 않고,
     * 동기화가 성공 0건으로 조용히 끝났다. 사유가 적혀 있으면 들고 올라간다.
     */
    private List<JsonNode> items(String json) throws Exception {
        List<JsonNode> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        if (json.trim().startsWith("<")) {
            String reason = TourApiError.reasonOf(json);
            if (reason != null) throw new IllegalStateException(reason);
            return out;
        }
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
