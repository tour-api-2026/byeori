package com.byeori.domain.activity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상세 화면을 연 기록. 로그인한 사용자만 남는다. */
@Entity
@Table(name = "view_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ViewLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String targetType;   // VENUE | PERFORMANCE
    private Long targetId;
    private String source;       // HOME | SEARCH | MAP | SHARED | DETAIL
    private LocalDateTime viewedAt;

    public ViewLog(Long userId, String targetType, Long targetId, String source) {
        this.userId = userId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.source = source;
        this.viewedAt = LocalDateTime.now();
    }
}
