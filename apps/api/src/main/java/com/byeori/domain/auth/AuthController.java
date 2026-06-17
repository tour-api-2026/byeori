package com.byeori.domain.auth;

import com.byeori.domain.auth.dto.AuthDtos.*;
import com.byeori.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 소셜 로그인·토큰 리프레시·로그아웃·현재 사용자. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService service;

    @PostMapping("/auth/social")
    public ApiResponse<TokenResponse> social(@RequestBody SocialLoginRequest req) {
        return ApiResponse.ok(service.socialLogin(req));
    }

    @PostMapping("/auth/token/refresh")
    public ApiResponse<TokenPair> refresh(@RequestBody RefreshRequest req) {
        return ApiResponse.ok(service.refresh(req));
    }

    /** 무상태: 클라이언트가 토큰 폐기. 서버는 200만 응답. */
    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null);
    }

    @GetMapping("/users/me")
    public ApiResponse<UserSummary> me(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.me(userId));
    }
}
