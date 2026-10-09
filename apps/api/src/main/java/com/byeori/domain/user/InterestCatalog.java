package com.byeori.domain.user;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 고를 수 있는 관심사와, 그것이 가리키는 우리 분류.
 *
 * 온보딩 화면의 낱말과 서버 `category` 가 **다른 말을 쓴다**. 온보딩은 '관람·공연·음식'
 * 이라 하고 장소는 '문화·맛집·카페'라 한다. 매핑을 두지 않으면 고른 값이 아무것도
 * 못 걸러낸다. 화면 낱말을 그대로 저장하고(사용자가 고른 그대로 되보여 줘야 한다),
 * 쓰는 쪽에서 이 표로 옮긴다.
 *
 * 받는 값은 여기 적힌 것만 통과시킨다. 아무 문자열이나 받으면 오타 하나가 조용히
 * 쌓여 영영 아무것도 안 걸리는 관심사가 된다.
 */
public final class InterestCatalog {

    private InterestCatalog() {}

    public static final String TOPIC = "TOPIC";
    public static final String REGION = "REGION";

    /** 한 사람이 고를 수 있는 최대 개수. 전부 고르면 안 고른 것과 같아진다. */
    public static final int MAX_PER_KIND = 10;

    /** 온보딩 주제 → 장소 category. '공연'은 장소 분류로는 문화지만 행사 쪽이 본체다. */
    private static final Map<String, List<String>> TOPIC_TO_CATEGORY = Map.of(
            "관람", List.of("문화"),
            "체험", List.of("체험"),
            "공연", List.of("문화"),
            "한복", List.of("한복"),
            "음식", List.of("맛집", "카페"),
            "공예", List.of("공예", "전통시장")
    );

    /** '공연'을 고른 사람에게는 장소보다 행사를 더 보여 줘야 한다. */
    private static final Set<String> PERFORMANCE_TOPICS = Set.of("공연", "관람");

    private static final Set<String> REGIONS = Set.of(
            "서울", "경기", "부산", "대전", "대구", "제주", "전주", "여수", "인천", "광주");

    public static boolean isTopic(String v) { return TOPIC_TO_CATEGORY.containsKey(v); }

    public static boolean isRegion(String v) { return REGIONS.contains(v); }

    /** 고른 주제들이 가리키는 장소 분류. 중복은 합친다. */
    public static Set<String> categoriesOf(List<String> topics) {
        return topics.stream()
                .map(TOPIC_TO_CATEGORY::get)
                .filter(java.util.Objects::nonNull)
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 행사를 더 보고 싶어 하는 사람인지. ② 점수에서 행사 쪽 가중을 올리는 데 쓴다. */
    public static boolean prefersPerformances(List<String> topics) {
        return topics.stream().anyMatch(PERFORMANCE_TOPICS::contains);
    }
}
