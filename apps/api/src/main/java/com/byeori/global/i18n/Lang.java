package com.byeori.global.i18n;

import java.util.Locale;

/**
 * 지원 언어. Accept-Language 로 이 네 값을 그대로 주고받는다.
 *
 * BCP-47 꼬리표(zh-Hans-CN 같은)를 해석하지 않는다 — 우리 앱은 늘 네 값 중 하나를
 * 보내고, 그 외는 브라우저가 제멋대로 보낸 것이라 어느 언어를 원하는지 알 수 없다.
 * 모르면 한국어다. 던지지 않는다 — 헤더 하나 때문에 500 이 나면 안 된다.
 */
public enum Lang {
    KO, EN, JA, ZH;

    public static Lang of(String header) {
        if (header == null || header.isBlank()) return KO;
        return switch (header.trim().toLowerCase(Locale.ROOT)) {
            case "en" -> EN;
            case "ja" -> JA;
            case "zh" -> ZH;
            default -> KO;
        };
    }
}
