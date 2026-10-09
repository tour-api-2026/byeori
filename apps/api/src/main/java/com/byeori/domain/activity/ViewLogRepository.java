package com.byeori.domain.activity;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ViewLogRepository extends JpaRepository<ViewLog, Long> {

    /** 같은 사람이 같은 곳을 금방 다시 본 적이 있는지. 뒤로가기로 오간 것을 여러 번으로 세지 않는다. */
    boolean existsByUserIdAndTargetTypeAndTargetIdAndViewedAtAfter(
            Long userId, String targetType, Long targetId, LocalDateTime after);

    @Modifying
    @Query("delete from ViewLog v where v.viewedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
