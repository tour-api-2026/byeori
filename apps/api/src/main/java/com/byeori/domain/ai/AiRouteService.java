package com.byeori.domain.ai;

import com.byeori.domain.ai.AiRouteDtos.GenerateRequest;
import com.byeori.domain.ai.AiRouteDtos.Preview;
import com.byeori.domain.ai.AiRouteDtos.Status;
import com.byeori.domain.ai.AiRouteDtos.Stop;
import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.external.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * AI 루트(하루 일정) 생성.
 *
 * AI에게 장소를 지어내게 하지 않는다. 서버가 우리 DB에서 조건에 맞는 후보를 뽑아 주고,
 * AI는 그 안에서 고르고 순서를 정하고 추천 이유만 쓴다. 응답의 ID는 후보와 대조해
 * 없는 것은 버린다. AI가 틀려도 존재하지 않는 장소가 화면에 나오지 않는다.
 */
@Service
@Slf4j
public class AiRouteService {

    /** 앱의 카테고리 칩 중 루트에 넣을 수 있는 것. 한복 혜택은 장소가 몇 곳뿐이라 뺐다. */
    static final Set<String> CATEGORIES = Set.of("문화", "체험", "전통시장", "공예", "한옥스테이", "맛집", "카페");

    static final int MIN_STOPS = 3;
    static final int MAX_STOPS = 6;
    private static final int RADIUS_M = 3000;
    private static final int VENUE_CANDIDATES = 40;
    private static final int EVENT_CANDIDATES = 8;
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final VenueRepository venueRepo;
    private final PerformanceRepository performanceRepo;
    private final OpenAiClient ai;
    private final AiQuota quota;
    private final Clock clock;

    /** 같은 조건(지역·테마·날짜)의 결과를 잠시 재사용한다. 비용 0으로 반복 요청을 받는다. */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(Preview preview, Instant expiresAt) {}

    /** 후보 한 곳. key 는 AI에게 보여주는 짧은 ID(v123 = 장소, p45 = 행사). */
    record Candidate(String key, String targetType, Long targetId, String name, String category,
                     String imageUrl, Double lat, Double lng) {}

    @Autowired
    public AiRouteService(VenueRepository venueRepo, PerformanceRepository performanceRepo, OpenAiClient ai,
                          @Value("${byeori.ai.daily-limit-per-user:10}") int perUser,
                          @Value("${byeori.ai.daily-limit-total:300}") int total) {
        this(venueRepo, performanceRepo, ai, new AiQuota(perUser, total, Clock.system(KST)), Clock.system(KST));
    }

    AiRouteService(VenueRepository venueRepo, PerformanceRepository performanceRepo, OpenAiClient ai,
                   AiQuota quota, Clock clock) {
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
        this.ai = ai;
        this.quota = quota;
        this.clock = clock;
    }

    public Status status(Long userId) {
        if (!ai.enabled()) return new Status(false, null);
        return new Status(true, userId == null ? null : quota.remaining(userId));
    }

    public Preview generate(Long userId, GenerateRequest req) {
        if (!ai.enabled()) {
            throw new BadRequestException("AI_DISABLED", "AI 루트 만들기를 지금은 쓸 수 없어요.");
        }
        Condition c = validate(req);

        String cacheKey = c.cacheKey();
        if (!Boolean.TRUE.equals(req.regenerate())) {
            Cached hit = cache.get(cacheKey);
            if (hit != null && hit.expiresAt().isAfter(Instant.now(clock))) {
                return withRemaining(hit.preview(), quota.remaining(userId));
            }
        }

        // 후보가 모자라면 AI를 부르지 않는다(비용도, 한도도 쓰지 않는다)
        List<Candidate> candidates = candidates(c);
        if (candidates.size() < MIN_STOPS) {
            throw new BadRequestException("AI_NOT_ENOUGH_PLACES",
                    "이 지역에는 고른 테마의 장소가 부족해요. 테마를 더 고르거나 다른 지역을 선택해 주세요.");
        }

        if (!quota.tryAcquire(userId)) {
            throw new BadRequestException("AI_QUOTA_EXCEEDED", quota.totalExhausted()
                    ? "오늘 준비된 AI 생성량이 모두 소진됐어요. 내일 다시 이용해 주세요."
                    : "오늘 만들 수 있는 AI 루트를 모두 사용했어요. 내일 다시 이용해 주세요.");
        }

        JsonNode answer = ai.completeJson(systemPrompt(), userPrompt(c, candidates), "day_route", schema());
        Preview preview = answer == null ? null : toPreview(answer, candidates, c.date());
        if (preview == null) {
            quota.refund(userId);
            throw new BadRequestException("AI_FAILED", "루트를 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
        }

        evictExpired();
        cache.put(cacheKey, new Cached(preview, Instant.now(clock).plus(CACHE_TTL)));
        return withRemaining(preview, quota.remaining(userId));
    }

    // ── 조건 검증 ─────────────────────────────────────────

    record Condition(double lat, double lng, String areaName, List<String> categories, LocalDate date) {
        /** 좌표는 약 100m 단위로 묶는다. 같은 지역 칩이면 같은 키가 된다. */
        String cacheKey() {
            return String.format("%.3f,%.3f|%s|%s", lat, lng, String.join(",", categories), date);
        }
    }

    Condition validate(GenerateRequest req) {
        if (req == null || req.lat() == null || req.lng() == null) {
            throw new BadRequestException("AI_INVALID", "지역을 선택해 주세요.");
        }
        // 대한민국 범위 밖 좌표는 후보가 없을 뿐 아니라 잘못된 요청이다
        if (req.lat() < 33 || req.lat() > 39 || req.lng() < 124 || req.lng() > 132) {
            throw new BadRequestException("AI_INVALID", "국내 지역만 선택할 수 있어요.");
        }
        List<String> cats = req.categories() == null ? List.of()
                : new ArrayList<>(new TreeSet<>(req.categories().stream().filter(CATEGORIES::contains).toList()));
        if (cats.isEmpty()) {
            throw new BadRequestException("AI_INVALID", "테마를 하나 이상 골라 주세요.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate date = req.date() == null ? today : req.date();
        if (date.isBefore(today) || date.isAfter(today.plusDays(90))) {
            throw new BadRequestException("AI_INVALID", "날짜는 오늘부터 90일 안에서 골라 주세요.");
        }
        String area = req.areaName() == null || req.areaName().isBlank() ? "선택한 지역" : req.areaName().strip();
        if (area.length() > 30) area = area.substring(0, 30);
        return new Condition(req.lat(), req.lng(), area, cats, date);
    }

    // ── 후보 ─────────────────────────────────────────────

    List<Candidate> candidates(Condition c) {
        double dLat = RADIUS_M / 111_000.0;
        double dLng = RADIUS_M / (111_000.0 * Math.cos(Math.toRadians(c.lat())));
        BigDecimal minLat = BigDecimal.valueOf(c.lat() - dLat), maxLat = BigDecimal.valueOf(c.lat() + dLat);
        BigDecimal minLng = BigDecimal.valueOf(c.lng() - dLng), maxLng = BigDecimal.valueOf(c.lng() + dLng);

        List<Candidate> out = new ArrayList<>();
        // 테마별로 나눠 뽑는다. 한꺼번에 뽑으면 수가 많은 문화·맛집이 공예·전통시장을 밀어낸다.
        int per = (int) Math.ceil((double) VENUE_CANDIDATES / c.categories().size());
        for (String cat : c.categories()) {
            for (Venue v : venueRepo.sampleForRoute(minLat, maxLat, minLng, maxLng, cat, per)) {
                out.add(new Candidate("v" + v.getId(), "VENUE", v.getId(), v.getName(), v.getCategory(),
                        v.getImageUrl(), num(v.getLat()), num(v.getLng())));
            }
        }
        for (Performance p : performanceRepo.findOnDateInBounds(c.date(), minLat, maxLat, minLng, maxLng,
                PageRequest.of(0, EVENT_CANDIDATES))) {
            out.add(new Candidate("p" + p.getId(), "PERFORMANCE", p.getId(), p.getTitle(),
                    p.isTraditional() ? "전통 행사" : "행사", p.getPosterImageUrl(), num(p.getLat()), num(p.getLng())));
        }
        return out;
    }

    private static Double num(BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }

    // ── 프롬프트 ─────────────────────────────────────────

    private static String systemPrompt() {
        return """
                너는 한국 전통문화 여행 서비스 '벼리'의 하루 여행 코스 설계자다.
                규칙:
                - 반드시 사용자가 준 후보 목록 안에서만 고른다. 목록에 없는 장소를 만들지 않는다.
                - 4~6곳을 고른다. 좌표를 보고 이동이 짧아지도록 순서를 정한다.
                - 하루 흐름을 지킨다: 오전은 관람·체험, 점심 무렵 맛집, 오후 카페·시장 순이 자연스럽다.
                - 맛집은 최대 2곳, 카페는 최대 1곳. 행사가 있으면 어울릴 때 1곳까지 넣는다.
                - time 은 방문 시작 시각(HH:mm, 09:00~20:00).
                - reason 은 왜 이 순서에 이곳인지 한국어 한 문장, 40자 이내.
                  운영시간·가격·전화번호처럼 목록에 없는 사실은 쓰지 않는다.
                - title 은 20자 이내, summary 는 코스 전체를 소개하는 한 문장 80자 이내.
                """;
    }

    private static String userPrompt(Condition c, List<Candidate> candidates) {
        StringBuilder sb = new StringBuilder()
                .append("지역: ").append(c.areaName()).append('\n')
                .append("날짜: ").append(c.date()).append('\n')
                .append("원하는 테마: ").append(String.join(", ", c.categories())).append('\n')
                .append("후보 (ID | 이름 | 분류 | 위도,경도):\n");
        for (Candidate k : candidates) {
            sb.append(k.key()).append(" | ").append(k.name()).append(" | ").append(k.category()).append(" | ")
                    .append(k.lat() == null ? "-" : String.format("%.4f,%.4f", k.lat(), k.lng())).append('\n');
        }
        return sb.toString();
    }

    static Map<String, Object> schema() {
        Map<String, Object> stop = obj(Map.of(
                "id", Map.of("type", "string"),
                "time", Map.of("type", "string"),
                "reason", Map.of("type", "string")));
        return obj(Map.of(
                "title", Map.of("type", "string"),
                "summary", Map.of("type", "string"),
                "stops", Map.of("type", "array", "items", stop)));
    }

    /** strict 모드: 모든 필드 required, 추가 필드 금지. */
    private static Map<String, Object> obj(Map<String, Object> props) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props);
        m.put("required", List.copyOf(props.keySet()));
        m.put("additionalProperties", false);
        return m;
    }

    // ── 응답 검증 ─────────────────────────────────────────

    /** AI 응답을 후보와 대조한다. 쓸 수 있는 곳이 MIN_STOPS 미만이면 null. */
    static Preview toPreview(JsonNode answer, List<Candidate> candidates, LocalDate date) {
        Map<String, Candidate> byKey = new LinkedHashMap<>();
        for (Candidate k : candidates) byKey.put(k.key(), k);

        List<Stop> stops = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode s : answer.path("stops")) {
            String key = s.path("id").asText("").strip();
            Candidate k = byKey.get(key);
            if (k == null || !seen.add(key)) continue; // 지어낸 ID·중복은 버린다
            String time = s.path("time").asText("").strip();
            stops.add(new Stop(k.targetType(), k.targetId(), k.name(), k.category(), k.imageUrl(),
                    k.lat(), k.lng(), TIME.matcher(time).matches() ? time : null,
                    clip(s.path("reason").asText(""), 60)));
            if (stops.size() == MAX_STOPS) break;
        }
        if (stops.size() < MIN_STOPS) return null;

        String title = clip(answer.path("title").asText(""), 30);
        return new Preview(title.isEmpty() ? "AI 추천 하루 코스" : title,
                clip(answer.path("summary").asText(""), 120), date, stops, 0);
    }

    private static String clip(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static Preview withRemaining(Preview p, int remaining) {
        return new Preview(p.title(), p.summary(), p.date(), p.stops(), remaining);
    }

    private void evictExpired() {
        if (cache.size() < 500) return;
        Instant now = Instant.now(clock);
        cache.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
    }
}
