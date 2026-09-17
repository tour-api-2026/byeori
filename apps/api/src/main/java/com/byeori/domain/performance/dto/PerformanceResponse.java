package com.byeori.domain.performance.dto;

import com.byeori.domain.performance.Performance;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PerformanceResponse(
        Long id, Long venueId, String title, String genre, String posterImageUrl,
        LocalDate startDate, LocalDate endDate, String state, String externalBookingUrl,
        BigDecimal avgRating, int reviewCount, String source,
        BigDecimal lat, BigDecimal lng, boolean traditional,
        /** 공사 축제는 상세를 열 때 소개글을 실시간으로 받아온다. 없으면 null. */
        String overview,
        /** 행사를 자세히 볼 수 있는 원문 페이지. 출처마다 다르다(아래 infoUrl 참고). */
        String infoUrl) {

    public static PerformanceResponse from(Performance p) {
        return new PerformanceResponse(
                p.getId(), p.getVenueId(), p.getTitle(), p.getGenre(), p.getPosterImageUrl(),
                p.getStartDate(), p.getEndDate(), p.getState(), p.getExternalBookingUrl(),
                p.getAvgRating(), p.getReviewCount() == null ? 0 : p.getReviewCount(), p.getSource(),
                p.getLat(), p.getLng(), p.isTraditional(), null, infoUrl(p));
    }

    /** 공사 OpenAPI 에서 받아온 소개글·홈페이지를 덧붙인 응답. */
    public PerformanceResponse withLive(String overview, String homepage) {
        return new PerformanceResponse(id, venueId, title, genre, posterImageUrl,
                startDate, endDate, state, externalBookingUrl, avgRating, reviewCount, source,
                lat, lng, traditional, overview, homepage != null ? homepage : infoUrl);
    }

    /**
     * 원문 페이지 주소.
     *
     * KOPIS 공연은 예매 링크가 없어(전통 행사 631건 전부) 눌러도 갈 곳이 없었다.
     * 공연 ID로 KOPIS 공개 상세 페이지를 만들면 공연기간·공연장소·관람연령과
     * 예매처 안내까지 그쪽에서 볼 수 있다.
     * 서울 행사는 이미 예매 링크를 갖고 있고, 공사 축제는 상세 조회로 홈페이지를 받는다.
     */
    private static String infoUrl(Performance p) {
        if (p.getKopisId() != null && !p.getKopisId().isBlank()) {
            return "https://kopis.or.kr/por/db/pblprfr/pblprfrView.do?menuId=MNU_00020&mt20Id=" + p.getKopisId();
        }
        return null;
    }
}
