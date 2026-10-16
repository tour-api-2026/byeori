package com.byeori.domain.itinerary;

/**
 * 구간마다 걸을지 탈지 정한다.
 *
 * 카카오모빌리티는 자동차 경로만 준다. 고궁에서 고궁으로 걷는 동선에 차 경로를 그리면
 * 길도 시간도 틀린다(수원화성 성곽길이 "5분"으로 나온다 — 걸으면 19분이다). 그렇다고
 * 전부 도보로 두면 부산 감천문화마을→자갈치 6km 를 "73분 걸으세요"가 된다.
 *
 * 그래서 구간마다 가른다. 기준은 직선거리다 — 실제 경로 거리를 알려면 이미 호출을
 * 해야 하는데, 어느 쪽에 호출할지를 정하는 자리라서 쓸 수 없다.
 */
public final class RouteModePlanner {

    private RouteModePlanner() {}

    public static final String WALK = "WALK";
    public static final String DRIVE = "DRIVE";
    public static final String AUTO = "AUTO";

    /** 지구 반지름(m). 구면 근사로 충분하다 — 기준값과 비교하는 데만 쓴다. */
    private static final double EARTH_R = 6_371_000;

    /** 두 점 사이 직선거리(m). */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_R * 2 * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /**
     * 이 구간을 어떻게 갈 것인가.
     *
     * 사용자가 WALK/DRIVE 를 고르면 거리와 상관없이 따른다 — 비 오는 날은 다 타고 싶고,
     * 날 좋은 날은 다 걷고 싶다. 모르는 값은 AUTO 로 본다. 경로 보기는 읽기 동작이라
     * 오타로 400 을 주는 것보다 그럴듯한 답을 주는 쪽이 낫다.
     */
    public static String legMode(String requested, double meters, int thresholdM) {
        if (WALK.equals(requested) || DRIVE.equals(requested)) return requested;
        return meters <= thresholdM ? WALK : DRIVE;
    }
}
