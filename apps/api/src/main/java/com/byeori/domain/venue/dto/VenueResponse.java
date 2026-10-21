package com.byeori.domain.venue.dto;

import com.byeori.global.i18n.Lang;
import com.byeori.domain.venue.Venue;
import java.math.BigDecimal;

public record VenueResponse(
        Long id, String name, String address, String category, String imageUrl,
        boolean hanbokDiscount, String hanbokDiscountDesc,
        BigDecimal avgRating, int reviewCount, String source,
        BigDecimal lat, BigDecimal lng,
        /** 공사 콘텐츠 ID. 우리 DB에 없는 장소는 id가 null이라 이 값으로 상세를 연다. */
        String tourContentId) {

    /** 번역이 없는 행은 nameIn 이 한국어 원본으로 떨어뜨린다 — 이름이 비어 나가지 않는다. */
    public static VenueResponse from(Venue v, Lang lang) {
        return new VenueResponse(
                v.getId(), v.nameIn(lang), v.getAddress(), v.getCategory(), v.getImageUrl(),
                v.isHanbokDiscount(), v.getHanbokDiscountDesc(),
                v.getAvgRating(), v.getReviewCount() == null ? 0 : v.getReviewCount(), v.getSource(),
                v.getLat(), v.getLng(),
                v.getDetailContentId() != null ? v.getDetailContentId() : v.getTourContentId());
    }
}
