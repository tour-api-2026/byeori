package com.byeori.global.security;

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
    private final String googleClientId;

    public AuthProperties(@Value("${byeori.auth.jwt-secret:}") String jwtSecret,
                          @Value("${byeori.auth.kakao-rest-key:}") String kakaoRestKey,
                          @Value("${byeori.auth.kakao-redirect-uri:}") String kakaoRedirectUri,
                          @Value("${byeori.auth.kakao-client-secret:}") String kakaoClientSecret,
                          @Value("${byeori.auth.google-client-id:}") String googleClientId) {
        this.jwtSecret = jwtSecret;
        this.kakaoRestKey = kakaoRestKey;
        this.kakaoRedirectUri = kakaoRedirectUri;
        this.kakaoClientSecret = kakaoClientSecret;
        this.googleClientId = googleClientId;
    }
}
