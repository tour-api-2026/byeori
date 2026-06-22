package com.byeori.global.external.dto;

/** TourAPI searchFestival2 항목(축제/행사). 공연과 달리 행사기간(eventStartDate/eventEndDate)을 제공한다. */
public record TourFestivalItem(
        String contentId,
        String title,
        String addr1,
        String mapy,            // 위도(lat)
        String mapx,            // 경도(lng)
        String firstImage,
        String tel,
        String eventStartDate,  // 행사 시작일 yyyyMMdd
        String eventEndDate     // 행사 종료일 yyyyMMdd
) {}
