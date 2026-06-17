package com.byeori.global.external.dto;

/** TourAPI areaBasedList2 항목(필요 필드만). v4.4 신분류체계(lclsSystm) 기준. */
public record TourItem(
        String contentId,
        String title,
        String addr1,
        String mapy,          // 위도(lat)
        String mapx,          // 경도(lng)
        String firstImage,
        String contentTypeId,
        String lclsSystm2,    // 신분류 중분류(예: FD05 카페/찻집)
        String lclsSystm3,    // 신분류 소분류(예: FD050100 카페, FD050200 찻집)
        String tel
) {}
