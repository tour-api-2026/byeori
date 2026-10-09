package com.byeori.domain.auth;

import com.byeori.domain.auth.dto.AuthDtos.*;
import com.byeori.global.response.ApiResponse;
import java.util.List;
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

    /** 아이디/비밀번호 로그인(현재 관리자 계정 전용). */
    @PostMapping("/auth/login")
    public ApiResponse<TokenResponse> login(@RequestBody LoginRequest req) {
        return ApiResponse.ok(service.login(req));
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

    /** 내 계정에 붙어 있는 로그인 수단. */
    @GetMapping("/users/me/social")
    public ApiResponse<List<AuthService.LinkedAccount>> linked(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.linkedAccounts(userId));
    }

    /**
     * 지금 로그인한 계정에 다른 소셜 계정을 잇는다.
     *
     * 몸체는 로그인과 **같은 모양**(provider + code/idToken/accessToken)이다. 화면이 평소
     * 로그인하듯 제공자 창을 띄우고, 받은 것을 그대로 여기로 보내면 된다.
     */
    @PostMapping("/users/me/social")
    public ApiResponse<List<AuthService.LinkedAccount>> link(@AuthenticationPrincipal Long userId,
                                                             @RequestBody SocialLoginRequest req) {
        return ApiResponse.ok(service.link(userId, req));
    }

    @DeleteMapping("/users/me/social/{provider}")
    public ApiResponse<List<AuthService.LinkedAccount>> unlink(@AuthenticationPrincipal Long userId,
                                                               @PathVariable("provider") String provider) {
        return ApiResponse.ok(service.unlink(userId, provider));
    }

    @PatchMapping("/users/me")
    public ApiResponse<UserSummary> updateMe(@AuthenticationPrincipal Long userId,
                                             @RequestBody UpdateProfileRequest req) {
        return ApiResponse.ok(service.updateMe(userId, req));
    }
}
