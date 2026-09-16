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
        String overview) {

    public static PerformanceResponse from(Performance p) {
        return new PerformanceResponse(
                p.getId(), p.getVenueId(), p.getTitle(), p.getGenre(), p.getPosterImageUrl(),
                p.getStartDate(), p.getEndDate(), p.getState(), p.getExternalBookingUrl(),
                p.getAvgRating(), p.getReviewCount() == null ? 0 : p.getReviewCount(), p.getSource(),
                p.getLat(), p.getLng(), p.isTraditional(), null);
    }

    /** 공사 OpenAPI 에서 받아온 소개글을 덧붙인 응답. */
    public PerformanceResponse withOverview(String overview) {
        return new PerformanceResponse(id, venueId, title, genre, posterImageUrl,
                startDate, endDate, state, externalBookingUrl, avgRating, reviewCount, source,
                lat, lng, traditional, overview);
    }
}
