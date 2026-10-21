package com.byeori.domain.feed;

/**
 * 추천 한 칸.
 *
 * reason 을 함께 보낸다 — 왜 이게 떴는지 모르면 추천은 그냥 무작위로 보인다.
 * 문장이 아니라 코드(reason)와 값(reasonParam)으로 보낸다 — 앱이 제 언어로 문장을 만든다.
 */
public record FeedItem(
        String targetType,   // VENUE | PERFORMANCE
        Long targetId,
        String name,
        String imageUrl,
        String category,     // 장소는 분류, 행사는 장르
        Double lat,
        Double lng,
        String reason,        // FeedReason.code
        String reasonParam,   // FeedReason.param (없으면 null)
        double score
) {}
