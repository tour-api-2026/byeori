package com.byeori.domain.wishlist.dto;

import java.math.BigDecimal;

public record WishlistResponse(
        Long id, String targetType, Long targetId,
        String name, String imageUrl, BigDecimal avgRating, int reviewCount) {
}
