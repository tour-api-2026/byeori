package com.byeori.domain.venue.dto;

import com.byeori.domain.venue.Venue;
import java.math.BigDecimal;

public record VenueDetailResponse(
        Long id, String name, String address, String category, String imageUrl, String description,
        String operatingHours, String phone, String homepageUrl,
        boolean hanbokDiscount, String hanbokDiscountDesc,
        BigDecimal avgRating, int reviewCount, String source,
        BigDecimal lat, BigDecimal lng, String visibility) {

    public static VenueDetailResponse from(Venue v) {
        return new VenueDetailResponse(
                v.getId(), v.getName(), v.getAddress(), v.getCategory(), v.getImageUrl(), v.getDescription(),
                v.getOperatingHours(), v.getPhone(), v.getHomepageUrl(),
                v.isHanbokDiscount(), v.getHanbokDiscountDesc(),
                v.getAvgRating(), v.getReviewCount() == null ? 0 : v.getReviewCount(), v.getSource(),
                v.getLat(), v.getLng(), v.getVisibility());
    }
}
