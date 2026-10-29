package com.byeori.domain.translation;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TranslationClientTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    @DisplayName("원문과 번역을 짝지어 돌려준다")
    void 짝짓기() throws Exception {
        var json = om.readTree("""
            {"items":[{"src":"경복궁","out":"Gyeongbokgung Palace"},
                      {"src":"북촌한옥마을","out":"Bukchon Hanok Village"}]}""");
        var got = TranslationClient.parse(json, List.of("경복궁", "북촌한옥마을"));
        assertThat(got).containsEntry("경복궁", "Gyeongbokgung Palace")
                       .containsEntry("북촌한옥마을", "Bukchon Hanok Village");
    }

    @Test
    @DisplayName("깨진 항목만 버리고 나머지는 살린다")
    void 부분_실패() throws Exception {
        var json = om.readTree("""
            {"items":[{"src":"경복궁","out":"Gyeongbokgung Palace"},
                      {"src":"없는것","out":"Ghost"},
                      {"out":"src 가 없다"},
                      {"src":"북촌한옥마을","out":""}]}""");
        var got = TranslationClient.parse(json, List.of("경복궁", "북촌한옥마을"));
        assertThat(got).containsOnlyKeys("경복궁");
    }

    @Test
    @DisplayName("null·공백 번역도 버린다 — 문자열 \"null\" 이 저장되면 안 된다")
    void null_값() throws Exception {
        var json = om.readTree("""
            {"items":[{"src":"경복궁","out":null},
                      {"src":null,"out":"Ghost"},
                      {"src":"북촌한옥마을","out":"   "},
                      {"src":"창덕궁","out":" Changdeokgung Palace "}]}""");
        var got = TranslationClient.parse(json, List.of("경복궁", "북촌한옥마을", "창덕궁"));
        assertThat(got).containsOnlyKeys("창덕궁").containsEntry("창덕궁", "Changdeokgung Palace");
    }

    @Test
    @DisplayName("items 가 없거나 모양이 다르면 빈 결과 — 던지지 않는다")
    void 모양이_다름() throws Exception {
        assertThat(TranslationClient.parse(om.readTree("{}"), List.of("경복궁"))).isEmpty();
        assertThat(TranslationClient.parse(om.readTree("[]"), List.of("경복궁"))).isEmpty();
        assertThat(TranslationClient.parse(om.readTree("null"), List.of("경복궁"))).isEmpty();
        assertThat(TranslationClient.parse(null, List.of("경복궁"))).isEmpty();
        assertThat(TranslationClient.parse(om.readTree("{\"items\":\"x\"}"), List.of("경복궁"))).isEmpty();
    }

    @Test
    @DisplayName("한글이 섞인 번역은 일본어·중국어에서 쓰지 않는다")
    void 한글_섞임() {
        assertThat(TranslationClient.hasHangul("고척동 고인돌")).isTrue();
        assertThat(TranslationClient.hasHangul("寺(서울)")).isTrue();
        assertThat(TranslationClient.hasHangul("景福宮")).isFalse();
        assertThat(TranslationClient.hasHangul("Gyeongbokgung Palace")).isFalse();
    }

    /**
     * 제목에 앞뒤 공백이 든 행이 있다. AI 는 "echoing the input unchanged" 를 지시해도
     * 되돌려 주는 src 에서 앞뒤 공백을 떼어 버린다. 글자 그대로만 맞추면 그 행은 매번
     * 버려지고, 세 밤 뒤 포기 상태가 된다(운영에서 영어 포기 14건 중 13건이 이것이었다).
     */
    @Test
    @DisplayName("AI 가 앞뒤 공백을 떼어 돌려줘도 원래 행에 짝지어 준다")
    void 공백_떼인_src() throws Exception {
        var json = om.readTree("""
            {"items":[{"src":"제1회 대한민국 전통예술 대제전","out":"The 1st Korea Traditional Arts Festival"}]}""");
        var got = TranslationClient.parse(json, List.of("제1회 대한민국 전통예술 대제전 "));
        // 키는 DB 에 있는 값(공백 포함)이어야 한다 — 부르는 쪽이 그 값으로 찾는다
        assertThat(got).containsOnlyKeys("제1회 대한민국 전통예술 대제전 ");
    }

    /** 공백만 다른 두 제목을 같은 묶음에서 물었으면 어느 쪽인지 알 수 없다. 둘 다 버린다. */
    @Test
    @DisplayName("공백만 다른 제목이 함께 있으면 느슨한 짝짓기를 쓰지 않는다")
    void 공백만_다른_제목() throws Exception {
        var json = om.readTree("""
            {"items":[{"src":"남산골","out":"Namsangol"}]}""");
        var got = TranslationClient.parse(json, List.of("남산골", "남산골 "));
        // 글자 그대로 맞는 것이 있으면 그것만 쓴다
        assertThat(got).containsOnlyKeys("남산골");

        var json2 = om.readTree("""
            {"items":[{"src":"남산골","out":"Namsangol"}]}""");
        var got2 = TranslationClient.parse(json2, List.of("남산골 ", " 남산골"));
        assertThat(got2).isEmpty();
    }

    /**
     * 낱자(ㄱ, ㅂㅍ)는 번역문에 남아야 한다. 제목에 낱자를 디자인으로 쓴 공연이 있고
     * (`SUPERPOSITION #3. PP ㅂㅍ`, `제5회 ㄱ기억연극전`), 그건 고유명사다.
     * CSV 임포터(scripts/import-translations.py)는 이미 음절만 본다. 기준을 맞춘다.
     */
    @Test
    @DisplayName("낱자만 든 것은 한글 섞임으로 보지 않는다 — 임포터와 같은 기준")
    void 낱자는_통과() {
        assertThat(TranslationClient.hasHangul("SUPERPOSITION #3. PP \u3142\u314D")).isFalse();
        assertThat(TranslationClient.hasHangul("\u3131\u8A18\u61B6\u6F14\u5287\u5C55")).isFalse();
        assertThat(TranslationClient.hasHangul("\u3145\u314F\u3139\u3141")).isFalse();
    }
}
