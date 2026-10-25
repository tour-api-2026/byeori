package com.byeori.domain.sync;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공사 다국어 관광정보(EngService2·JpnService2·ChsService2)의 제목을 다룬다.
 *
 * 다국어판은 국문판과 **contentId 가 다르다.** 같은 경복궁이 국문 126508, 영문 264337 이다.
 * 그래서 id 로는 이을 수 없고 장소를 따로 맞춰야 한다.
 *
 * 다행히 제목이 원문을 달고 온다 — "Gyeongbokgung Palace (경복궁)". 영문은 15,382건 중
 * 15,319건(99%), 중문 간체는 90%, 일문은 74% 가 그렇다. 이 원문이 가장 믿을 만한 단서다.
 *
 * 좌표만으로 잇지 않는 이유 — 같은 좌표에 여러 장소가 등록돼 있어 영문 기준 367곳이
 * 후보 둘 이상이 된다. 반대로 이름만으로 이으면 '송정해수욕장'(부산/강원), '삼각산'처럼
 * **같은 이름의 다른 장소** 147건이 잘못 붙는다. 그래서 둘을 함께 본다.
 */
public final class MultilangTitle {

    private MultilangTitle() {
    }

    /** 괄호·대괄호 묶음 하나. 중첩된 것은 안쪽만 잡힌다. */
    private static final Pattern GROUP = Pattern.compile("[(\\[]([^()\\[\\]]*)[)\\]]");
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");

    /**
     * 제목에 달려 온 한국어 원문. 없으면 빈 문자열.
     *
     * 한글이 든 괄호 묶음 중 **가장 뒤의 것**을 쓴다. 'APLSHOP [Tax Refund Shop](APLSHOP)'
     * 처럼 괄호가 여럿이고, 뒤쪽이 원문인 경우가 대부분이다.
     */
    public static String koreanPart(String title) {
        if (title == null || title.isBlank()) return "";
        List<String> found = new ArrayList<>();
        Matcher m = GROUP.matcher(title);
        while (m.find()) {
            String g = m.group(1).trim();
            if (!g.isEmpty() && HANGUL.matcher(g).find()) found.add(g);
        }
        return found.isEmpty() ? "" : found.get(found.size() - 1);
    }

    /**
     * 같은 장소로 볼 만큼 좌표가 가까운가. 위도 0.009°(약 1km), 경도 0.011°(위도 36도에서 약 1km).
     *
     * 1km 는 넉넉해 보이지만 **이름이 이미 같은 상태에서** 쓰는 조건이다. 여기서 걸러내려는
     * 것은 수십 미터 오차가 아니라 부산 송정해수욕장과 강원 송정해수욕장 같은 동명이소다.
     * 공사 국문판과 다국어판은 따로 관리돼 같은 장소도 좌표가 조금씩 다르므로, 좁게 잡으면
     * 맞는 짝까지 버린다(완전일치만 요구하면 영문 3,803곳이 1,826곳으로 줄었다).
     */
    public static boolean nearby(double lat1, double lng1, double lat2, double lng2) {
        return Math.abs(lat1 - lat2) <= 0.009 && Math.abs(lng1 - lng2) <= 0.011;
    }
}
