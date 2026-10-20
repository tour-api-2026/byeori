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

    private static final String SYSTEM = """
        You translate Korean place and event names for a travel app.
        Rules:
        - Use the official Revised Romanization of Korean for proper nouns,
          then add the English common noun: 경복궁 -> "Gyeongbokgung Palace",
          북촌한옥마을 -> "Bukchon Hanok Village", 국립중앙박물관 -> "National Museum of Korea".
        - Keep well-known official English names when they exist.
        - Do not translate a proper noun into its literal meaning.
        - Keep it short: this is a label on a card, not a sentence.
        - Return every input exactly once, echoing the input in "src".
        """;

    /** 원문 → 번역. 실패하거나 AI 가 빠뜨린 항목은 결과에 없다. */
    public Map<String, String> translate(Lang lang, List<String> names) {
        if (lang == Lang.KO || names.isEmpty()) return Map.of();
        String user = "Target language: " + lang.name() + "\n" + String.join("\n", names);
        try {
            JsonNode res = openAi.completeJson(SYSTEM, user, "translations", SCHEMA);
            return parse(res, names);
        } catch (Exception e) {
            // 한 묶음이 실패해도 다음 묶음은 돌아야 한다. 못 한 행은 다음 배치에서 다시 집는다.
            log.warn("번역 실패 lang={} 건수={} : {}", lang, names.size(), e.toString());
            return Map.of();
        }
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
