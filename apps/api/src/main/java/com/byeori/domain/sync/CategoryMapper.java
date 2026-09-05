package com.byeori.domain.sync;

/** TourAPI contentTypeId/신분류체계(lclsSystm) → 앱 카테고리(문화/체험/카페/맛집). */
public final class CategoryMapper {

    private CategoryMapper() {}

    public static String fromTour(String contentTypeId, String lclsSystm2, String lclsSystm3) {
        if (contentTypeId == null) return "문화";
        return switch (contentTypeId) {
            case "39" -> isCafe(lclsSystm2, lclsSystm3) ? "카페" : "맛집"; // 음식점
            case "28" -> "체험";                                          // 레포츠
            case "14" -> "문화";                                          // 문화시설
            default -> "문화";                                            // 12 관광지 등
        };
    }

    /**
     * 앱 카테고리 → TourAPI contentTypeId. 실시간 조회 시 유형을 좁히는 데 쓴다.
     * 0이면 유형 지정 없이 전체를 받는다.
     *
     * 카페와 맛집은 같은 39(음식점)라 API 단계에서 나눌 수 없다 — 응답의 신분류로 갈린다.
     * 한복은 공공데이터에 없는 자체 분류라 유형을 지정하지 않고 전체에서 찾는다.
     */
    public static int toTourContentTypeId(String category) {
        if (category == null || category.isBlank()) return 0;
        return switch (category) {
            case "맛집", "카페" -> 39;
            case "체험" -> 28;
            case "문화" -> 14;
            default -> 0;
        };
    }

    /** 카페/찻집 = 신분류 중분류 FD05(소분류 FD050100 카페 / FD050200 찻집 / FD050300 기타음료). */
    private static boolean isCafe(String lclsSystm2, String lclsSystm3) {
        return (lclsSystm2 != null && lclsSystm2.startsWith("FD05"))
                || (lclsSystm3 != null && lclsSystm3.startsWith("FD05"));
    }
}
