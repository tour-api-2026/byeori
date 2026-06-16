package com.byeori.domain.sync;

/** TourAPI contentTypeId/cat3 → 앱 카테고리(문화/체험/카페/맛집). 실제 응답 보며 보정. */
public final class CategoryMapper {

    private CategoryMapper() {}

    private static final String CAT3_CAFE = "A05020900"; // 카페/찻집

    public static String fromTour(String contentTypeId, String cat3) {
        if (contentTypeId == null) return "문화";
        return switch (contentTypeId) {
            case "39" -> CAT3_CAFE.equals(cat3) ? "카페" : "맛집"; // 음식점
            case "28" -> "체험";                                   // 레포츠
            case "14" -> "문화";                                   // 문화시설
            default -> "문화";                                     // 12 관광지 등
        };
    }
}
