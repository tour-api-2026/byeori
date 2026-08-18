package com.byeori.domain.review;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {

    boolean existsByReviewIdAndUserId(Long reviewId, Long userId);
}
