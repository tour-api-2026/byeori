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

    /**
     * 외국인이 꼭 들르는 브랜드 매장·장소. **신분류가 아니라 상호명으로 고른다.**
     *
     * 쇼핑(38) 4,787건(서울·제주)의 신분류가 쓸모없기 때문이다 — 대부분이 SH040300 한 칸에
     * 몰려 있고 거기에 약국·안경원·올리브영·다이소가 전부 섞여 있다. 백화점조차 8건만
     * SH010100 으로 제대로 분류되고 21건은 그 잡탕에 들어 있다.
     *
     * **브랜드로 시작할 때만** 고르는 것이 핵심이다. '백화점'이 들어가면 담는 식으로 하면
     * 973건이 들어오는데 그중 944건이 백화점이 아니라 그 안에 입점한 매장이다
     * ('ABC마트 GS 롯데백화점 김포공항점', 'COS 신세계백화점 강남점', '갤럭시 롯데백화점 강남점').
     * 입점 매장은 앞에 자기 상호가 붙으므로 ^ 하나로 깨끗하게 갈린다.
     *
     * 올리브영(397건)·다이소(63건)는 독립 매장이라 같은 규칙에서 버려지는 것이 없다.
     *
     * 면세점은 뺐다. 공사 데이터에 3건뿐이고 그중 둘은 롯데백화점·아울렛에 붙은
     * '[면세점(TAX REFUND SHOP)]' 표시다. 시내면세점은 아예 없어서, 넣으려면 손으로 넣어야 한다.
     */
    private static final java.util.regex.Pattern MUST_VISIT_BRAND = java.util.regex.Pattern.compile(
            "^(올리브영|다이소|(?:롯데|신세계|현대|갤러리아|AK|NC)\\s?백화점)");

    /**
     * 한강공원은 쇼핑이 아니라 관광지(12)에 있어 유형으로는 못 가른다. 이름으로 찾는다.
     * '여의도한강공원'처럼 앞에 지역이 붙으므로 시작이 아니라 포함으로 본다.
     *
     * 물놀이장·눈썰매장 같은 부대시설도 함께 딸려 온다(24건 중 4건). 한 장소에 카테고리가
     * 하나뿐이라 그것들은 체험에서 빠진다 — 한강공원 안의 시설이니 여기 두는 편이 맞다고 봤다.
     */
    private static boolean isMustVisit(String title) {
        if (title == null) return false;
        String t = title.strip();
        return MUST_VISIT_BRAND.matcher(t).find() || t.contains("한강공원");
    }

    public static String fromTour(String contentTypeId, String title, String lclsSystm2, String lclsSystm3) {
        // 유형보다 먼저 본다 — 필수 명소는 쇼핑(38)과 관광지(12)에 흩어져 있다.
        if (isMustVisit(title)) return "필수명소";
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
     * 필수명소는 쇼핑(38)과 관광지(12)에 흩어져 있어 하나로 못 집는다 — 0으로 전체를 받는다.
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
