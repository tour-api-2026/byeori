package com.byeori.global.external.dto;

/** TourAPI areaBasedList2 항목(필요 필드만). */
public record TourItem(
        String contentId,
        String title,
        String addr1,
        String mapy,          // 위도(lat)
        String mapx,          // 경도(lng)
        String firstImage,
        String contentTypeId,
        String cat3,
        String tel
) {}
