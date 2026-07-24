package com.byeori.global.auth;

import com.byeori.global.exception.BadRequestException;
import com.byeori.global.security.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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

    private final AuthProperties props;
    private final RestClient http = RestClient.create();
    private final ObjectMapper om = new ObjectMapper();

    public GoogleClient(AuthProperties props) {
        this.props = props;
    }

    public SocialProfile verify(String idToken) {
        if (props.getGoogleClientId() == null || props.getGoogleClientId().isBlank()) {
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
            if (aud == null || !aud.equals(props.getGoogleClientId())) {
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
