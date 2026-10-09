package com.byeori.domain.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAuthRepository extends JpaRepository<SocialAuth, Long> {

    /** 로그인할 때 쓰는 길. 이 조합이 곧 사람을 가리킨다. */
    Optional<SocialAuth> findByProviderAndProviderUserId(String provider, String providerUserId);

    List<SocialAuth> findByUserIdOrderByCreatedAtAsc(Long userId);

    boolean existsByUserIdAndProvider(Long userId, String provider);

    void deleteByUserIdAndProvider(Long userId, String provider);

    long countByUserId(Long userId);
}
