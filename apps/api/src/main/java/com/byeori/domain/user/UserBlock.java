package com.byeori.domain.user;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 차단(단방향). userId가 blockedUserId를 차단한 기록. */
@Entity
@Table(name = "user_blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long blockedUserId;
    private LocalDateTime createdAt;

    public UserBlock(Long userId, Long blockedUserId) {
        this.userId = userId;
        this.blockedUserId = blockedUserId;
        this.createdAt = LocalDateTime.now();
    }
}
