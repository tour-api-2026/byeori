package com.byeori.domain.auth;

import com.byeori.domain.auth.dto.AuthDtos.*;
import com.byeori.domain.upload.UploadedImageRepository;
import com.byeori.domain.user.SocialAuth;
import com.byeori.domain.user.SocialAuthRepository;
import com.byeori.domain.user.User;
import com.byeori.domain.user.UserRepository;
import com.byeori.global.auth.GoogleClient;
import com.byeori.global.auth.KakaoClient;
import com.byeori.global.auth.SocialProfile;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import com.byeori.global.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import java.util.List;
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
    private final SocialAuthRepository socialAuthRepository;
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
        SocialProfile profile = verify(req);

        User user = findOrCreate(profile);

        String access = tokenProvider.generateAccess(user.getId(), user.getRole());
        String refresh = tokenProvider.generateRefresh(user.getId(), user.getRole());
        return new TokenResponse(access, refresh, toSummary(user));
    }

    // ── 계정 연결 ─────────────────────────────

    /** 내가 지금 쓸 수 있는 로그인 수단. */
    @Transactional(readOnly = true)
    public List<LinkedAccount> linkedAccounts(Long userId) {
        return socialAuthRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(a -> new LinkedAccount(a.getProvider(), a.getCreatedAt()))
                .toList();
    }

    /**
     * 지금 로그인한 계정에 다른 소셜 계정을 잇는다.
     *
     * 이메일이 같다고 자동으로 잇지 않는다. 카카오는 애초에 이메일을 주지 않고, 검증되지
     * 않은 이메일로 자동 연결하면 남의 이메일을 제 소셜 계정에 넣은 뒤 그 서비스로 들어와
     * 남의 벼리 계정을 차지할 수 있다. 그래서 **로그인한 사람이 직접** 잇는다.
     *
     * 그 소셜 계정이 이미 다른 벼리 계정에 붙어 있으면 거부한다. 합치기는 찜·루트·리뷰를
     * 옮기고 한쪽을 지우는 되돌릴 수 없는 일이라, 여기서 조용히 해치울 것이 아니다.
     */
    @Transactional
    public List<LinkedAccount> link(Long userId, SocialLoginRequest req) {
        SocialProfile profile = verify(req);
        var existing = socialAuthRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (existing.isPresent()) {
            if (existing.get().getUserId().equals(userId)) {
                return linkedAccounts(userId);          // 이미 내 것 — 두 번 눌러도 탈나지 않게
            }
            throw new BadRequestException("SOCIAL_ALREADY_LINKED",
                    "이 계정은 다른 벼리 계정에 연결되어 있어요. 그 계정으로 로그인해 주세요.");
        }
        socialAuthRepository.save(new SocialAuth(userId, profile.provider(), profile.providerUserId()));
        return linkedAccounts(userId);
    }

    /**
     * 연결을 끊는다.
     *
     * 마지막 하나는 끊지 못한다 — 끊는 순간 로그인할 길이 사라져 제 계정에 영영 못 들어간다.
     * 아이디/비밀번호가 있는 계정(ADMIN·REVIEW)은 소셜이 0개여도 들어갈 수 있지만, 그 둘은
     * 소셜을 연결하지도 않으므로 여기 올 일이 없다.
     */
    @Transactional
    public List<LinkedAccount> unlink(Long userId, String provider) {
        String p = provider == null ? "" : provider.toUpperCase();
        if (!socialAuthRepository.existsByUserIdAndProvider(userId, p)) {
            throw new NotFoundException("SOCIAL_NOT_LINKED", "연결되지 않은 계정이에요.");
        }
        if (socialAuthRepository.countByUserId(userId) <= 1) {
            throw new BadRequestException("SOCIAL_LAST_ONE",
                    "마지막 로그인 수단은 끊을 수 없어요. 다른 계정을 먼저 연결해 주세요.");
        }
        socialAuthRepository.deleteByUserIdAndProvider(userId, p);
        return linkedAccounts(userId);
    }

    /** 로그인과 연결이 같은 검증을 쓴다. 한쪽만 고쳐져 어긋나는 일이 없게 한 곳에 둔다. */
    private SocialProfile verify(SocialLoginRequest req) {
        if (req == null || req.provider() == null) {
            throw new BadRequestException("PROVIDER_REQUIRED", "provider가 필요합니다.");
        }
        return switch (req.provider().toLowerCase()) {
            case "kakao" -> (req.accessToken() != null && !req.accessToken().isBlank())
                    ? kakaoClient.verifyToken(req.accessToken())
                    : kakaoClient.verify(req.code(), req.redirectUri());
            case "google" -> (req.code() != null && !req.code().isBlank())
                    ? googleClient.verifyCode(req.code(), req.redirectUri())
                    : googleClient.verify(req.idToken());
            default -> throw new BadRequestException("UNSUPPORTED_PROVIDER",
                    "지원하지 않는 제공자입니다: " + req.provider());
        };
    }

    /** 연결된 수단 한 줄. 제공자 쪽 id 는 내보내지 않는다 — 화면이 쓸 일이 없다. */
    public record LinkedAccount(String provider, java.time.LocalDateTime linkedAt) {}

    /**
     * 소셜 프로필로 사람을 찾는다. 없으면 새로 만든다.
     *
     * **social_auths 를 먼저 본다.** 한 사람이 카카오·구글을 둘 다 연결했을 때, 어느 쪽으로
     * 들어와도 같은 계정이 나와야 한다. users 의 (auth_provider, provider_user_id) 로만
     * 찾으면 수단을 하나밖에 못 보므로 연결해 둔 쪽이 무시된다.
     *
     * users 쪽 조회는 **옮기다 빠진 줄을 위한 그물**이다. 마이그레이션(V25)이 기존 사용자를
     * 모두 옮기지만, 그때 없던 줄이 있거나 옮기기가 일부 실패해도 로그인이 끊기지 않아야 한다.
     * 그런 줄을 만나면 그 자리에서 social_auths 에 채워 넣는다.
     */
    private User findOrCreate(SocialProfile profile) {
        var linked = socialAuthRepository
                .findByProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (linked.isPresent()) {
            User user = userRepository.findById(linked.get().getUserId())
                    .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
            user.syncFromProvider(profile.email());
            return user;
        }

        var legacy = userRepository
                .findByAuthProviderAndProviderUserId(profile.provider(), profile.providerUserId());
        if (legacy.isPresent()) {
            User user = legacy.get();
            user.syncFromProvider(profile.email());
            socialAuthRepository.save(new SocialAuth(user.getId(), profile.provider(), profile.providerUserId()));
            return user;
        }

        User created = userRepository.save(User.social(
                profile.provider(), profile.providerUserId(),
                profile.nickname(), profile.email(), profile.imageUrl()));
        socialAuthRepository.save(new SocialAuth(created.getId(), profile.provider(), profile.providerUserId()));
        return created;
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
