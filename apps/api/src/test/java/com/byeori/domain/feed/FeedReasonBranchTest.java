package com.byeori.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.venue.Venue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 어떤 이유가 골라지는가. FeedReasonTest 는 팩토리 모양만 보므로 if 두 갈래를 맞바꿔도
 * 통과한다 — 순서(가까움 > 관심 지역 > 관심 주제 > 분류)는 여기서 못박는다.
 */
class FeedReasonBranchTest {

    private static final TasteProfile NO_REGION = new TasteProfile(Set.of(), false, Set.of());
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private static Venue venue(String category, String address, Double lat, Double lng) {
        Venue v = BeanUtils.instantiateClass(Venue.class);
        ReflectionTestUtils.setField(v, "category", category);
        ReflectionTestUtils.setField(v, "address", address);
        if (lat != null) ReflectionTestUtils.setField(v, "lat", BigDecimal.valueOf(lat));
        if (lng != null) ReflectionTestUtils.setField(v, "lng", BigDecimal.valueOf(lng));
        return v;
    }

    private static Performance performance(String genre, LocalDate start) {
        Performance p = BeanUtils.instantiateClass(Performance.class);
        ReflectionTestUtils.setField(p, "genre", genre);
        ReflectionTestUtils.setField(p, "startDate", start);
        return p;
    }

    @Test
    @DisplayName("3km 안이면 NEARBY — 거리를 소수 한 자리로")
    void 가까우면_nearby() {
        // 경도 0.01도 차이는 서울 위도에서 약 0.9km
        Venue v = venue("문화", "서울 종로구", 37.5700, 126.9800);
        FeedReason r = FeedService.venueReason(NO_REGION, v, 0.9, 0, 37.5700, 126.9700);
        assertThat(r.code()).isEqualTo("NEARBY");
        assertThat(r.param()).matches("0\\.\\d");
    }

    @Test
    @DisplayName("3km 밖이면 NEARBY 가 아니다 — 분류로 내려간다")
    void 멀면_nearby_아님() {
        Venue v = venue("문화", "서울 종로구", 37.5700, 126.9800);
        FeedReason r = FeedService.venueReason(NO_REGION, v, 0.0, 0, 37.5700, 127.1000);
        assertThat(r.code()).isEqualTo("CATEGORY");
    }

    @Test
    @DisplayName("좌표가 없으면 거리 이유를 건너뛴다")
    void 좌표_없음() {
        Venue v = venue("문화", "서울 종로구", null, null);
        assertThat(FeedService.venueReason(NO_REGION, v, 0.0, 0, null, null).code()).isEqualTo("CATEGORY");
    }

    @Test
    @DisplayName("취향이 0.5 를 넘으면 FAVORITE_TOPIC")
    void 관심_주제() {
        Venue v = venue("문화", "서울 종로구", null, null);
        FeedReason r = FeedService.venueReason(NO_REGION, v, 0.8, 0, null, null);
        assertThat(r.code()).isEqualTo("FAVORITE_TOPIC");
        assertThat(r.param()).isEqualTo("문화");
    }

    @Test
    @DisplayName("분류가 없으면 RECOMMENDED")
    void 분류_없음() {
        Venue v = venue(null, "서울 종로구", null, null);
        assertThat(FeedService.venueReason(NO_REGION, v, 0.0, 0, null, null).code()).isEqualTo("RECOMMENDED");
    }

    @Test
    @DisplayName("관심 지역이면 FAVORITE_REGION — 관심 주제보다 앞선다")
    void 관심_지역() {
        TasteProfile taste = new TasteProfile(Set.of(), false, Set.of("부산"));
        Venue v = venue("문화", "부산 해운대구", null, null);
        assertThat(FeedService.venueReason(taste, v, 0.9, 0, null, null).code()).isEqualTo("FAVORITE_REGION");
    }

    @Test
    @DisplayName("이미 시작했으면 ONGOING")
    void 진행중() {
        Performance p = performance("연극", TODAY.minusDays(2));
        assertThat(FeedService.performanceReason(p, TODAY, 0.9).code()).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("3일 뒤 시작이면 STARTS_IN_DAYS, 값은 \"3\"")
    void 며칠_뒤() {
        Performance p = performance("연극", TODAY.plusDays(3));
        FeedReason r = FeedService.performanceReason(p, TODAY, 0.0);
        assertThat(r.code()).isEqualTo("STARTS_IN_DAYS");
        assertThat(r.param()).isEqualTo("3");
    }

    @Test
    @DisplayName("7일째까지 임박, 8일째부터는 장르")
    void 칠일_경계() {
        assertThat(FeedService.performanceReason(performance("연극", TODAY.plusDays(7)), TODAY, 0.0).code())
                .isEqualTo("STARTS_IN_DAYS");
        assertThat(FeedService.performanceReason(performance("연극", TODAY.plusDays(8)), TODAY, 0.0).code())
                .isEqualTo("GENRE");
    }

    @Test
    @DisplayName("시작일이 없으면 장르·관심 주제·UPCOMING 순")
    void 시작일_없음() {
        assertThat(FeedService.performanceReason(performance("연극", null), TODAY, 0.9).code())
                .isEqualTo("FAVORITE_TOPIC");
        assertThat(FeedService.performanceReason(performance("연극", null), TODAY, 0.0).code())
                .isEqualTo("GENRE");
        assertThat(FeedService.performanceReason(performance(null, null), TODAY, 0.0).code())
                .isEqualTo("UPCOMING");
    }
}
