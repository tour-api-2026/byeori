package com.byeori.domain.sync;

import java.util.regex.Pattern;

/**
 * 같은 행사를 두 출처에서 각각 받은 것인지 본다.
 *
 * KOPIS 와 서울 열린데이터가 같은 공연을 각자 올린다. 우리 쪽 중복 방지는 출처별 외부
 * id(kopis_id, seoul_id)로만 돌아서, 출처가 다르면 같은 행사가 두 줄로 남는다.
 * 2026-10-11 기준 진행 중·예정 3,325건에서 18쌍이 그런 경우였다.
 *
 * 유사도 비율은 쓸 수 없었다. "서울 바비큐 페스티벌" vs "서울뮤직페스티벌"(다른 행사)이
 * 0.71 인데, 실제 중복인 쌍이 0.61 로 더 낮게 나왔다 — 짧은 제목에서 비율이 뒤집힌다.
 * 장소도 기준이 못 된다: 18쌍 중 양쪽에 venue_id 가 있는 쌍이 0개였다.
 *
 * 그래서 **가장 긴 공통 토막**의 길이로 본다. 같은 데이터에서 기준을 바꿔 보면:
 * 6자 이상 18쌍(전부 진짜), 5자 이상 22쌍(늘어난 4쌍 중 3쌍이 다른 행사 — "국악관현악"
 * 5자가 겹친 서로 다른 연주회), 4자 이상 40쌍(늘어난 18쌍 전부 "페스티벌"이 겹친 것).
 * 6자로 끊으면 진짜 중복 몇 건을 놓치지만("헤이스트링"은 5자라 그냥 둔다), 다른 행사를
 * 하나로 합치는 쪽이 더 나쁘다.
 *
 * 이름만으로는 부족하다. 쓰는 쪽({@link PerformanceDeduper})에서 기간(시작일·종료일)이
 * 같고 출처가 다를 때만 이 판정을 묻는다. 18쌍 전부 종료일까지 같았다.
 *
 * 아직 틀릴 수 있는 모양이 남아 있다 — 다른 두 악단의 연주회가 시작일·종료일까지 같고
 * 이름 끝이 "…도립국악관현악단"처럼 6자 넘게 겹치면 하나로 합쳐진다. 지금 데이터에는
 * 없지만(그런 쌍은 기간이 달랐다) 생길 수 있다. 그래서 가리기만 하고 지우지 않으며,
 * 쓸기마다 새로 가린 수를 로그에 남긴다 — 숫자가 튀면 그때 보고 한 줄로 되돌린다.
 */
public final class PerformanceDuplicateMatcher {

    private PerformanceDuplicateMatcher() {}

    /** 공통 토막이 이 길이 이상이면 같은 행사로 본다. */
    public static final int MIN_COMMON = 6;

    /** "[세종문화회관] 2026 …" 처럼 괄호 안에 주최·장소·지역을 덧붙여 온다. */
    private static final Pattern BRACKETS = Pattern.compile("\\(.*?\\)|\\[.*?]|<.*?>|【.*?】|〈.*?〉");
    /** 한쪽은 "제4회", 다른 쪽은 회차를 안 쓴다. */
    private static final Pattern ORDINAL = Pattern.compile("제\\s*\\d+\\s*회");
    /** 한쪽은 "2026 …", 다른 쪽은 연도를 안 쓴다. */
    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    /** 공백·기호는 출처마다 달라서 비교에서 뺀다. */
    private static final Pattern NOISE = Pattern.compile("[^0-9a-z가-힣]");

    /** 비교용으로 다듬은 제목. 비교에만 쓰고 저장하지 않는다. 비면 null. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase();
        s = BRACKETS.matcher(s).replaceAll("");
        s = ORDINAL.matcher(s).replaceAll("");
        s = YEAR.matcher(s).replaceAll("");
        s = NOISE.matcher(s).replaceAll("");
        return s.isBlank() ? null : s;
    }

    /** 두 제목이 공유하는 가장 긴 토막의 길이. 비교할 수 없으면 0. */
    public static int commonLength(String titleA, String titleB) {
        String a = normalize(titleA);
        String b = normalize(titleB);
        if (a == null || b == null) return 0;

        // 바로 앞 행만 있으면 되므로 두 줄로 굴린다(제목이 짧아 비용은 어차피 작다).
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        int best = 0;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = a.charAt(i - 1) == b.charAt(j - 1) ? prev[j - 1] + 1 : 0;
                if (cur[j] > best) best = cur[j];
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return best;
    }

    /** 제목만 보고 같은 행사라 할 수 있는가. 기간·출처 조건은 부르는 쪽이 따진다. */
    public static boolean sameEvent(String titleA, String titleB) {
        return commonLength(titleA, titleB) >= MIN_COMMON;
    }
}
