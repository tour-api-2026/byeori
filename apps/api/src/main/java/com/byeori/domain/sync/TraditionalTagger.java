package com.byeori.domain.sync;

import java.util.List;

/** 전통 테마 행사 판정 — KOPIS 장르 '국악' 또는 제목의 전통 키워드 포함. */
public final class TraditionalTagger {

    private TraditionalTagger() {}

    private static final List<String> TITLE_KEYWORDS = List.of(
            "전통", "국악", "한옥", "고궁", "궁궐", "문화재", "민속", "판소리",
            "사물놀이", "풍물", "농악", "탈춤", "한복", "궁중", "무형유산",
            "향교", "서원", "단오", "세시풍속",
            // 5대 궁·종묘 — '궁' 한 글자는 오탐(궁금 등)이 많아 고유명만 매칭
            "경복궁", "창덕궁", "덕수궁", "창경궁", "경희궁", "종묘");

    public static boolean isTraditional(String title, String genre) {
        if (genre != null && genre.contains("국악")) return true;
        if (title == null) return false;
        // '문화재단'(기관명)이 '문화재'로 오탐되지 않게 제거 후 매칭
        String normalized = title.replace("문화재단", "");
        return TITLE_KEYWORDS.stream().anyMatch(normalized::contains);
    }
}
