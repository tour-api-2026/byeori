package com.byeori.domain.venue.dto;

import com.byeori.domain.venue.Venue;
import java.math.BigDecimal;

public record VenueResponse(
        Long id, String name, String address, String category, String imageUrl,
        boolean hanbokDiscount, String hanbokDiscountDesc,
        BigDecimal avgRating, int reviewCount, String source,
        BigDecimal lat, BigDecimal lng) {

    public static VenueResponse from(Venue v) {
        return new VenueResponse(
                v.getId(), v.getName(), v.getAddress(), v.getCategory(), v.getImageUrl(),
                v.isHanbokDiscount(), v.getHanbokDiscountDesc(),
                v.getAvgRating(), v.getReviewCount() == null ? 0 : v.getReviewCount(), v.getSource(),
                v.getLat(), v.getLng());
    }
}
