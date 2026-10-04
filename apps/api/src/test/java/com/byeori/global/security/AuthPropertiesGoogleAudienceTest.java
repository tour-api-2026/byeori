package com.byeori.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구글 OAuth 클라이언트가 플랫폼마다 따로 필요해(웹 1개 + Android 1개) id_token 의 aud 가
 * 둘 중 하나로 온다. 어느 쪽이든 받아들여야 웹과 앱이 같은 백엔드를 쓸 수 있다.
 */
class AuthPropertiesGoogleAudienceTest {

    private static final String WEB = "566537373981-web.apps.googleusercontent.com";
    private static final String ANDROID = "566537373981-android.apps.googleusercontent.com";

    private AuthProperties props(String googleClientIds) {
        return new AuthProperties("", "", "", "", googleClientIds);
    }

    @Test
    @DisplayName("쉼표로 나열한 클라이언트 ID를 모두 허용한다")
    void 여러_개_허용() {
        AuthProperties p = props(WEB + "," + ANDROID);
        assertThat(p.isGoogleAudienceAllowed(WEB)).isTrue();
        assertThat(p.isGoogleAudienceAllowed(ANDROID)).isTrue();
    }

    @Test
    @DisplayName("목록에 없는 aud 는 거부한다")
    void 모르는_aud_거부() {
        assertThat(props(WEB).isGoogleAudienceAllowed("other.apps.googleusercontent.com")).isFalse();
    }

    @Test
    @DisplayName("단일 값도 그대로 동작한다 — 기존 GOOGLE_CLIENT_ID 설정 호환")
    void 단일_값_호환() {
        assertThat(props(WEB).isGoogleAudienceAllowed(WEB)).isTrue();
    }

    @Test
    @DisplayName("공백과 빈 항목은 무시한다 — .env 에서 손으로 이어 붙이다 보면 섞인다")
    void 공백_정리() {
        AuthProperties p = props("  " + WEB + " , , " + ANDROID + "  ,");
        assertThat(p.getGoogleClientIds()).containsExactly(WEB, ANDROID);
        assertThat(p.isGoogleAudienceAllowed(ANDROID)).isTrue();
    }

    @Test
    @DisplayName("미설정이면 목록이 비고 어떤 aud 도 통과시키지 않는다")
    void 미설정() {
        assertThat(props("").getGoogleClientIds()).isEmpty();
        assertThat(props("").isGoogleAudienceAllowed(WEB)).isFalse();
        assertThat(props("").isGoogleAudienceAllowed(null)).isFalse();
    }

    @Test
    @DisplayName("aud 가 null 이면 거부한다")
    void null_aud() {
        assertThat(props(WEB).isGoogleAudienceAllowed(null)).isFalse();
    }
}
