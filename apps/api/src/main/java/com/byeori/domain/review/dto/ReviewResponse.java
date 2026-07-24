package com.byeori.domain.review.dto;

import com.byeori.domain.review.Review;
import com.byeori.global.content.ContentTarget;
import java.time.LocalDateTime;

public record ReviewResponse(
        Long id, String targetType, Long targetId, Long userId,
        int rating, String content, LocalDateTime createdAt) {

    public static ReviewResponse from(Review r) {
        ContentTarget t = ContentTarget.of(r.getPerformanceId(), r.getVenueId());
        return new ReviewResponse(r.getId(), t.targetType().name(), t.targetId(), r.getUserId(),
                r.getRating(), r.getContent(), r.getCreatedAt());
    }
}
