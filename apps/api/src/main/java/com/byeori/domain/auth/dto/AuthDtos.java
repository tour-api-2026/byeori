package com.byeori.domain.auth.dto;

/** 인증 도메인 요청/응답 DTO 모음. */
public final class AuthDtos {

    private AuthDtos() {}

    /** POST /auth/social 요청. provider="kakao"|"google". 카카오: code(웹) 또는 accessToken(네이티브 SDK). */
    public record SocialLoginRequest(String provider, String code, String idToken, String accessToken, String redirectUri) {}

    /** POST /auth/token/refresh 요청. */
    public record RefreshRequest(String refreshToken) {}

    /** 로그인/리프레시 토큰 쌍. */
    public record TokenResponse(String accessToken, String refreshToken, UserSummary user) {}

    /** 토큰 갱신 응답(사용자 정보 없음). */
    public record TokenPair(String accessToken, String refreshToken) {}

    /** 사용자 요약. */
    public record UserSummary(Long id, String name, String email, String profileImageUrl) {}
}
