package com.byeori.domain.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    void deleteByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    /** 내가 차단한 사용자 id 목록 — 리뷰 목록 필터링에 쓴다. */
    @Query("select b.blockedUserId from UserBlock b where b.userId = :userId order by b.createdAt desc")
    List<Long> findBlockedUserIds(@Param("userId") Long userId);
}
