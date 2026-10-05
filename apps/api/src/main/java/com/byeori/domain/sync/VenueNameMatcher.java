package com.byeori.domain.sync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * KOPIS 공연시설명으로 우리 장소를 찾는다.
 *
 * 좌표로 잇지 않는 이유: 같은 건물·부지의 다른 시설이 0~10m 거리에 있어 거리로는 갈리지
 * 않았고(국악 공연이 갤러리·둘레길로 잡혔다), 행사 쪽에 이름이 없어 맞는지 확인할 방법도
 * 없었다. 시설명은 공연목록 응답에 이미 들어 있어(fcltynm) 추가 호출 없이 비교할 수 있다.
 *
 * 퍼지 매칭은 쓰지 않는다. 정규화한 이름이 정확히 같을 때만 잇는다 — 커버리지를 조금 잃는
 * 대신(최근 표본 기준 공연의 46%가 연결됐다) 틀린 연결을 만들지 않는다. 못 맞춘 쪽은
 * 대부분 소극장·사설 공연장이라 애초에 관광 명소 DB에 없다.
 */
public final class VenueNameMatcher {

    private VenueNameMatcher() {}

    /** "강릉아트센터 (구. 강릉문화예술관)" 처럼 괄호 안에 옛 이름·지역을 덧붙여 온다. */
    private static final Pattern BRACKETS = Pattern.compile("\\(.*?\\)|\\[.*?]");
    /** 같은 곳을 "3.15아트센터" / "3·15 아트센터" 로 다르게 적는다. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s·‧⋅,.\\-_'\"]");

    /**
     * 비교용으로 다듬은 이름. 비교에만 쓰고 저장하지 않는다.
     * 맞출 수 없는 이름(빈 값, 괄호만 있는 값)은 null 을 준다.
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = unescape(raw);
        s = BRACKETS.matcher(s).replaceAll("");
        s = SEPARATORS.matcher(s).replaceAll("");
        s = s.toLowerCase();
        return s.isBlank() ? null : s;
    }

    /** KOPIS 응답은 XML 이라 &amp; 같은 엔티티가 그대로 실려 온다. */
    private static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    /**
     * 장소 id→이름 목록을 정규화 이름 색인으로 만든다.
     *
     * 정규화 후 이름이 겹치는 장소가 둘 이상이면 색인에서 뺀다. 어느 쪽인지 알 수 없는데
     * 아무거나 고르면 그게 바로 틀린 연결이 된다.
     */
    public static Map<String, Long> buildIndex(Map<Long, String> venues) {
        Map<String, Long> index = new HashMap<>();
        Set<String> duplicated = new HashSet<>();
        venues.forEach((id, name) -> {
            String key = normalize(name);
            if (key == null) return;
            if (index.putIfAbsent(key, id) != null) duplicated.add(key);
        });
        duplicated.forEach(index::remove);
        return index;
    }

    /** 색인에서 이 시설명에 해당하는 장소 id. 없으면 null. */
    public static Long find(Map<String, Long> index, String facilityName) {
        String key = normalize(facilityName);
        return key == null ? null : index.get(key);
    }
}
