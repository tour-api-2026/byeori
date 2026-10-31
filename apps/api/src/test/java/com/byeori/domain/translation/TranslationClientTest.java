package com.byeori.domain.translation;

import static org.assertj.core.api.Assertions.assertThat;
import com.byeori.global.i18n.Lang;
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

    /**
     * 한글이 하나라도 남으면 버리던 것을 바꿨다.
     *
     * 모델은 보통명사만 번역하고 고유명사를 한글로 남긴다(`뚝섬汉江公园`). 금지해도 계속 그런다 —
     * 한자가 없는 이름을 음역자로 만드는 것을 꺼린다. 그런데 그걸 버리면 **중국어 사용자에게
     * 한국어 원문이 그대로 보인다.** 汉江公园 만 읽어도 무엇인지 아는 쪽이 낫고, 실제 가이드북도
     * 음역이 안 되는 고유명사는 원문으로 둔다.
     *
     * 그래서 '한글이 남았나' 가 아니라 **'한국어를 얼마나 줄였나'** 로 판정한다.
     */
    @Test
    @DisplayName("고유명사만 한글로 남은 부분 번역은 쓴다")
    void 부분_번역은_쓴다() {
        assertThat(TranslationClient.usable("뚝섬한강공원", "뚝섬汉江公园")).isTrue();
        assertThat(TranslationClient.usable("꽃잔디식당", "花잔디食堂")).isTrue();
        assertThat(TranslationClient.usable("두가헌 레스토랑", "두가헌レストラン")).isTrue();
        assertThat(TranslationClient.usable("씨야 20주년 전국 투어 콘서트: THE FAN [서울]",
                "씨야 20周年全国巡演音乐会: THE FAN [首尔]")).isTrue();
        // 고유명사가 보통명사보다 길어도 받는다. 村 만 봐도 마을인 것을 안다 —
        // 처음 둔 '절반' 선이 이것들을 버렸다.
        assertThat(TranslationClient.usable("예움터 마을", "예움터村")).isTrue();
        assertThat(TranslationClient.usable("꾸꾸네", "꾸꾸家")).isTrue();
        assertThat(TranslationClient.usable("뚝도 아리수 정수센터", "뚝도 아리수 정수中心")).isTrue();
        assertThat(TranslationClient.usable("서울남산국악당 젊은국악", "서울남산국악당 若手国楽")).isTrue();
    }

    @Test
    @DisplayName("한글이 안 남은 번역은 당연히 쓴다")
    void 온전한_번역() {
        assertThat(TranslationClient.usable("경복궁", "景福宮")).isTrue();
        assertThat(TranslationClient.usable("웃장", "ウッチャン")).isTrue();
        // 낱자만 든 제목은 그대로 돌려주는 것이 맞다 — 디자인으로 쓴 고유명사다
        assertThat(TranslationClient.usable("SUPERPOSITION #3. PP \u3142\u314D",
                "SUPERPOSITION #3. PP \u3142\u314D")).isTrue();
    }

    /** 부분 번역을 받아들이는 것과 '일을 안 한 것' 을 받아들이는 것은 다르다. */
    @Test
    @DisplayName("일을 안 한 응답은 계속 버린다")
    void 일을_안_한_것() {
        // 원문을 그대로 되돌렸다 — 짧은 순우리말 이름에서 모델이 자주 이렇게 한다
        assertThat(TranslationClient.usable("고척동 고인돌", "고척동 고인돌")).isFalse();
        assertThat(TranslationClient.usable("예당", "예당")).isFalse();
        assertThat(TranslationClient.usable("빠우", " 빠우 ")).isFalse();   // 공백만 다른 것도 되돌림이다
        // 로마자가 섞여 있어도 되돌림은 되돌림이다
        assertThat(TranslationClient.usable("Heon Seo (헌서) 1st EP", "Heon Seo (헌서) 1st EP")).isFalse();
        // 한국어가 한 글자도 줄지 않았다
        assertThat(TranslationClient.usable("예당", "예당 ホール")).isFalse();
        // 번역하지 않고 잘라 냈다 — 줄긴 했지만 번역된 글자가 없다
        assertThat(TranslationClient.usable("뚝섬한강공원", "뚝섬")).isFalse();
        assertThat(TranslationClient.usable("경복궁", "")).isFalse();
        assertThat(TranslationClient.usable("경복궁", null)).isFalse();
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
    @DisplayName("낱자는 한글로 세지 않는다 — 임포터와 같은 기준")
    void 낱자는_세지_않는다() {
        assertThat(TranslationClient.hangulSyllables("SUPERPOSITION #3. PP \u3142\u314D")).isZero();
        assertThat(TranslationClient.hangulSyllables("\u3131\u8A18\u61B6\u6F14\u5287\u5C55")).isZero();
        assertThat(TranslationClient.hangulSyllables("\u3145\u314F\u3139\u3141")).isZero();
        assertThat(TranslationClient.hangulSyllables("\uACBD\uBD35\uAD81")).isEqualTo(3);
    }

    /**
     * 프롬프트는 여전히 한글을 금지한다.
     *
     * usable() 이 고유명사가 남은 것을 받아 주게 됐지만, 그건 **차선을 버리지 않는 것**이고
     * 요구는 그대로다 — 온전히 옮긴 쪽이 낫다. 프롬프트에서 금지를 빼면 모델이 쉬운 길로 가
     * 절반만 옮긴 것이 늘고, 그중 상당수는 usable() 의 '절반' 선에 걸려 결국 버려진다.
     *
     * 금지만 하면 모델이 빈칸을 내놓으므로 대신 무엇을 쓸지도 말해야 한다. 그 짝을 고정한다.
     */
    @Test
    @DisplayName("일본어·중국어 프롬프트는 한글을 금지하고 음역 방법을 알려 준다")
    void 프롬프트가_한글을_금지() {
        for (Lang lang : List.of(Lang.JA, Lang.ZH)) {
            String p = TranslationClient.systemFor(lang);
            assertThat(p).as("%s 프롬프트가 한글 금지를 말해야 한다", lang)
                         .containsIgnoringCase("hangul");
            // 금지만 하면 모델이 빈칸을 내놓는다. 대신 무엇을 쓸지도 말해야 한다.
            assertThat(p).as("%s 프롬프트가 음역 방법을 말해야 한다", lang)
                         .containsIgnoringCase("transliterate");
        }
    }

    /** 영어는 로마자를 쓰므로 한글 금지가 필요 없다 — 괜히 넣어 규칙을 늘리지 않는다. */
    @Test
    @DisplayName("영어 프롬프트는 그대로 둔다")
    void 영어_프롬프트() {
        assertThat(TranslationClient.systemFor(Lang.EN)).contains("Revised Romanization");
    }
}
