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
}
