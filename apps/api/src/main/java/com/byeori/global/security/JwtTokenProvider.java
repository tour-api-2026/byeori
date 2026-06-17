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
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            // HS256은 최소 256bit(32byte) 키 필요. 부팅은 막지 않되 안전한 기본값으로 패딩.
            secret = (secret == null ? "" : secret) + "dev-only-secret-please-change-min-32-bytes-long-xxxxx";
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
