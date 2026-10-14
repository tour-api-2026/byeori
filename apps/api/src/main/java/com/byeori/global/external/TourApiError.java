package com.byeori.global.external;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * data.go.kr 게이트웨이가 알려 준 실패 사유를 꺼낸다.
 *
 * 왜 필요한가: 장소 증분 동기화가 2026-09-21 부터 24일간 매일 실패했는데 아무도 몰랐다.
 * 기록에 남은 것이 "동기화 목록 조회 실패" 한 줄이어서다. 그 한 줄로는 잠깐 끊긴 것과
 * 인증키가 죽은 것을 구별할 수 없다 — 앞쪽은 다음 회차에 저절로 낫고, 뒤쪽은 사람이
 * 손대지 않으면 영원히 안 낫는다. 실제 사유는 응답 본문에 또박또박 적혀 있었다.
 *
 * <pre>
 * &lt;errMsg&gt;SERVICE_KEY_IS_NOT_REGISTERED_ERROR&lt;/errMsg&gt;
 * &lt;returnAuthMsg&gt;등록되지 않은 서비스키&lt;/returnAuthMsg&gt;
 * &lt;returnReasonCode&gt;30&lt;/returnReasonCode&gt;
 * </pre>
 */
public final class TourApiError {

    private TourApiError() {}

    /**
     * 게이트웨이가 쓰는 사유 태그들. **앞쪽을 먼저 쓴다.**
     *
     * 한 응답에 여러 개가 함께 온다. returnAuthMsg 는 "등록되지 않은 서비스키" 처럼
     * 사람이 읽을 말이고 errMsg 는 코드다. 사람이 먼저 읽을 쪽을 고른다 — 본문에서
     * 코드가 먼저 나오므로, 하나의 정규식으로 합치면 코드가 설명을 덮는다.
     */
    private static final List<Pattern> REASON_TAGS = List.of(
            tag("returnAuthMsg"), tag("resultMsg"), tag("errMsg"));

    /** XML 과 JSON 양쪽 꼴을 한 번에 본다. */
    private static Pattern tag(String name) {
        return Pattern.compile("<" + name + ">(.*?)</" + name + ">"
                + "|\"" + name + "\"\\s*:\\s*\"(.*?)\"");
    }

    /** 사람이 손대야 하는 사유들. 다음 회차에 저절로 낫지 않는다. */
    private static final Pattern NEEDS_HUMAN = Pattern.compile(
            "SERVICE_KEY_IS_NOT_REGISTERED|SERVICE_ACCESS_DENIED|TEMPORARILY_DISABLE"
                    + "|LIMITED_NUMBER_OF_SERVICE_REQUESTS|SERVICE_KEY_IS_NOT_REGISTERED_ERROR"
                    + "|등록되지 않은 서비스키|서비스 접근 거부|활용기간|요청 제한");

    /** 응답 본문에서 사유를 꺼낸다. 못 찾으면 null. */
    public static String reasonOf(String body) {
        if (body == null || body.isBlank()) return null;
        for (Pattern p : REASON_TAGS) {
            Matcher m = p.matcher(body);
            if (!m.find()) continue;
            String found = m.group(1) != null ? m.group(1) : m.group(2);
            if (found != null && !found.isBlank()) return found.trim();
        }
        return null;
    }

    /**
     * 예외 사슬에서 사유를 꺼낸다.
     *
     * RestClient 는 4xx·5xx 응답 본문을 예외 메시지에 실어 준다. 그 본문에 사유가 들어
     * 있는데, 지금은 감싸는 과정에서 버려지고 있었다. 사슬을 끝까지 따라가 찾는다.
     */
    public static String reasonOf(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String reason = reasonOf(t.getMessage());
            if (reason != null) return reason;
            if (t.getCause() == t) break;
        }
        // 사유 태그가 없으면 맨 아래 예외의 말이라도 남긴다(타임아웃·DNS 등).
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String msg = root.getMessage();
        return msg == null || msg.isBlank() ? root.getClass().getSimpleName() : msg.trim();
    }

    /** 이 사유는 사람이 손대야 하는가. 그렇다면 경고가 아니라 오류로 알려야 한다. */
    public static boolean needsHuman(String reason) {
        return reason != null && NEEDS_HUMAN.matcher(reason).find();
    }
}
