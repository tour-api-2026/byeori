package com.byeori.global.security;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 인증 관련 설정(JWT 시크릿·소셜 키). 키는 백엔드 환경변수로 주입, 미설정 시에도 부팅. */
@Component
@Getter
public class AuthProperties {

    private final String jwtSecret;
    private final String kakaoRestKey;
    private final String kakaoRedirectUri;
    /**
     * 카카오 콘솔에서 Client Secret 을 켜 두면 토큰 요청에 반드시 함께 보내야 한다.
     * 빠지면 KOE010(Bad client credentials)으로 거부된다 — 키 자체는 맞아도 그렇다.
     */
    private final String kakaoClientSecret;
    /**
     * 허용할 구글 OAuth 클라이언트 ID 목록.
     *
     * 구글은 플랫폼마다 클라이언트를 따로 만들게 하고(웹 1개 + Android 1개), id_token 의
     * aud 가 그중 하나로 온다. 하나만 허용하면 웹이 되면 앱이 막히고 앱이 되면 웹이 막힌다.
     * GOOGLE_CLIENT_IDS 에 쉼표로 나열하고, 없으면 기존 GOOGLE_CLIENT_ID 를 쓴다.
     */
    private final List<String> googleClientIds;

    public AuthProperties(@Value("${byeori.auth.jwt-secret:}") String jwtSecret,
                          @Value("${byeori.auth.kakao-rest-key:}") String kakaoRestKey,
                          @Value("${byeori.auth.kakao-redirect-uri:}") String kakaoRedirectUri,
                          @Value("${byeori.auth.kakao-client-secret:}") String kakaoClientSecret,
                          @Value("${byeori.auth.google-client-ids:}") String googleClientIds) {
        this.jwtSecret = jwtSecret;
        this.kakaoRestKey = kakaoRestKey;
        this.kakaoRedirectUri = kakaoRedirectUri;
        this.kakaoClientSecret = kakaoClientSecret;
        this.googleClientIds = parseIds(googleClientIds);
    }

    /** 이 aud 를 가진 구글 id_token 을 받아들일지. */
    public boolean isGoogleAudienceAllowed(String aud) {
        return aud != null && googleClientIds.contains(aud);
    }

    /** 손으로 이어 붙인 값이라 공백·빈 항목이 섞인다. */
    private static List<String> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
