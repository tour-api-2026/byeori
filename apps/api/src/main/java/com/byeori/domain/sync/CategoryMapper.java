package com.byeori.domain.sync;

/**
 * TourAPI contentTypeId/신분류체계(lclsSystm) → 앱 카테고리.
 *
 * 공사 유형(contentTypeId)만으로는 우리 카테고리가 나오지 않는다. 음식점(39)은 카페와
 * 맛집이 섞여 있고, 쇼핑(38)·숙박(32)은 대부분 우리와 상관없는 것(약국·모텔 등)이라
 * 전통문화와 닿는 소분류만 골라 쓴다. 그 구분이 신분류체계에 있다.
 */
public final class CategoryMapper {

    private CategoryMapper() {}

    /** 우리가 다루지 않는 장소. 수집 단계에서 걸러낸다. */
    public static final String NONE = null;

    public static String fromTour(String contentTypeId, String lclsSystm2, String lclsSystm3) {
        if (contentTypeId == null) return "문화";
        return switch (contentTypeId) {
            case "39" -> isCafe(lclsSystm2, lclsSystm3) ? "카페" : "맛집";   // 음식점
            case "28" -> "체험";                                            // 레포츠
            case "14" -> "문화";                                            // 문화시설
            case "38" -> shopping(lclsSystm2, lclsSystm3);                  // 쇼핑 — 일부만
            case "32" -> lodging(lclsSystm3);                               // 숙박 — 한옥만
            default -> "문화";                                              // 12 관광지 등
        };
    }

    /**
     * 앱 카테고리 → TourAPI contentTypeId. 0이면 유형 지정 없이 전체를 받는다.
     *
     * 카페와 맛집은 같은 39라 이 단계에서 나눌 수 없다 — 응답의 신분류로 갈린다.
     * 전통시장·공예도 같은 38이고, 한복은 공공데이터에 없는 자체 표시라 0이다.
     */
    public static int toTourContentTypeId(String category) {
        if (category == null || category.isBlank()) return 0;
        return switch (category) {
            case "맛집", "카페" -> 39;
            case "체험" -> 28;
            case "문화" -> 14;
            case "전통시장", "공예" -> 38;
            case "한옥스테이" -> 32;
            default -> 0;
        };
    }

    /** 카페/찻집 = 중분류 FD05(소분류 FD050100 카페 / FD050200 찻집 / FD050300 기타음료). */
    private static boolean isCafe(String lclsSystm2, String lclsSystm3) {
        return startsWith(lclsSystm2, "FD05") || startsWith(lclsSystm3, "FD05");
    }

    /**
     * 쇼핑(38)은 16,742건 대부분이 약국·안경원·백화점이라 그대로 담으면 안 된다.
     * 전통문화와 닿는 둘만 고른다.
     *   SH06     시장 — 전통시장·5일장·상설시장
     *   SH050100 공예 — 공방·공예사·기념품 갤러리
     */
    private static String shopping(String lclsSystm2, String lclsSystm3) {
        if (startsWith(lclsSystm2, "SH06")) return "전통시장";
        if (startsWith(lclsSystm3, "SH050100")) return "공예";
        return NONE;
    }

    /** 숙박(32)은 호텔·모텔·펜션이 대부분이라 한옥(AC030200)만 고른다. */
    private static String lodging(String lclsSystm3) {
        return startsWith(lclsSystm3, "AC030200") ? "한옥스테이" : NONE;
    }

    private static boolean startsWith(String s, String prefix) {
        return s != null && s.startsWith(prefix);
    }
}
