package com.byeori.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.byeori.domain.ai.AiRouteDtos.GenerateRequest;
import com.byeori.domain.ai.AiRouteDtos.Preview;
import com.byeori.domain.ai.AiRouteService.Candidate;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.external.OpenAiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** AI 루트 — 지어낸 장소를 거르고, 비용 상한과 캐시가 동작하는지. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiRouteServiceTest {

    static final ObjectMapper OM = new ObjectMapper();
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    static final LocalDate TODAY = LocalDate.now(CLOCK);

    @Mock VenueRepository venueRepo;
    @Mock PerformanceRepository performanceRepo;
    @Mock OpenAiClient ai;

    AiRouteService service;

    @BeforeEach
    void setUp() {
        service = new AiRouteService(venueRepo, performanceRepo, ai, new AiQuota(2, 100, CLOCK), CLOCK);
        when(ai.enabled()).thenReturn(true);
        List<Venue> venues = List.of(venue(1), venue(2), venue(3), venue(4)); // 스텁 안에서 목을 만들면 Mockito가 꼬인다
        when(venueRepo.sampleForRoute(any(), any(), any(), any(), anyString(), anyInt())).thenReturn(venues);
        when(performanceRepo.findOnDateInBounds(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    static Venue venue(long id) {
        Venue v = org.mockito.Mockito.mock(Venue.class);
        when(v.getId()).thenReturn(id);
        when(v.getName()).thenReturn("장소" + id);
        when(v.getCategory()).thenReturn("문화");
        when(v.getLat()).thenReturn(BigDecimal.valueOf(37.57));
        when(v.getLng()).thenReturn(BigDecimal.valueOf(126.98));
        return v;
    }

    static List<Candidate> candidates(int n) {
        return java.util.stream.IntStream.rangeClosed(1, n)
                .mapToObj(i -> new Candidate("v" + i, "VENUE", (long) i, "장소" + i, "문화", null, 37.5, 127.0))
                .toList();
    }

    /** ids[i] 를 i+1 번 칸에 고른 응답. */
    static JsonNode answer(String... ids) {
        var picks = OM.createArrayNode();
        for (int i = 0; i < ids.length; i++) picks.addObject().put("slot", i + 1).put("id", ids[i]).put("reason", "가까워요");
        var root = OM.createObjectNode().put("title", "종로 하루").put("summary", "요약");
        root.set("picks", picks);
        return root;
    }

    static final AiRouteService.Kept NONE = AiRouteService.Kept.NONE;

    static final List<AiRouteService.Slot> SIGHT_SLOTS = java.util.stream.IntStream.rangeClosed(1, 6)
            .mapToObj(i -> new AiRouteService.Slot(i, "1" + i + ":00", "관람", java.util.Set.of("문화")))
            .toList();

    static GenerateRequest req(boolean regenerate) {
        return new GenerateRequest(37.57, 126.98, "종로구", List.of("문화"), TODAY, regenerate, null, null);
    }

    static GenerateRequest reqNote(String note) {
        return new GenerateRequest(37.57, 126.98, "종로구", List.of("문화"), TODAY, false, note, null);
    }

    @Test
    void 후보에_없는_ID와_중복은_칸을_비운다() {
        Preview p = AiRouteService.toPreview(answer("v1", "v99", "v2", "v1", "p7", "v3"), SIGHT_SLOTS, candidates(5), TODAY, NONE);

        assertThat(p.stops()).extracting(s -> s.targetId()).containsExactly(1L, 2L, 3L);
        assertThat(p.stops().get(0).name()).isEqualTo("장소1"); // 이름은 AI가 아니라 우리 DB 값
        assertThat(p.stops().get(1).time()).isEqualTo("13:00"); // 시각은 AI가 아니라 틀에서
    }

    @Test
    void 쓸_수_있는_곳이_3곳_미만이면_실패() {
        assertThat(AiRouteService.toPreview(answer("v1", "v2", "v404"), SIGHT_SLOTS, candidates(5), TODAY, NONE)).isNull();
    }

    @Test
    void 칸의_분류와_다른_장소는_버린다() {
        var slots = List.of(
                new AiRouteService.Slot(1, "10:00", "관람", java.util.Set.of("문화")),
                new AiRouteService.Slot(2, "12:30", "점심", java.util.Set.of("맛집")),
                new AiRouteService.Slot(3, "14:00", "카페", java.util.Set.of("카페")),
                new AiRouteService.Slot(4, "15:30", "관람", java.util.Set.of("문화")));
        var cands = List.of(
                new Candidate("v1", "VENUE", 1L, "궁", "문화", null, 37.5, 127.0),
                new Candidate("v2", "VENUE", 2L, "식당", "맛집", null, 37.5, 127.0),
                new Candidate("v3", "VENUE", 3L, "찻집", "카페", null, 37.5, 127.0),
                new Candidate("v4", "VENUE", 4L, "박물관", "문화", null, 37.5, 127.0));

        // 카페 칸에 식당을 넣으면 그 칸만 빈다
        Preview p = AiRouteService.toPreview(answer("v1", "v2", "v2", "v4"), slots, cands, TODAY, NONE);

        assertThat(p.stops()).extracting(s -> s.name()).containsExactly("궁", "식당", "박물관");
    }

    @Test
    void 하루_틀은_점심_카페_숙소를_제자리에_둔다() {
        var cands = List.of(
                new Candidate("v1", "VENUE", 1L, "a", "문화", null, 37.5, 127.0),
                new Candidate("v2", "VENUE", 2L, "b", "맛집", null, 37.5, 127.0),
                new Candidate("v3", "VENUE", 3L, "c", "카페", null, 37.5, 127.0),
                new Candidate("v4", "VENUE", 4L, "d", "한옥스테이", null, 37.5, 127.0),
                new Candidate("p5", "PERFORMANCE", 5L, "e", "전통 행사", null, 37.5, 127.0));

        var slots = AiRouteService.slots(List.of("문화", "맛집", "카페", "한옥스테이"), cands);

        assertThat(slots).extracting(AiRouteService.Slot::label)
                .containsExactly("오전 관람", "오전 관람", "점심", "오후 카페", "오후 관람", "숙소");
        assertThat(slots.get(0).kinds()).containsExactly("문화");           // 오전엔 행사 없음
        assertThat(slots.get(4).kinds()).contains("문화", "전통 행사");     // 행사는 오후 첫 관람 칸에만
        assertThat(slots.get(2).kinds()).containsExactly("맛집");
    }

    @Test
    void 맛집_카페만_고르면_행사를_섞지_않고_저녁까지_넣는다() {
        var cands = List.of(
                new Candidate("v2", "VENUE", 2L, "b", "맛집", null, 37.5, 127.0),
                new Candidate("v3", "VENUE", 3L, "c", "카페", null, 37.5, 127.0),
                new Candidate("p5", "PERFORMANCE", 5L, "e", "전통 행사", null, 37.5, 127.0));

        var slots = AiRouteService.slots(List.of("맛집", "카페"), cands);

        assertThat(slots).extracting(AiRouteService.Slot::label).containsExactly("점심", "오후 카페", "저녁");
    }

    @Test
    void 같은_조건은_캐시로_응답하고_다시_만들기는_새로_부른다() {
        when(ai.completeJson(anyString(), anyString(), eq("day_route"), any())).thenReturn(answer("v1", "v2", "v3"));

        service.generate(7L, req(false));
        service.generate(7L, req(false));      // 캐시 적중 — 호출 없음, 한도 차감 없음
        Preview third = service.generate(7L, req(true));

        verify(ai, times(2)).completeJson(anyString(), anyString(), eq("day_route"), any());
        assertThat(third.remainingToday()).isZero(); // 사용자 한도 2회를 다 씀
    }

    @Test
    void 하루_한도를_넘으면_거절한다() {
        when(ai.completeJson(anyString(), anyString(), eq("day_route"), any())).thenReturn(answer("v1", "v2", "v3"));
        service.generate(7L, req(true));
        service.generate(7L, req(true));

        assertThatThrownBy(() -> service.generate(7L, req(true)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("AI_QUOTA_EXCEEDED");
    }

    @Test
    void AI가_실패하면_한도를_돌려준다() {
        when(ai.completeJson(anyString(), anyString(), eq("day_route"), any())).thenReturn(null);

        assertThatThrownBy(() -> service.generate(7L, req(true))).isInstanceOf(BadRequestException.class);

        assertThat(service.status(7L).remainingToday()).isEqualTo(2);
    }

    @Test
    void 후보가_모자라면_AI를_부르지_않는다() {
        List<Venue> one = List.of(venue(1));
        when(venueRepo.sampleForRoute(any(), any(), any(), any(), anyString(), anyInt())).thenReturn(one);

        assertThatThrownBy(() -> service.generate(7L, req(true)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("AI_NOT_ENOUGH_PLACES");
        verify(ai, never()).completeJson(anyString(), anyString(), anyString(), any());
    }

    @Test
    void 지난_날짜와_모르는_테마는_거절한다() {
        assertThatThrownBy(() -> service.validate(
                new GenerateRequest(37.57, 126.98, null, List.of("문화"), TODAY.minusDays(1), false, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.validate(
                new GenerateRequest(37.57, 126.98, null, List.of("해킹"), TODAY, false, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 요청_한_줄은_줄바꿈을_지우고_100자로_자른다() {
        var c = service.validate(reqNote("  아이와 함께\n많이 걷지 않게  " + "가".repeat(200)));

        assertThat(c.note()).startsWith("아이와 함께 많이 걷지 않게 가");
        assertThat(c.note()).hasSize(100);
        assertThat(c.note()).doesNotContain("\n");
    }

    @Test
    void 요청이_다르면_캐시를_나눠_쓴다() {
        when(ai.completeJson(anyString(), anyString(), eq("day_route"), any())).thenReturn(answer("v1", "v2", "v3"));

        service.generate(7L, reqNote("아이와 함께"));
        service.generate(7L, reqNote("아이와 함께"));   // 캐시 적중
        service.generate(7L, reqNote("혼자 조용히"));   // 다른 요청 → 새로 만든다

        verify(ai, times(2)).completeJson(anyString(), anyString(), eq("day_route"), any());
    }

    @Test
    void 다듬기_요청은_지금_코스를_후보에_넣고_캐시를_쓰지_않는다() {
        var prev = List.of(new AiRouteDtos.PreviousStop(1, "VENUE", 900L, "앞 장소와 가까워요"));
        Venue kept = venue(900);
        when(venueRepo.findAllById(List.of(900L))).thenReturn(List.of(kept));
        when(ai.completeJson(anyString(), anyString(), eq("day_route"), any())).thenReturn(answer("v900", "v1", "v2"));

        var r1 = new GenerateRequest(37.57, 126.98, "종로구", List.of("문화"), TODAY, false, "2번을 바꿔줘", prev);
        Preview p = service.generate(7L, r1);
        service.generate(7L, r1);   // 다듬기는 매번 새로 만든다(캐시 없음)

        assertThat(p.stops()).extracting(s -> s.targetId()).contains(900L); // 그대로 둔 곳
        verify(ai, times(2)).completeJson(anyString(), anyString(), eq("day_route"), any());
    }

    @Test
    void 다듬기에서_AI가_담지_않은_칸은_이유까지_그대로_둔다() {
        var slots = List.of(
                new AiRouteService.Slot(1, "10:00", "관람", java.util.Set.of("문화")),
                new AiRouteService.Slot(2, "12:30", "점심", java.util.Set.of("맛집")),
                new AiRouteService.Slot(3, "14:00", "관람", java.util.Set.of("문화")));
        var cands = List.of(
                new Candidate("v1", "VENUE", 1L, "궁", "문화", null, 37.5, 127.0),
                new Candidate("v2", "VENUE", 2L, "먼저 고른 식당", "맛집", null, 37.5, 127.0),
                new Candidate("v3", "VENUE", 3L, "새 식당", "맛집", null, 37.5, 127.0),
                new Candidate("v4", "VENUE", 4L, "박물관", "문화", null, 37.5, 127.0));
        var kept = new AiRouteService.Kept(
                java.util.Map.of(1, cands.get(0), 2, cands.get(1), 3, cands.get(3)),
                java.util.Map.of(1, "첫 이유", 2, "둘째 이유", 3, "셋째 이유"));
        // AI는 점심 칸(2번)만 바꿔 담았다
        var a = OM.createObjectNode().put("title", "고친 코스").put("summary", "요약");
        a.putArray("picks").addObject().put("slot", 2).put("id", "v3").put("reason", "요청대로 바꿨어요");

        Preview p = AiRouteService.toPreview(a, slots, cands, TODAY, kept);

        assertThat(p.stops()).extracting(s -> s.name()).containsExactly("궁", "새 식당", "박물관");
        assertThat(p.stops().get(0).reason()).isEqualTo("첫 이유");     // 그대로 둔 칸은 이유도 유지
        assertThat(p.stops().get(1).reason()).isEqualTo("요청대로 바꿨어요");
        assertThat(p.stops().get(2).reason()).isEqualTo("셋째 이유");
    }

    @Test
    void 칸이_비어_밀려도_칸_번호로_짝지어_분류가_섞이지_않는다() {
        var slots = List.of(
                new AiRouteService.Slot(1, "10:00", "관람", java.util.Set.of("문화")),
                new AiRouteService.Slot(2, "12:30", "점심", java.util.Set.of("맛집")),
                new AiRouteService.Slot(3, "14:00", "관람", java.util.Set.of("문화")));
        var cands = List.of(
                new Candidate("v1", "VENUE", 1L, "궁", "문화", null, 37.5, 127.0),
                new Candidate("v2", "VENUE", 2L, "식당", "맛집", null, 37.5, 127.0),
                new Candidate("v3", "VENUE", 3L, "박물관", "문화", null, 37.5, 127.0));
        // 2·3번 칸만 채워져 있던 코스를 다듬는다 — 1번 칸으로 밀려 들어가면 안 된다
        var kept = new AiRouteService.Kept(java.util.Map.of(2, cands.get(1), 3, cands.get(2)),
                java.util.Map.of(2, "점심 이유", 3, "관람 이유"));
        var a = OM.createObjectNode().put("title", "t").put("summary", "s");
        a.putArray("picks").addObject().put("slot", 1).put("id", "v1").put("reason", "관람");

        Preview p = AiRouteService.toPreview(a, slots, cands, TODAY, kept);

        assertThat(p.stops()).extracting(s -> s.slot()).containsExactly(1, 2, 3);
        assertThat(p.stops()).extracting(s -> s.name()).containsExactly("궁", "식당", "박물관");
        assertThat(p.stops().get(1).time()).isEqualTo("12:30");
    }

    @Test
    void 스키마는_strict_규칙을_지킨다() {
        var schema = AiRouteService.schema();
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        assertThat(schema.get("required")).isEqualTo(List.copyOf(
                ((java.util.Map<?, ?>) schema.get("properties")).keySet()));
    }
}
