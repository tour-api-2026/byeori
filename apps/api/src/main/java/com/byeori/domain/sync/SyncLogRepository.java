package com.byeori.domain.sync;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    /** 증분 동기화의 커서. 마지막으로 성공한 회차가 어디까지 받았는지를 말해 준다. */
    Optional<SyncLog> findTopByProviderAndTargetTypeAndStatusOrderByStartedAtDesc(
            String provider, String targetType, String status);
}
