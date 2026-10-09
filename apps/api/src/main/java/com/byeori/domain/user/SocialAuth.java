package com.byeori.domain.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 사람이 가진 로그인 수단 하나.
 *
 * users 에 (auth_provider, provider_user_id) 가 그대로 남아 있지만 그건 '처음 가입한 수단'일
 * 뿐이고, 로그인할 때 실제로 보는 건 이 표다. 한 사람이 여러 줄을 가질 수 있다.
 *
 * access_token 칸은 테이블에 있지만 넣지 않는다. 우리가 그 토큰으로 할 일이 없고,
 * 남의 서비스 토큰을 보관하면 샜을 때 피해가 우리 밖으로 번진다.
 */
@Entity
@Table(name = "social_auths")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** KAKAO | GOOGLE */
    private String provider;

    private String providerUserId;

    private LocalDateTime createdAt;

    public SocialAuth(Long userId, String provider, String providerUserId) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.createdAt = LocalDateTime.now();
    }
}
