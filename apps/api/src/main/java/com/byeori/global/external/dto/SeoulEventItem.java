package com.byeori.global.external.dto;

/** 서울 열린데이터 culturalEventInfo 1행. strtdate/endDate는 "yyyy-MM-dd HH:mm:ss.S" 원문. */
public record SeoulEventItem(
        String codename, String title, String place,
        String strtdate, String endDate,
        String mainImg, String orgLink, String hmpgAddr,
        String lat, String lot) {
}
