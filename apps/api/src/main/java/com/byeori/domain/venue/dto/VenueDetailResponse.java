package com.byeori.domain.venue.dto;

import com.byeori.global.i18n.Lang;
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

    public static VenueDetailResponse from(Venue v, Lang lang) {
        return from(v, null, lang);
    }

    public static VenueDetailResponse from(Venue v, TourDetail d, Lang lang) {
        LiveInfo live = (d == null || d.isEmpty()) ? null
                : new LiveInfo(d.overview(), d.useTime(), d.restDate(), d.infoCenter(), d.parking(), d.homepage());
        return new VenueDetailResponse(
                v.getId(), v.nameIn(lang), v.getAddress(), v.getCategory(), v.getImageUrl(), v.getDescription(),
                v.getOperatingHours(), v.getPhone(), v.getHomepageUrl(),
                v.isHanbokDiscount(), v.getHanbokDiscountDesc(),
                v.getAvgRating(), v.getReviewCount() == null ? 0 : v.getReviewCount(), v.getSource(),
                v.getLat(), v.getLng(), v.getVisibility(), live);
    }

    /**
     * 번역한 긴 설명으로 바꾼 사본. 앱이 읽는 곳은 공사 개요(liveInfo.overview)라 그것이 있으면
     * 거기를, 없으면 우리 DB 의 description 을 바꾼다 — 번역할 원문을 고른 기준과 같아야 한다.
     */
    public VenueDetailResponse withLongText(String text) {
        if (liveInfo != null && liveInfo.overview() != null && !liveInfo.overview().isBlank()) {
            LiveInfo l = new LiveInfo(text, liveInfo.useTime(), liveInfo.restDate(),
                    liveInfo.infoCenter(), liveInfo.parking(), liveInfo.homepage());
            return new VenueDetailResponse(id, name, address, category, imageUrl, description, operatingHours,
                    phone, homepageUrl, hanbokDiscount, hanbokDiscountDesc, avgRating, reviewCount, source,
                    lat, lng, visibility, l);
        }
        return new VenueDetailResponse(id, name, address, category, imageUrl, text, operatingHours,
                phone, homepageUrl, hanbokDiscount, hanbokDiscountDesc, avgRating, reviewCount, source,
                lat, lng, visibility, liveInfo);
    }

    /** 번역할 긴 원문. 공사 개요가 있으면 그것, 없으면 DB 설명. 둘 다 비면 null. */
    public String longText() {
        if (liveInfo != null && liveInfo.overview() != null && !liveInfo.overview().isBlank()) return liveInfo.overview();
        return description == null || description.isBlank() ? null : description;
    }
}
