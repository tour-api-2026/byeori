package com.byeori.global.external.dto;

/**
 * detailCommon2 의 기본 정보. 우리 DB에 없는 장소(실시간 결과의 절반 가까이)를
 * 상세 화면에 띄우려면 이름·주소·좌표가 필요한데, TourDetail 에는 운영 정보만 있다.
 */
public record TourBasic(
        String contentId, String contentTypeId, String title, String addr1,
        String firstImage, String mapx, String mapy, String tel,
        String lclsSystm2, String lclsSystm3
) {}
