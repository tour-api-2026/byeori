package com.byeori.domain.review.dto;

public record ReviewRequest(String targetType, Long targetId, Integer rating, String content) {
}
