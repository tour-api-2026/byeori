package com.byeori.domain.review;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 리뷰 신고. venue_reports와 같은 구조 — 운영진이 status로 처리 상태를 관리한다. */
@Entity
@Table(name = "review_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reviewId;
    private Long userId;
    private String reason;

    @Column(columnDefinition = "text")
    private String detail;

    private String status;
    private LocalDateTime createdAt;

    public ReviewReport(Long reviewId, Long userId, String reason, String detail) {
        this.reviewId = reviewId;
        this.userId = userId;
        this.reason = reason;
        this.detail = detail;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }
}
