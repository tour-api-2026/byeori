package com.byeori.domain.translation;

import com.byeori.global.external.OpenAiClient;
import com.byeori.global.i18n.Lang;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import java.util.stream.Collectors;
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
        NEVER leave Hangul (Korean letters) in the output. Every syllable must come out as
        kanji, hiragana or katakana. An answer containing Hangul is discarded.
        Use the established Japanese name written in kanji when one exists:
        경복궁 -> "景福宮", 창덕궁 -> "昌徳宮", 창경궁 -> "昌慶宮", 북촌한옥마을 -> "北村韓屋村",
        국립중앙박물관 -> "国立中央博物館", 광화문 -> "光化門", 한강공원 -> "漢江公園".
        Otherwise translate the common nouns (公園, 寺, 市場, 博物館...) and write the Korean proper
        part in katakana following how Japanese guidebooks transliterate Korean.
        Many names are native Korean words with no kanji at all (옛날두부, 웃장, 오얏리, 예원).
        Do not give up and do not echo the Korean: transliterate the sound into katakana
        (떡볶이 -> "トッポッキ", 비빔밥 -> "ビビンバ").
        """;

    private static final String ZH = """
        Target language: Simplified Chinese. Write in Chinese characters only, never in Latin letters
        (except where the Korean name itself contains Latin letters or digits).
        NEVER leave Hangul (Korean letters) in the output. Every syllable must come out as a
        Chinese character. An answer containing Hangul is discarded.
        Use the established Mainland Chinese name when one exists:
        경복궁 -> "景福宫", 창덕궁 -> "昌德宫", 창경궁 -> "昌庆宫", 북촌한옥마을 -> "北村韩屋村",
        국립중앙박물관 -> "韩国国立中央博物馆", 광화문 -> "光化门", 한강공원 -> "汉江公园".
        Otherwise translate the common nouns (公园, 寺, 市场, 博物馆...) and transliterate the Korean
        proper part with the usual Chinese guidebook rendering of Korean names (hanja readings
        when the hanja is well known).
        Many names are native Korean words with no hanja at all (옛날두부, 웃장, 오얏리, 예원).
        Do not give up and do not echo the Korean: translate the common noun and transliterate
        the proper part with 音译 characters (떡볶이 -> "炒年糕", 비빔밥 -> "拌饭").
        """;

    static String systemFor(Lang lang) {
        return COMMON + switch (lang) { case JA -> JA; case ZH -> ZH; default -> EN; };
    }

    /** AI 를 부르지 못했다(꺼짐·시간 초과·거절). 모델이 일부 행을 빠뜨린 것과 구분하려고 따로 둔다. */
    public static class UnavailableException extends RuntimeException {
        public UnavailableException(String msg, Throwable cause) { super(msg, cause); }
    }

    /**
     * 원문 → 번역. AI 가 빠뜨리거나 쓸 수 없어 버린 항목은 결과에 없다(정상 반환).
     * 호출 자체가 실패하면 UnavailableException — 배치가 이것을 "행이 못 번역됨" 으로 세면
     * 장애 하루에 멀쩡한 행이 전부 포기 상태로 넘어간다.
     */
    public Map<String, String> translate(Lang lang, List<String> names) {
        if (lang == Lang.KO || names.isEmpty()) return Map.of();
        String user = String.join("\n", names);
        try {
            JsonNode res = openAi.completeJson(systemFor(lang), user, "translations", SCHEMA, 0.2);
            if (res == null) throw new UnavailableException("응답 없음(비활성·실패·거절)", null);
            Map<String, String> got = parse(res, names);
            int answered = got.size();
            if (lang == Lang.JA || lang == Lang.ZH) {
                // 버린 짝 몇 개는 남긴다. 숫자만으로는 모델이 무엇을 내놨는지 알 수 없어,
                // 프롬프트를 고쳐야 하는지 이름 자체가 어려운지 판단할 수 없었다.
                String sample = got.entrySet().stream().filter(e -> hasHangul(e.getValue())).limit(3)
                        .map(e -> e.getKey() + " -> " + e.getValue()).collect(Collectors.joining(", "));
                got.values().removeIf(TranslationClient::hasHangul);
                if (!sample.isEmpty()) log.info("한글이 남아 버린 표본 lang={} : {}", lang, sample);
            }
            // 부르는 쪽은 '못 한 행' 을 한 숫자로만 센다. 그 안에 아주 다른 두 가지가 섞여 있다 —
            // 모델이 아예 빠뜨린 것은 다시 물으면 되고, 한글이 남아 버린 것은 다시 물어도 같다.
            // 어느 쪽인지 모르면 무엇을 고쳐야 하는지 알 수 없어 여기서 갈라 적는다.
            if (got.size() < names.size())
                log.info("번역 묶음 lang={} 물은 것={} 쓸 수 있음={} 한글이 남아 버림={} 응답에 없음={}",
                        lang, names.size(), got.size(), answered - got.size(), names.size() - answered);
            return got;
        } catch (UnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new UnavailableException("번역 호출 실패 lang=" + lang + " 건수=" + names.size(), e);
        }
    }

    private static final String PROSE = """
        You translate a Korean travel-guide description of a place into the target language for
        foreign visitors. Translate faithfully and completely: do not summarise, shorten, or add anything.
        Keep paragraph breaks. Keep place names consistent with their well-known official form in the
        target language (e.g. 경복궁 -> Gyeongbokgung Palace in English). Return only the translation.
        """;

    private static final Map<String, Object> TEXT_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("text", Map.of("type", "string")),
            "required", List.of("text"),
            "additionalProperties", false);

    /**
     * 한 장소의 긴 설명 하나를 번역한다. 화면 뒤에서만 불린다(DetailTranslator) — 사용자가 기다리지 않는다.
     *
     * translate() 를 쓰지 않는 이유 — 그쪽 프롬프트는 "카드 라벨처럼 짧게" 를 요구해 문단을
     * 줄여 버리고, 원문을 src 로 그대로 되돌려야 하는데 긴 글은 그게 자주 어긋난다.
     * 실패하거나 빈 값이면 null 이고 던지지 않는다. 부르는 쪽이 한국어로 떨어뜨린다.
     */
    public String translateText(Lang lang, String text) {
        if (lang == Lang.KO || text == null || text.isBlank()) return null;
        try {
            JsonNode res = openAi.completeJsonLongText(systemForProse(lang), text, "description", TEXT_SCHEMA, 0.2);
            if (res == null || !res.path("text").isTextual()) return null;
            String tr = res.get("text").asText().trim();
            return tr.isEmpty() ? null : tr;
        } catch (Exception e) {
            log.warn("설명 번역 실패 lang={} 길이={} : {}", lang, text.length(), e.toString());
            return null;
        }
    }

    private static String systemForProse(Lang lang) {
        String name = switch (lang) { case JA -> "Japanese"; case ZH -> "Simplified Chinese"; default -> "English"; };
        return PROSE + "Target language: " + name + ".\n";
    }

    /**
     * 일본어·중국어 칸에 한글이 남은 번역은 못 쓴다. 100건 표본에서 "고척동 고인돌" 을 그대로
     * 돌려주거나 "寺(서울)" 처럼 한글을 섞은 것이 나왔다. 버리면 해시가 안 찍혀 다음 배치가
     * 다시 시도한다(한글뿐인 이름이 계속 실패하면 그 몇 건만 매일 재시도한다).
     */
    static boolean hasHangul(String s) {
        // 음절(가~힣)만 본다. 낱자(ㄱ, ㅂㅍ)는 제목에 디자인으로 쓴 것이라 번역문에도 남아야
        // 한다(`SUPERPOSITION #3. PP ㅂㅍ`, `제5회 ㄱ기억연극전`). CSV 임포터
        // (scripts/import-translations.py)도 음절만 본다 — 두 곳의 기준이 같아야 한다.
        return s.codePoints().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
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
        // 앞뒤 공백이 든 제목이 있다. AI 는 "echoing the input unchanged" 를 지시해도 src 에서
        // 공백을 떼어 돌려준다. 글자 그대로만 맞추면 그 행은 매번 버려지고 세 밤 뒤 포기 상태가
        // 된다(영어 포기 14건 중 13건이 이것이었다). 그래서 공백을 뗀 꼴로도 찾는다.
        //
        // 공백만 다른 두 이름을 같은 묶음에서 물었으면 어느 쪽인지 알 수 없다. 그 꼴은 빼 둔다 —
        // 틀린 행에 번역이 붙는 것보다 다음 배치가 다시 시도하는 것이 낫다.
        Map<String, String> loose = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (String a : asked) {
            String k = a.strip();
            if (k.isEmpty() || k.equals(a)) continue;   // 글자 그대로 찾히니 느슨한 꼴이 필요 없다
            if (loose.put(k, a) != null) ambiguous.add(k);
        }
        for (JsonNode it : items) {
            // path() 는 null 값 노드를 NullNode 로 돌려주고, asText(null) 은 거기서 "null" 문자열을 준다.
            // 값이 문자열이 아니면 버린다.
            JsonNode s = it.path("src");
            JsonNode o = it.path("out");
            if (!s.isTextual() || !o.isTextual()) continue;
            String src = s.asText();
            String tr = o.asText().trim();
            // 글자 그대로가 먼저다. 못 찾으면 공백을 뗀 꼴로 한 번 더 본다.
            String key = want.contains(src) ? src
                       : ambiguous.contains(src.strip()) ? null : loose.get(src.strip());
            if (tr.isEmpty() || key == null) continue;
            out.put(key, tr);
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
