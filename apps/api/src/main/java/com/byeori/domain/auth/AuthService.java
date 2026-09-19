package com.byeori.domain.auth;

import com.byeori.domain.auth.dto.AuthDtos.*;
import com.byeori.domain.upload.UploadedImageRepository;
import com.byeori.domain.user.User;
import com.byeori.domain.user.UserRepository;
import com.byeori.global.auth.GoogleClient;
import com.byeori.global.auth.KakaoClient;
import com.byeori.global.auth.SocialProfile;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import com.byeori.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 검증 → users 업서트 → JWT 발급/리프레시. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UploadedImageRepository uploadedImageRepository;
    private final KakaoClient kakaoClient;
    private final GoogleClient googleClient;
    private final JwtTokenProvider tokenProvider;

    @Value("${byeori.auth.admin-id:admin}")
    private String adminId;
    @Value("${byeori.auth.admin-password:byeori1234}")
    private String adminPassword;
    // 스토어 심사용 계정. 미설정(빈 값)이면 심사 로그인 경로 자체가 비활성.
    @Value("${byeori.auth.review-id:}")
    private String reviewId;
    @Value("${byeori.auth.review-password:}")
    private String reviewPassword;

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
                    u.syncFromProvider(profile.email());
                    return u;
                })
                .orElseGet(() -> userRepository.save(User.social(
                        profile.provider(), profile.providerUserId(),
                        profile.nickname(), profile.email(), profile.imageUrl())));

        String access = tokenProvider.generateAccess(user.getId(), user.getRole());
        String refresh = tokenProvider.generateRefresh(user.getId(), user.getRole());
        return new TokenResponse(access, refresh, toSummary(user));
    }

    /**
     * 아이디/비밀번호 로그인. 관리자 계정(ADMIN) 또는 스토어 심사용 계정(USER)만 통과.
     * 자격증명은 byeori.auth.admin-id/admin-password(운영은 ADMIN_* 환경변수),
     * 심사용은 byeori.auth.review-id/review-password(REVIEW_* 환경변수, 미설정 시 비활성).
     */
    @Transactional
    public TokenResponse login(LoginRequest req) {
        String id = (req != null && req.id() != null) ? req.id().trim() : null;
        String pw = (req != null) ? req.password() : null;

        User user;
        if (matches(adminId, adminPassword, id, pw)) {
            user = userRepository
                    .findByAuthProviderAndProviderUserId("ADMIN", adminId)
                    .orElseGet(() -> userRepository.save(User.admin(adminId, "관리자")));
        } else if (matches(reviewId, reviewPassword, id, pw)) {
            user = userRepository
                    .findByAuthProviderAndProviderUserId("REVIEW", reviewId)
                    .orElseGet(() -> userRepository.save(User.review(reviewId, "심사용 계정")));
        } else {
            throw new BadRequestException("INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String access = tokenProvider.generateAccess(user.getId(), user.getRole());
        String refresh = tokenProvider.generateRefresh(user.getId(), user.getRole());
        return new TokenResponse(access, refresh, toSummary(user));
    }

    /** 설정된 자격증명과 요청이 일치하는지. 설정값이 비어 있으면(미설정) 항상 false. */
    private static boolean matches(String expectedId, String expectedPw, String id, String pw) {
        return expectedId != null && !expectedId.isBlank()
                && expectedPw != null && !expectedPw.isBlank()
                && expectedId.equals(id) && expectedPw.equals(pw);
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

    /** 프로필 사진으로 받는 값: 우리 업로드 경로(절대 URL이어도 된다). */
    private static final Pattern UPLOAD_URL =
            Pattern.compile("^(https?://[^/]+)?/api/v1/uploads/images/([0-9a-f]{32})$");

    /**
     * 닉네임·프로필 사진 수정.
     *
     * 사진은 지우거나(null), 지금 사진을 그대로 두거나, 본인이 올린 이미지로만 바꿀 수 있다.
     * 아무 외부 URL이나 받으면 남의 업로드나 추적용 이미지를 프로필에 걸 수 있다.
     */
    @Transactional
    public UserSummary updateMe(Long userId, UpdateProfileRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        String name = req == null || req.name() == null ? "" : req.name().strip();
        if (name.length() < 2 || name.length() > 20) {
            throw new BadRequestException("INVALID_NAME", "닉네임은 2~20자로 입력해 주세요.");
        }

        String image = req.profileImageUrl() == null || req.profileImageUrl().isBlank()
                ? null : req.profileImageUrl().strip();
        if (image != null && !image.equals(user.getProfileImageUrl())) {
            Matcher m = UPLOAD_URL.matcher(image);
            boolean mine = m.matches() && uploadedImageRepository.findById(m.group(2))
                    .map(img -> userId.equals(img.getUploaderId()))
                    .orElse(false);
            if (!mine) {
                throw new BadRequestException("INVALID_IMAGE", "직접 올린 사진만 프로필로 쓸 수 있어요.");
            }
        }

        user.editProfile(name, image);
        return toSummary(user);
    }

    private UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getName(), u.getEmail(), u.getProfileImageUrl());
    }
}
