package com.byeori.domain.auth;

import com.byeori.domain.auth.dto.AuthDtos.*;
import com.byeori.domain.user.User;
import com.byeori.domain.user.UserRepository;
import com.byeori.global.auth.GoogleClient;
import com.byeori.global.auth.KakaoClient;
import com.byeori.global.auth.SocialProfile;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import com.byeori.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 검증 → users 업서트 → JWT 발급/리프레시. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final KakaoClient kakaoClient;
    private final GoogleClient googleClient;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public TokenResponse socialLogin(SocialLoginRequest req) {
        if (req == null || req.provider() == null) {
            throw new BadRequestException("PROVIDER_REQUIRED", "provider가 필요합니다.");
        }
        SocialProfile profile = switch (req.provider().toLowerCase()) {
            case "kakao" -> (req.accessToken() != null && !req.accessToken().isBlank())
                    ? kakaoClient.verifyToken(req.accessToken())        // 네이티브 SDK 경로
                    : kakaoClient.verify(req.code(), req.redirectUri()); // 웹 OAuth 경로
            case "google" -> googleClient.verify(req.idToken());
            default -> throw new BadRequestException("UNSUPPORTED_PROVIDER",
                    "지원하지 않는 제공자입니다: " + req.provider());
        };

        User user = userRepository
                .findByAuthProviderAndProviderUserId(profile.provider(), profile.providerUserId())
                .map(u -> {
                    u.updateProfile(profile.nickname(), profile.email(), profile.imageUrl());
                    return u;
                })
                .orElseGet(() -> userRepository.save(User.social(
                        profile.provider(), profile.providerUserId(),
                        profile.nickname(), profile.email(), profile.imageUrl())));

        String access = tokenProvider.generateAccess(user.getId(), user.getRole());
        String refresh = tokenProvider.generateRefresh(user.getId(), user.getRole());
        return new TokenResponse(access, refresh, toSummary(user));
    }

    /** 무상태 리프레시: refresh 토큰 검증 후 새 토큰쌍 발급. */
    @Transactional(readOnly = true)
    public TokenPair refresh(RefreshRequest req) {
        if (req == null || req.refreshToken() == null || req.refreshToken().isBlank()) {
            throw new BadRequestException("REFRESH_TOKEN_REQUIRED", "refreshToken이 필요합니다.");
        }
        Claims claims;
        try {
            claims = tokenProvider.parse(req.refreshToken());
        } catch (Exception e) {
            throw new BadRequestException("INVALID_REFRESH_TOKEN", "유효하지 않은 refreshToken입니다.");
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BadRequestException("INVALID_REFRESH_TOKEN", "refresh 토큰이 아닙니다.");
        }
        Long userId = Long.valueOf(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        String access = tokenProvider.generateAccess(user.getId(), user.getRole());
        String refresh = tokenProvider.generateRefresh(user.getId(), user.getRole());
        return new TokenPair(access, refresh);
    }

    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        return toSummary(user);
    }

    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getName(), u.getEmail(), u.getProfileImageUrl());
    }
}
