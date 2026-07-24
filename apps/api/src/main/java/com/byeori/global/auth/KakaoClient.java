package com.byeori.global.auth;

import com.byeori.global.exception.BadRequestException;
import com.byeori.global.security.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 카카오 OAuth: 인가코드(code) → 토큰 → 사용자 정보.
 *  1) POST https://kauth.kakao.com/oauth/token (grant_type=authorization_code)
 *  2) GET  https://kapi.kakao.com/v2/user/me (Bearer access_token)
 */
@Component
@Slf4j
public class KakaoClient {

    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USERME_URL = "https://kapi.kakao.com/v2/user/me";

    private final AuthProperties props;
    private final RestClient http = RestClient.create();
    private final ObjectMapper om = new ObjectMapper();

    public KakaoClient(AuthProperties props) {
        this.props = props;
    }

    public SocialProfile verify(String code, String redirectUriOverride) {
        if (props.getKakaoRestKey() == null || props.getKakaoRestKey().isBlank()) {
            throw new BadRequestException("KAKAO_NOT_CONFIGURED", "카카오 REST 키가 설정되지 않았습니다.");
        }
        if (code == null || code.isBlank()) {
            throw new BadRequestException("KAKAO_CODE_REQUIRED", "카카오 인가코드(code)가 필요합니다.");
        }
        String redirectUri = (redirectUriOverride != null && !redirectUriOverride.isBlank())
                ? redirectUriOverride : props.getKakaoRedirectUri();
        try {
            String accessToken = requestToken(code, redirectUri);
            return requestProfile(accessToken);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("카카오 검증 실패: {}", e.getMessage());
            throw new BadRequestException("KAKAO_VERIFY_FAILED", "카카오 인증에 실패했습니다.");
        }
    }

    /** 네이티브 SDK 등에서 받은 access_token으로 바로 사용자 정보 조회(코드 교환 생략). */
    public SocialProfile verifyToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new BadRequestException("KAKAO_TOKEN_REQUIRED", "카카오 accessToken이 필요합니다.");
        }
        try {
            return requestProfile(accessToken);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("카카오 토큰 검증 실패: {}", e.getMessage());
            throw new BadRequestException("KAKAO_VERIFY_FAILED", "카카오 인증에 실패했습니다.");
        }
    }

    private String requestToken(String code, String redirectUri) throws Exception {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", props.getKakaoRestKey());
        if (redirectUri != null && !redirectUri.isBlank()) form.add("redirect_uri", redirectUri);
        form.add("code", code);

        String body = http.post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        JsonNode node = om.readTree(body);
        String accessToken = text(node, "access_token");
        if (accessToken == null) {
            throw new BadRequestException("KAKAO_TOKEN_FAILED", "카카오 토큰 발급에 실패했습니다.");
        }
        return accessToken;
    }

    private SocialProfile requestProfile(String accessToken) throws Exception {
        String body = http.get().uri(USERME_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(String.class);
        JsonNode node = om.readTree(body);
        String providerUserId = text(node, "id");
        JsonNode account = node.path("kakao_account");
        String email = text(account, "email");
        JsonNode profile = account.path("profile");
        String nickname = text(profile, "nickname");
        String image = text(profile, "profile_image_url");
        if (providerUserId == null) {
            throw new BadRequestException("KAKAO_PROFILE_FAILED", "카카오 사용자 정보를 가져오지 못했습니다.");
        }
        return new SocialProfile("KAKAO", providerUserId, email, nickname, image);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull() || v.asText().isBlank()) ? null : v.asText();
    }
}
