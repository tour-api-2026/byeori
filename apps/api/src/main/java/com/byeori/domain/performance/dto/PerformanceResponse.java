package com.byeori.domain.performance.dto;

import com.byeori.domain.performance.Performance;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PerformanceResponse(
        Long id, Long venueId, String title, String genre, String posterImageUrl,
        LocalDate startDate, LocalDate endDate, String state, String externalBookingUrl,
        BigDecimal avgRating, int reviewCount, String source,
        BigDecimal lat, BigDecimal lng) {

    public static PerformanceResponse from(Performance p) {
        return new PerformanceResponse(
                p.getId(), p.getVenueId(), p.getTitle(), p.getGenre(), p.getPosterImageUrl(),
                p.getStartDate(), p.getEndDate(), p.getState(), p.getExternalBookingUrl(),
                p.getAvgRating(), p.getReviewCount() == null ? 0 : p.getReviewCount(), p.getSource(),
                p.getLat(), p.getLng());
    }
}
