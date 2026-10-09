package com.byeori.domain.feed;

/**
 * 추천 한 칸.
 *
 * reason 을 함께 보낸다 — 왜 이게 떴는지 모르면 추천은 그냥 무작위로 보인다.
 * "관심 주제 · 공연", "내 주변 1.2km", "진행중" 같은 한 줄이다.
 */
public record FeedItem(
        String targetType,   // VENUE | PERFORMANCE
        Long targetId,
        String name,
        String imageUrl,
        String category,     // 장소는 분류, 행사는 장르
        Double lat,
        Double lng,
        String reason,
        double score
) {}
