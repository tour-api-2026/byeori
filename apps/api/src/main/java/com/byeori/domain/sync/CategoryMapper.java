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

    /** 카페/찻집 = 신분류 중분류 FD05(소분류 FD050100 카페 / FD050200 찻집 / FD050300 기타음료). */
    private static boolean isCafe(String lclsSystm2, String lclsSystm3) {
        return (lclsSystm2 != null && lclsSystm2.startsWith("FD05"))
                || (lclsSystm3 != null && lclsSystm3.startsWith("FD05"));
    }
}
