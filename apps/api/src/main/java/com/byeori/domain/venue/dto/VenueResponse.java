package com.byeori.domain.venue.dto;

import com.byeori.domain.venue.Venue;
import java.math.BigDecimal;

public record VenueResponse(
        Long id, String name, String address, String category, String imageUrl,
        boolean hanbokDiscount, String hanbokDiscountDesc,
        BigDecimal avgRating, int reviewCount, String source,
        BigDecimal lat, BigDecimal lng,
        /** 공사 콘텐츠 ID. 우리 DB에 없는 장소는 id가 null이라 이 값으로 상세를 연다. */
        String tourContentId) {

    public static VenueResponse from(Venue v) {
        return new VenueResponse(
                v.getId(), v.getName(), v.getAddress(), v.getCategory(), v.getImageUrl(),
                v.isHanbokDiscount(), v.getHanbokDiscountDesc(),
                v.getAvgRating(), v.getReviewCount() == null ? 0 : v.getReviewCount(), v.getSource(),
                v.getLat(), v.getLng(),
                v.getDetailContentId() != null ? v.getDetailContentId() : v.getTourContentId());
    }
}
