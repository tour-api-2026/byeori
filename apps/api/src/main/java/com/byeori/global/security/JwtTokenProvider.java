package com.byeori.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** HS256 JWT 발급/검증. subject=userId, claim "role". access 1h / refresh 14d. */
@Component
public class JwtTokenProvider {

    private static final Duration ACCESS_TTL = Duration.ofHours(1);
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private final SecretKey key;

    public JwtTokenProvider(AuthProperties props) {
        String secret = props.getJwtSecret();
        // HS256은 최소 256bit(32byte) 키 필요. 하드코딩 폴백은 토큰 위조 위험이라 금지 —
        // 미설정/취약 시 조용히 넘어가지 말고 부팅을 실패시킨다(JWT_SECRET 환경변수 필수).
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET(byeori.auth.jwt-secret) 미설정 또는 32바이트 미만. "
                    + "운영 배포에는 32바이트 이상의 안전한 시크릿을 환경변수로 주입해야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccess(Long userId, String role) {
        return build(userId, role, ACCESS_TTL, "access");
    }

    public String generateRefresh(Long userId, String role) {
        return build(userId, role, REFRESH_TTL, "refresh");
    }

    private String build(Long userId, String role, Duration ttl, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 후 Claims 반환. 실패 시 예외(JwtException). */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
