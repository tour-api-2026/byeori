package com.byeori.domain.venue.dto;

import com.byeori.domain.venue.Venue;
import com.byeori.global.external.dto.TourBasic;
import com.byeori.global.external.dto.TourDetail;
import java.math.BigDecimal;

public record VenueDetailResponse(
        Long id, String name, String address, String category, String imageUrl, String description,
        String operatingHours, String phone, String homepageUrl,
        boolean hanbokDiscount, String hanbokDiscountDesc,
        BigDecimal avgRating, int reviewCount, String source,
        BigDecimal lat, BigDecimal lng, String visibility,
        /** 한국관광공사 OpenAPI에서 이 요청 시점에 받아온 값. 실패하거나 없으면 null. */
        LiveInfo liveInfo) {

    /** 상세 화면을 열 때 실시간으로 조회한 운영 정보. 항목별로 없으면 null이라 화면에서 숨긴다. */
    public record LiveInfo(String overview, String useTime, String restDate,
                           String infoCenter, String parking, String homepage) {}

    /**
     * 우리 DB에 없는 장소. 공사 응답만으로 화면을 구성한다.
     * id는 null이라 리뷰·위시리스트처럼 우리 레코드가 필요한 기능은 화면에서 막는다.
     */
    public static VenueDetailResponse fromTour(TourBasic b, TourDetail d, String category) {
        LiveInfo live = (d == null || d.isEmpty()) ? null
                : new LiveInfo(d.overview(), d.useTime(), d.restDate(), d.infoCenter(), d.parking(), d.homepage());
        BigDecimal lat = b.mapy() == null || b.mapy().isBlank() ? null : new BigDecimal(b.mapy());
        BigDecimal lng = b.mapx() == null || b.mapx().isBlank() ? null : new BigDecimal(b.mapx());
        return new VenueDetailResponse(
                null, b.title(), b.addr1(), category, b.firstImage(), null,
                d == null ? null : d.useTime(), b.tel(), d == null ? null : d.homepage(),
                false, null, BigDecimal.ZERO, 0, "TOURAPI",
                lat, lng, "PUBLIC", live);
    }

    public static VenueDetailResponse from(Venue v) {
        return from(v, null);
    }

    public static VenueDetailResponse from(Venue v, TourDetail d) {
        LiveInfo live = (d == null || d.isEmpty()) ? null
                : new LiveInfo(d.overview(), d.useTime(), d.restDate(), d.infoCenter(), d.parking(), d.homepage());
        return new VenueDetailResponse(
                v.getId(), v.getName(), v.getAddress(), v.getCategory(), v.getImageUrl(), v.getDescription(),
                v.getOperatingHours(), v.getPhone(), v.getHomepageUrl(),
                v.isHanbokDiscount(), v.getHanbokDiscountDesc(),
                v.getAvgRating(), v.getReviewCount() == null ? 0 : v.getReviewCount(), v.getSource(),
                v.getLat(), v.getLng(), v.getVisibility(), live);
    }
}
