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

    static JsonNode answer(String... ids) {
        var stops = OM.createArrayNode();
        for (String id : ids) stops.addObject().put("id", id).put("time", "10:00").put("reason", "가까워요");
        var root = OM.createObjectNode().put("title", "종로 하루").put("summary", "요약");
        root.set("stops", stops);
        return root;
    }

    static GenerateRequest req(boolean regenerate) {
        return new GenerateRequest(37.57, 126.98, "종로구", List.of("문화"), TODAY, regenerate);
    }

    @Test
    void 후보에_없는_ID와_중복은_버린다() {
        Preview p = AiRouteService.toPreview(answer("v1", "v99", "v2", "v1", "p7", "v3"), candidates(5), TODAY);

        assertThat(p.stops()).extracting(s -> s.targetId()).containsExactly(1L, 2L, 3L);
        assertThat(p.stops().get(0).name()).isEqualTo("장소1"); // 이름은 AI가 아니라 우리 DB 값
    }

    @Test
    void 쓸_수_있는_곳이_3곳_미만이면_실패() {
        assertThat(AiRouteService.toPreview(answer("v1", "v2", "v404"), candidates(5), TODAY)).isNull();
    }

    @Test
    void 잘못된_시각은_비우고_6곳을_넘기지_않는다() {
        var a = answer("v1", "v2", "v3", "v4", "v5", "v6", "v7");
        ((com.fasterxml.jackson.databind.node.ObjectNode) a.path("stops").get(0)).put("time", "25:99");

        Preview p = AiRouteService.toPreview(a, candidates(7), TODAY);

        assertThat(p.stops()).hasSize(6);
        assertThat(p.stops().get(0).time()).isNull();
        assertThat(p.stops().get(1).time()).isEqualTo("10:00");
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
                new GenerateRequest(37.57, 126.98, null, List.of("문화"), TODAY.minusDays(1), false)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.validate(
                new GenerateRequest(37.57, 126.98, null, List.of("해킹"), TODAY, false)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 스키마는_strict_규칙을_지킨다() {
        var schema = AiRouteService.schema();
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        assertThat(schema.get("required")).isEqualTo(List.copyOf(
                ((java.util.Map<?, ?>) schema.get("properties")).keySet()));
    }
}
