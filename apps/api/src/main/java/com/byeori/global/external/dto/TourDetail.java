package com.byeori.global.external.dto;

/**
 * 한국관광공사 OpenAPI 상세 조회(detailCommon2 + detailIntro2) 결과.
 *
 * 장소 상세 화면을 열 때마다 실시간으로 받아오는 값이다. 동기화로 내려받는 목록
 * 데이터(TourItem)에는 없는 항목들이라, 상세 화면에서만 확인할 수 있다.
 * 값이 비어 있는 항목은 null — 화면에서 해당 줄을 숨긴다.
 */
public record TourDetail(
        String overview,     // 장소 소개글 (detailCommon2)
        String homepage,     // 홈페이지 (HTML 앵커가 섞여 오므로 정제 필요)
        String useTime,      // 이용/운영 시간 (detailIntro2)
        String restDate,     // 휴무일
        String infoCenter,   // 문의 및 안내 전화
        String parking       // 주차 시설
) {
    /** 모든 항목이 비어 있으면 화면에 붙일 게 없다. */
    public boolean isEmpty() {
        return overview == null && homepage == null && useTime == null
                && restDate == null && infoCenter == null && parking == null;
    }
}
