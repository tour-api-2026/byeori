package com.byeori.domain.feed;

/**
 * 추천이 뜬 이유. 코드와 값을 나눠 담는다.
 *
 * 전에는 서버가 "관심 주제 · 문화" 처럼 **문장을 조립해서** 내려보냈다. 앱은 그것을 한
 * 덩어리로 받으므로 사전으로 가를 수 없어, 다국어로 만들 수 없었다. 코드만 주면 앱이
 * 제 언어로 문장을 만든다.
 *
 * param 은 코드마다 뜻이 다르다 — 거리(km), 남은 날수, 분류, 장르. 서식(소수점·단위)은
 * 언어마다 다르므로 서버는 숫자를 문자열로만 넘기고 꾸미지 않는다.
 */
public record FeedReason(String code, String param) {

    public static FeedReason nearby(double km) {
        // Locale 을 고정한다 — 서버 로케일이 쉼표 소수점이면 앱이 "1,2" 를 받게 된다.
        return new FeedReason("NEARBY", String.format(java.util.Locale.ROOT, "%.1f", km));
    }
    public static FeedReason favoriteRegion() { return new FeedReason("FAVORITE_REGION", null); }
    public static FeedReason favoriteTopic(String topic) { return new FeedReason("FAVORITE_TOPIC", topic); }
    /** 분류·장르를 그대로 보여 주는 경우. 앱이 제 분류값 사전으로 번역한다. */
    public static FeedReason category(String category) { return new FeedReason("CATEGORY", category); }
    public static FeedReason genre(String genre) { return new FeedReason("GENRE", genre); }
    public static FeedReason recommended() { return new FeedReason("RECOMMENDED", null); }
    public static FeedReason ongoing() { return new FeedReason("ONGOING", null); }
    public static FeedReason startsToday() { return new FeedReason("STARTS_TODAY", null); }
    public static FeedReason startsInDays(long days) {
        return new FeedReason("STARTS_IN_DAYS", String.valueOf(days));
    }
    public static FeedReason upcoming() { return new FeedReason("UPCOMING", null); }
}
