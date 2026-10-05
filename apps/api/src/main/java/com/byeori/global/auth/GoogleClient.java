package com.byeori.global.auth;

import com.byeori.global.exception.BadRequestException;
import com.byeori.global.security.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 구글 OAuth: id_token 검증.
 *  GET https://oauth2.googleapis.com/tokeninfo?id_token=... → aud == GOOGLE_CLIENT_ID 확인.
 */
@Component
@Slf4j
public class GoogleClient {

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";

    private final AuthProperties props;
    private final RestClient http = RestClient.create();
    private final ObjectMapper om = new ObjectMapper();

    public GoogleClient(AuthProperties props) {
        this.props = props;
    }

    /**
     * 웹 로그인: 인가 코드 → 토큰 → id_token 검증.
     *
     * 브라우저가 id_token 을 직접 받는 implicit 방식은 쓰지 않는다. 그 방식에서는 인증을
     * 마쳐도 토큰이 페이지로 돌아오지 않아 로그인이 멈췄고(백엔드에 요청 자체가 안 왔다),
     * 원인을 바깥에서 특정하지 못했다. 같은 앱의 카카오 웹이 쓰는 코드 플로우로 맞춘다.
     */
    public SocialProfile verifyCode(String code, String redirectUri) {
        if (props.getGoogleWebClientId() == null || props.getGoogleWebClientId().isBlank()
                || props.getGoogleClientSecret() == null || props.getGoogleClientSecret().isBlank()) {
            throw new BadRequestException("GOOGLE_NOT_CONFIGURED",
                    "구글 웹 클라이언트 설정이 없습니다.");
        }
        if (code == null || code.isBlank()) {
            throw new BadRequestException("GOOGLE_CODE_REQUIRED", "구글 인가코드(code)가 필요합니다.");
        }
        String idToken;
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("code", code);
            form.add("client_id", props.getGoogleWebClientId());
            form.add("client_secret", props.getGoogleClientSecret());
            // 콘솔에 등록된 값과 한 글자도 다르면 redirect_uri_mismatch 가 난다.
            form.add("redirect_uri", redirectUri);

            String body = http.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            idToken = text(om.readTree(body), "id_token");
        } catch (Exception e) {
            log.warn("구글 코드 교환 실패: {}", e.getMessage());
            throw new BadRequestException("GOOGLE_CODE_EXCHANGE_FAILED", "구글 인증에 실패했습니다.");
        }
        if (idToken == null) {
            throw new BadRequestException("GOOGLE_IDTOKEN_MISSING", "구글 id_token을 받지 못했습니다.");
        }
        return verify(idToken);
    }

    public SocialProfile verify(String idToken) {
        if (props.getGoogleClientIds().isEmpty()) {
            throw new BadRequestException("GOOGLE_NOT_CONFIGURED", "구글 클라이언트 ID가 설정되지 않았습니다.");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new BadRequestException("GOOGLE_IDTOKEN_REQUIRED", "구글 idToken이 필요합니다.");
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(TOKENINFO_URL)
                    .queryParam("id_token", idToken)
                    .build(true)
                    .toUri();
            String body = http.get().uri(uri).retrieve().body(String.class);
            JsonNode node = om.readTree(body);

            String aud = text(node, "aud");
            if (!props.isGoogleAudienceAllowed(aud)) {
                throw new BadRequestException("GOOGLE_AUD_MISMATCH", "구글 토큰 대상(aud)이 일치하지 않습니다.");
            }
            String providerUserId = text(node, "sub");
            if (providerUserId == null) {
                throw new BadRequestException("GOOGLE_PROFILE_FAILED", "구글 사용자 정보를 가져오지 못했습니다.");
            }
            return new SocialProfile("GOOGLE", providerUserId,
                    text(node, "email"), text(node, "name"), text(node, "picture"));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("구글 검증 실패: {}", e.getMessage());
            throw new BadRequestException("GOOGLE_VERIFY_FAILED", "구글 인증에 실패했습니다.");
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull() || v.asText().isBlank()) ? null : v.asText();
    }
}
