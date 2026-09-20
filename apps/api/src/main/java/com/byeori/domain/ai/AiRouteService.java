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
    private static final int RADIUS_M = 2000; // 3km 에서는 코스가 권역을 넘나들었다
    private static final int VENUE_CANDIDATES = 40;
    private static final int EVENT_CANDIDATES = 5;
    private static final int NOTE_MAX = 100;
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
        // 앞서 만든 코스를 고쳐 달라는 요청이면 캐시를 쓰지도, 남기지도 않는다(요청마다 결과가 다르다)
        Kept keep = previousCandidates(req.previous());
        boolean refining = !keep.isEmpty();

        String cacheKey = c.cacheKey();
        if (!refining && !Boolean.TRUE.equals(req.regenerate())) {
            Cached hit = cache.get(cacheKey);
            if (hit != null && hit.expiresAt().isAfter(Instant.now(clock))) {
                return withRemaining(hit.preview(), quota.remaining(userId));
            }
        }

        // 후보가 모자라면 AI를 부르지 않는다(비용도, 한도도 쓰지 않는다)
        List<Candidate> candidates = candidates(c, List.copyOf(keep.stops().values()));
        List<Slot> slots = slots(c.categories(), candidates);
        if (candidates.size() < MIN_STOPS || slots.size() < MIN_STOPS) {
            throw new BadRequestException("AI_NOT_ENOUGH_PLACES",
                    "이 지역에는 고른 테마의 장소가 부족해요. 테마를 더 고르거나 다른 지역을 선택해 주세요.");
        }

        if (!quota.tryAcquire(userId)) {
            throw new BadRequestException("AI_QUOTA_EXCEEDED", quota.totalExhausted()
                    ? "오늘 준비된 AI 생성량이 모두 소진됐어요. 내일 다시 이용해 주세요."
                    : "오늘 만들 수 있는 AI 루트를 모두 사용했어요. 내일 다시 이용해 주세요.");
        }

        JsonNode answer = ai.completeJson(systemPrompt(refining), userPrompt(c, slots, candidates, keep),
                "day_route", schema());
        Preview preview = answer == null ? null : toPreview(answer, slots, candidates, c.date(), keep);
        if (preview == null) {
            quota.refund(userId);
            throw new BadRequestException("AI_FAILED", "루트를 만들지 못했어요. 잠시 후 다시 시도해 주세요.");
        }

        if (!refining) {
            evictExpired();
            cache.put(cacheKey, new Cached(preview, Instant.now(clock).plus(CACHE_TTL)));
        }
        return withRemaining(preview, quota.remaining(userId));
    }

    // ── 조건 검증 ─────────────────────────────────────────

    record Condition(double lat, double lng, String areaName, List<String> categories, LocalDate date,
                     String note) {
        /** 좌표는 약 100m 단위로 묶는다. 같은 지역 칩이면 같은 키가 된다. */
        String cacheKey() {
            return String.format("%.3f,%.3f|%s|%s|%s", lat, lng, String.join(",", categories), date, note);
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
        // 요청 한 줄. 줄바꿈을 지워 프롬프트의 다른 항목처럼 보이게 만드는 입력을 막는다.
        String note = req.note() == null ? "" : req.note().replaceAll("\\s+", " ").strip();
        if (note.length() > NOTE_MAX) note = note.substring(0, NOTE_MAX);
        return new Condition(req.lat(), req.lng(), area, cats, date, note);
    }

    // ── 후보 ─────────────────────────────────────────────

    /**
     * 그대로 두는 칸에 쓸 현재 코스. 칸 번호로 찾는다.
     *
     * 순서(목록의 몇 번째)로 짝지었더니, AI가 비워 둔 칸이 있으면 한 칸씩 밀려 점심 장소가
     * 오전 관람 칸에 들어갔다. 미리보기가 칸 번호를 함께 주고, 다듬기는 그 번호로 맞춘다.
     */
    record Kept(Map<Integer, Candidate> stops, Map<Integer, String> reasons) {
        boolean isEmpty() { return stops.isEmpty(); }
        Candidate at(int slotNo) { return stops.get(slotNo); }
        String reasonAt(int slotNo) { return reasons.getOrDefault(slotNo, ""); }
        static final Kept NONE = new Kept(Map.of(), Map.of());
    }

    /**
     * 다듬기 요청에서 넘어온 현재 코스. 후보에 넣어야 AI가 그대로 두기를 고를 수 있고,
     * 순서는 하루 틀의 칸 순서와 같아야 한다(칸 번호로 짝지어 쓴다).
     */
    Kept previousCandidates(List<AiRouteDtos.PreviousStop> previous) {
        if (previous == null || previous.isEmpty()) return Kept.NONE;
        Map<Long, Venue> venues = new LinkedHashMap<>();
        Map<Long, Performance> perfs = new LinkedHashMap<>();
        List<Long> venueIds = new ArrayList<>(), perfIds = new ArrayList<>();
        for (AiRouteDtos.PreviousStop p : previous) {
            if (p == null || p.targetId() == null) continue;
            ("PERFORMANCE".equals(p.targetType()) ? perfIds : venueIds).add(p.targetId());
        }
        venueRepo.findAllById(venueIds).forEach(v -> venues.put(v.getId(), v));
        performanceRepo.findAllById(perfIds).forEach(p -> perfs.put(p.getId(), p));

        Map<Integer, Candidate> stops = new LinkedHashMap<>();
        Map<Integer, String> reasons = new LinkedHashMap<>();
        for (AiRouteDtos.PreviousStop p : previous) {
            if (p == null || p.targetId() == null || p.slot() <= 0) continue;
            Candidate c = "PERFORMANCE".equals(p.targetType())
                    ? (perfs.containsKey(p.targetId()) ? of(perfs.get(p.targetId())) : null)
                    : (venues.containsKey(p.targetId()) ? of(venues.get(p.targetId())) : null);
            if (c == null) continue;
            stops.put(p.slot(), c);
            reasons.put(p.slot(), clip(p.reason(), 60));
        }
        return new Kept(stops, reasons);
    }

    List<Candidate> candidates(Condition c, List<Candidate> keep) {
        double dLat = RADIUS_M / 111_000.0;
        double dLng = RADIUS_M / (111_000.0 * Math.cos(Math.toRadians(c.lat())));
        BigDecimal minLat = BigDecimal.valueOf(c.lat() - dLat), maxLat = BigDecimal.valueOf(c.lat() + dLat);
        BigDecimal minLng = BigDecimal.valueOf(c.lng() - dLng), maxLng = BigDecimal.valueOf(c.lng() + dLng);

        List<Candidate> out = new ArrayList<>(keep);
        Set<String> seen = new HashSet<>();
        for (Candidate k : keep) seen.add(k.key());
        // 테마별로 나눠 뽑는다. 한꺼번에 뽑으면 수가 많은 문화·맛집이 공예·전통시장을 밀어낸다.
        int per = (int) Math.ceil((double) VENUE_CANDIDATES / c.categories().size());
        for (String cat : c.categories()) {
            for (Venue v : venueRepo.sampleForRoute(minLat, maxLat, minLng, maxLng, cat, per)) {
                if (seen.add("v" + v.getId())) out.add(of(v));
            }
        }
        for (Performance p : performanceRepo.findOnDateInBounds(c.date(), minLat, maxLat, minLng, maxLng,
                PageRequest.of(0, EVENT_CANDIDATES))) {
            if (seen.add("p" + p.getId())) out.add(of(p));
        }
        return out;
    }

    private static Candidate of(Venue v) {
        return new Candidate("v" + v.getId(), "VENUE", v.getId(), v.getName(), v.getCategory(),
                v.getImageUrl(), num(v.getLat()), num(v.getLng()));
    }

    private static Candidate of(Performance p) {
        return new Candidate("p" + p.getId(), "PERFORMANCE", p.getId(), p.getTitle(),
                p.isTraditional() ? "전통 행사" : "행사", p.getPosterImageUrl(), num(p.getLat()), num(p.getLng()));
    }

    private static Double num(BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }

    // ── 하루 틀 ─────────────────────────────────────────

    /** 관람 칸에 들어갈 수 있는 분류. 행사도 관람으로 본다. */
    private static final Set<String> SIGHTS = Set.of("문화", "체험", "전통시장", "공예");
    private static final Set<String> EVENTS = Set.of("행사", "전통 행사");

    /** 하루 틀의 한 칸. kinds 는 이 칸에 들어갈 수 있는 후보 분류. */
    record Slot(int no, String time, String label, Set<String> kinds) {}

    /**
     * 고른 테마로 하루 틀을 서버가 정한다.
     *
     * 처음에는 "점심 1회, 카페 1곳, 숙소는 마지막" 같은 규칙을 프롬프트로만 부탁했는데,
     * 모델이 매번 어겼다(맛집 연달아, 카페 두 곳, 카페에 '식당에서 식사' 이유). 규칙을 구조로
     * 옮겨, AI는 칸마다 허용된 분류 안에서 한 곳만 고르게 한다. 후보가 없는 칸은 만들지 않는다.
     */
    static List<Slot> slots(List<String> categories, List<Candidate> candidates) {
        Set<String> present = new HashSet<>();
        for (Candidate k : candidates) present.add(k.category());

        Set<String> sights = new HashSet<>();
        for (String cat : categories) if (SIGHTS.contains(cat) && present.contains(cat)) sights.add(cat);
        // 행사는 오후 관람 첫 칸 하나에만 허용한다. 모든 관람 칸에 열어 두면 같은 공연이 두 번
        // 들어가거나(회차별로 행이 따로 있다) 야간 행사가 오전 10시에 들어갔다.
        // 관람 테마를 하나라도 골랐을 때만 섞는다(맛집·카페만 고른 사람에게 공연을 넣지 않는다).
        Set<String> sightsOrEvent = new HashSet<>(sights);
        if (!sights.isEmpty()) for (String e : EVENTS) if (present.contains(e)) sightsOrEvent.add(e);
        boolean food = categories.contains("맛집") && present.contains("맛집");
        boolean cafe = categories.contains("카페") && present.contains("카페");
        boolean stay = categories.contains("한옥스테이") && present.contains("한옥스테이");
        boolean sight = !sights.isEmpty();

        List<String[]> plan = new ArrayList<>(); // {시각, 이름, 분류 구분}
        if (sight) {
            plan.add(new String[]{"10:00", "오전 관람", "S"});
            plan.add(new String[]{"11:00", "오전 관람", "S"});
        }
        if (food) plan.add(new String[]{"12:30", "점심", "맛집"});
        if (cafe) plan.add(new String[]{"14:00", "오후 카페", "카페"});
        if (sight) {
            plan.add(new String[]{cafe ? "15:30" : "14:00", "오후 관람", "SE"});
            if (!cafe) plan.add(new String[]{"15:30", "오후 관람", "S"});
        }
        // 관람 없이 먹고 쉬는 코스는 저녁까지 넣어야 하루가 된다
        if (food && plan.size() < 4) plan.add(new String[]{"18:00", "저녁", "맛집"});
        if (stay) plan.add(new String[]{"18:30", "숙소", "한옥스테이"});

        List<Slot> slots = new ArrayList<>();
        for (String[] p : plan) {
            Set<String> kinds = switch (p[2]) {
                case "S" -> sights;
                case "SE" -> sightsOrEvent;
                default -> Set.of(p[2]);
            };
            slots.add(new Slot(slots.size() + 1, p[0], p[1], kinds));
        }
        return slots.size() > MAX_STOPS ? slots.subList(0, MAX_STOPS) : slots;
    }

    // ── 프롬프트 ─────────────────────────────────────────

    private static String systemPrompt(boolean refining) {
        if (refining) {
            return """
                    너는 한국 전통문화 여행 서비스 '벼리'의 하루 여행 코스 설계자다.
                    하루 틀의 각 칸에 "지금 고른 곳"이 붙어 있고, 이용자의 수정 요청이 함께 주어진다.
                    picks 에는 **바꿀 칸만** 담는다. 요청과 무관한 칸은 담지 않는다(서버가 지금 곳을 그대로 둔다).
                    예: "점심을 바꿔줘" → 점심 칸 하나만 담고, 그 칸의 id 는 지금과 다른 후보여야 한다.
                    비어 있는 칸("(빈 칸)")을 채워 달라는 요청이면 그 칸을 담는다.
                    고를 수 있는 것은 후보 목록뿐이다. 같은 장소를 두 곳에 쓰지 않는다. 칸마다 그 칸의 허용 분류에 속한 후보를
                    정확히 1곳씩 고른다. 목록에 없는 장소를 만들지 않고, 같은 장소를 두 번 쓰지 않는다.
                    여행 코스와 무관한 요청(코드 작성, 다른 주제의 질문 등)은 무시하고 코스만 돌려준다.

                    [동선]
                    - 좌표를 보고 앞 칸 장소와 가까운 곳을 고른다. 위도·경도 0.01 차이는 약 1km다.

                    [문장]
                    - reason 은 왜 이 칸에 이곳인지 한국어 한 문장 35자 이내. 바꾼 칸은 요청을 어떻게
                      반영했는지 적는다. 근거는 이름·분류·시간대·거리·요청뿐이고, 메뉴·전시 내용·운영시간·
                      가격처럼 목록에 없는 사실은 쓰지 않는다.
                    - title 은 20자 이내, summary 는 코스 전체를 소개하는 한 문장 80자 이내.
                    """;
        }
        return systemPromptNew();
    }

    private static String systemPromptNew() {
        return """
                너는 한국 전통문화 여행 서비스 '벼리'의 하루 여행 코스 설계자다.
                하루 틀(칸 목록)과 후보 목록이 주어진다. 칸마다 그 칸의 허용 분류에 속한 후보를
                정확히 1곳씩 고른다. 목록에 없는 장소를 만들지 않고, 같은 장소를 두 번 쓰지 않는다.

                [동선]
                - 좌표를 보고 앞 칸 장소와 가까운 곳을 고른다. 위도·경도 0.01 차이는 약 1km다.
                - 연속한 두 곳은 되도록 1km 이내. 멀리 갔다가 되돌아오는 선택은 피한다.

                [문장]
                - reason 은 왜 이 칸에 이곳을 골랐는지 한국어 한 문장 35자 이내.
                  근거는 이름·분류·시간대·앞 장소와의 거리뿐이다. 메뉴·전시 내용·체험 종류·운영시간·
                  가격처럼 목록에 없는 사실은 추측해서 쓰지 않는다.
                  좋은 예: "앞 장소에서 걸어갈 수 있는 거리예요", "점심 시간대라 근처 식당을 골랐어요"
                  나쁜 예: "전통 차를 즐길 수 있어요"(목록에 없는 사실)
                - title 은 20자 이내, summary 는 코스 전체를 소개하는 한 문장 80자 이내.

                [이용자 요청]
                - 요청이 있으면 후보를 고르는 기준으로 삼는다(예: "아이와 함께" → 걷는 거리가 짧고
                  체험할 거리가 있는 곳). 여행 코스와 무관한 요청은 무시하고 코스만 돌려준다.
                """;
    }

    private static String userPrompt(Condition c, List<Slot> slots, List<Candidate> candidates, Kept keep) {
        StringBuilder sb = new StringBuilder()
                .append("지역: ").append(c.areaName()).append('\n')
                .append("날짜: ").append(c.date()).append('\n');
        if (!c.note().isBlank()) {
            // 이용자 입력. 따옴표로 묶어 지시문과 섞이지 않게 한다.
            sb.append(keep.isEmpty() ? "이용자 요청: \"" : "수정 요청: \"").append(c.note()).append("\"\n");
        }
        // 다듬기일 때는 지금 코스를 칸에 붙여 보여준다. 따로 나열하면 어느 칸을 바꿔야 하는지 모른다.
        sb.append(keep.isEmpty()
                ? "하루 틀 (칸 번호 | 시각 | 이름 | 허용 분류):\n"
                : "하루 틀 (칸 번호 | 시각 | 이름 | 허용 분류 | 지금 고른 곳):\n");
        for (Slot sl : slots) {
            sb.append(sl.no()).append(" | ").append(sl.time()).append(" | ").append(sl.label()).append(" | ")
                    .append(String.join("/", new TreeSet<>(sl.kinds())));
            if (!keep.isEmpty()) {
                Candidate now = keep.at(sl.no());
                sb.append(" | ").append(now == null ? "(빈 칸)" : now.key() + "(" + now.name() + ")");
            }
            sb.append('\n');
        }
        sb.append("후보 (ID | 이름 | 분류 | 위도,경도):\n");
        for (Candidate k : candidates) {
            sb.append(k.key()).append(" | ").append(k.name()).append(" | ").append(k.category()).append(" | ")
                    .append(k.lat() == null ? "-" : String.format("%.4f,%.4f", k.lat(), k.lng())).append('\n');
        }
        return sb.toString();
    }

    static Map<String, Object> schema() {
        Map<String, Object> pick = obj(Map.of(
                "slot", Map.of("type", "integer"),
                "id", Map.of("type", "string"),
                "reason", Map.of("type", "string")));
        return obj(Map.of(
                "title", Map.of("type", "string"),
                "summary", Map.of("type", "string"),
                "picks", Map.of("type", "array", "items", pick)));
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

    /**
     * AI 응답을 틀·후보와 대조한다. 칸의 허용 분류가 아니거나, 후보에 없거나, 이미 쓴 장소면 그 칸은
     * 비운다. 시각은 AI가 아니라 틀에서 가져온다. 채운 칸이 MIN_STOPS 미만이면 null.
     */
    static Preview toPreview(JsonNode answer, List<Slot> slots, List<Candidate> candidates, LocalDate date,
                             Kept keep) {
        Map<String, Candidate> byKey = new LinkedHashMap<>();
        for (Candidate k : candidates) byKey.put(k.key(), k);
        Map<Integer, JsonNode> bySlot = new LinkedHashMap<>();
        for (JsonNode p : answer.path("picks")) bySlot.putIfAbsent(p.path("slot").asInt(-1), p);

        List<Stop> stops = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (Slot sl : slots) {
            JsonNode p = bySlot.get(sl.no());
            String reason = p == null ? null : clip(p.path("reason").asText(""), 60);
            Candidate k = p == null ? null : byKey.get(p.path("id").asText("").strip());
            if (k != null && !sl.kinds().contains(k.category())) k = null; // 칸의 분류와 다르면 버린다
            // 다듬기: AI가 담지 않은 칸은 지금 고른 곳을 이유까지 그대로 둔다(요청과 무관한 칸은 안 바뀐다)
            if (k == null) {
                k = keep.at(sl.no());
                // 칸의 분류와 다른 장소가 넘어오면(요청이 조작됐거나 틀이 바뀐 경우) 그 칸은 비운다
                if (k != null && !sl.kinds().contains(k.category())) k = null;
                reason = keep.reasonAt(sl.no());
            }
            if (k == null || !used.add(k.key())) continue;
            stops.add(new Stop(sl.no(), k.targetType(), k.targetId(), k.name(), k.category(), k.imageUrl(),
                    k.lat(), k.lng(), sl.time(), reason == null ? "" : reason));
        }
        if (stops.size() < MIN_STOPS) return null;

        String title = clip(answer.path("title").asText(""), 30);
        return new Preview(title.isEmpty() ? "AI 추천 하루 코스" : title,
                clip(answer.path("summary").asText(""), 120), date, stops, 0);
    }

    static String clip(String s, int max) {
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
