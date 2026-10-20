package com.byeori.domain.translation;

import com.byeori.global.external.OpenAiClient;
import com.byeori.global.i18n.Lang;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 장소·행사 이름을 묶음으로 번역한다.
 *
 * 고유명사라 로마자 표기법이 중요하다 — 경복궁은 "Blessing Palace" 가 아니라
 * "Gyeongbokgung Palace" 다. 프롬프트에 규칙과 대조표를 넣어 잡는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TranslationClient {

    private final OpenAiClient openAi;

    // 언어마다 규칙이 다르다. 처음에는 로마자 규칙 하나를 모든 언어에 줬더니 일본어·중국어
    // 칸에도 영어 로마자가 들어왔다(100건 표본). 그래서 공통 규칙 + 언어별 규칙으로 나눈다.
    private static final String COMMON = """
        You translate Korean place and event names for a travel app used by foreign visitors.
        Rules:
        - Keep well-known official names in the target language when they exist.
        - Do not translate a proper noun into its literal meaning.
        - Keep it short: this is a label on a card, not a sentence.
        - Do not add explanations. Do not drop or add parenthesised parts such as (Seoul).
        - Return every input exactly once, echoing the input unchanged in "src".
        - Seoul's five royal palaces are NOT interchangeable: 경복궁 Gyeongbokgung, 창덕궁 Changdeokgung,
          창경궁 Changgyeonggung, 덕수궁 Deoksugung, 경희궁 Gyeonghuigung.
        """;

    private static final String EN = """
        Target language: English.
        Use the official Revised Romanization of Korean for proper nouns, then add the English
        common noun: 경복궁 -> "Gyeongbokgung Palace", 북촌한옥마을 -> "Bukchon Hanok Village",
        국립중앙박물관 -> "National Museum of Korea", 창경궁 -> "Changgyeonggung Palace".
        """;

    private static final String JA = """
        Target language: Japanese. Write in Japanese script only, never in Latin letters
        (except where the Korean name itself contains Latin letters or digits).
        Use the established Japanese name written in kanji when one exists:
        경복궁 -> "景福宮", 창덕궁 -> "昌徳宮", 창경궁 -> "昌慶宮", 북촌한옥마을 -> "北村韓屋村",
        국립중앙박물관 -> "国立中央博物館", 광화문 -> "光化門", 한강공원 -> "漢江公園".
        Otherwise translate the common nouns (公園, 寺, 市場, 博物館...) and write the Korean proper
        part in katakana following how Japanese guidebooks transliterate Korean.
        """;

    private static final String ZH = """
        Target language: Simplified Chinese. Write in Chinese characters only, never in Latin letters
        (except where the Korean name itself contains Latin letters or digits).
        Use the established Mainland Chinese name when one exists:
        경복궁 -> "景福宫", 창덕궁 -> "昌德宫", 창경궁 -> "昌庆宫", 북촌한옥마을 -> "北村韩屋村",
        국립중앙박물관 -> "韩国国立中央博物馆", 광화문 -> "光化门", 한강공원 -> "汉江公园".
        Otherwise translate the common nouns (公园, 寺, 市场, 博物馆...) and transliterate the Korean
        proper part with the usual Chinese guidebook rendering of Korean names (hanja readings
        when the hanja is well known).
        """;

    private static String systemFor(Lang lang) {
        return COMMON + switch (lang) { case JA -> JA; case ZH -> ZH; default -> EN; };
    }

    /** 원문 → 번역. 실패하거나 AI 가 빠뜨린 항목은 결과에 없다. */
    public Map<String, String> translate(Lang lang, List<String> names) {
        if (lang == Lang.KO || names.isEmpty()) return Map.of();
        String user = String.join("\n", names);
        try {
            JsonNode res = openAi.completeJson(systemFor(lang), user, "translations", SCHEMA, 0.2);
            Map<String, String> got = parse(res, names);
            if (lang == Lang.JA || lang == Lang.ZH) got.values().removeIf(TranslationClient::hasHangul);
            return got;
        } catch (Exception e) {
            // 한 묶음이 실패해도 다음 묶음은 돌아야 한다. 못 한 행은 다음 배치에서 다시 집는다.
            log.warn("번역 실패 lang={} 건수={} : {}", lang, names.size(), e.toString());
            return Map.of();
        }
    }

    /**
     * 일본어·중국어 칸에 한글이 남은 번역은 못 쓴다. 100건 표본에서 "고척동 고인돌" 을 그대로
     * 돌려주거나 "寺(서울)" 처럼 한글을 섞은 것이 나왔다. 버리면 해시가 안 찍혀 다음 배치가
     * 다시 시도한다(한글뿐인 이름이 계속 실패하면 그 몇 건만 매일 재시도한다).
     */
    static boolean hasHangul(String s) {
        return s.codePoints().anyMatch(c -> (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF)
                || (c >= 0x3130 && c <= 0x318F));
    }

    /**
     * 응답에서 쓸 수 있는 짝만 고른다.
     *
     * 요청하지 않은 원문은 버린다 — AI 가 지어낸 짝을 저장하면 엉뚱한 행에 번역이 붙는다.
     * 빈 번역도 버린다. 셋 중 하나가 깨져도 나머지 둘은 살린다.
     */
    static Map<String, String> parse(JsonNode res, List<String> asked) {
        Map<String, String> out = new LinkedHashMap<>();
        if (res == null || !res.isObject()) return out;
        JsonNode items = res.get("items");
        if (items == null || !items.isArray()) return out;
        Set<String> want = new HashSet<>(asked);
        for (JsonNode it : items) {
            // path() 는 null 값 노드를 NullNode 로 돌려주고, asText(null) 은 거기서 "null" 문자열을 준다.
            // 값이 문자열이 아니면 버린다.
            JsonNode s = it.path("src");
            JsonNode o = it.path("out");
            if (!s.isTextual() || !o.isTextual()) continue;
            String src = s.asText();
            String tr = o.asText().trim();
            if (tr.isEmpty() || !want.contains(src)) continue;
            out.put(src, tr);
        }
        return out;
    }

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("items", Map.of(
                    "type", "array",
                    "items", Map.of(
                            "type", "object",
                            "properties", Map.of("src", Map.of("type", "string"),
                                                 "out", Map.of("type", "string")),
                            "required", List.of("src", "out"),
                            "additionalProperties", false))),
            "required", List.of("items"),
            "additionalProperties", false);
}
