package com.byeori.domain.review;

import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.review.dto.ReviewReportRequest;
import com.byeori.domain.review.dto.ReviewRequest;
import com.byeori.domain.review.dto.ReviewResponse;
import com.byeori.domain.review.dto.ReviewUpdateRequest;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.content.ContentTarget;
import com.byeori.global.content.ContentType;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReviewService {

    /** reason 컬럼이 varchar(50) — 초과 시 DB에서 터지므로 서비스에서 막는다. */
    private static final int REASON_MAX = 50;

    private final ReviewRepository repo;
    private final VenueRepository venueRepo;
    private final PerformanceRepository performanceRepo;
    private final ReviewReportRepository reportRepo;

    public ReviewService(ReviewRepository repo, VenueRepository venueRepo,
                         PerformanceRepository performanceRepo, ReviewReportRepository reportRepo) {
        this.repo = repo;
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
        this.reportRepo = reportRepo;
    }

    public List<ReviewResponse> listByTarget(String targetType, Long targetId) {
        ContentTarget t = new ContentTarget(ContentType.from(targetType), targetId);
        List<Review> reviews = t.targetType() == ContentType.VENUE
                ? repo.findByVenueIdOrderByCreatedAtDesc(targetId)
                : repo.findByPerformanceIdOrderByCreatedAtDesc(targetId);
        return reviews.stream().map(ReviewResponse::from).toList();
    }

    public List<ReviewResponse> listMine(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(ReviewResponse::from).toList();
    }

    @Transactional
    public ReviewResponse create(Long userId, ReviewRequest req) {
        validateRating(req.rating());
        ContentTarget t = new ContentTarget(ContentType.from(req.targetType()), req.targetId());
        Review saved = repo.save(new Review(userId, t.performanceId(), t.venueId(), req.rating(), req.content()));
        recalc(t);
        return ReviewResponse.from(saved);
    }

    @Transactional
    public ReviewResponse update(Long userId, Long id, ReviewUpdateRequest req) {
        validateRating(req.rating());
        Review review = repo.findById(id).orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다."));
        if (!review.getUserId().equals(userId)) {
            throw new BadRequestException("REVIEW_FORBIDDEN", "본인 리뷰만 수정할 수 있습니다.");
        }
        review.update(req.rating(), req.content());
        recalc(ContentTarget.of(review.getPerformanceId(), review.getVenueId()));
        return ReviewResponse.from(review);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Review review = repo.findById(id).orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다."));
        if (!review.getUserId().equals(userId)) {
            throw new BadRequestException("REVIEW_FORBIDDEN", "본인 리뷰만 삭제할 수 있습니다.");
        }
        ContentTarget t = ContentTarget.of(review.getPerformanceId(), review.getVenueId());
        repo.delete(review);
        recalc(t);
    }

    /** 리뷰 신고. 구글 UGC 정책이 요구하는 신고 수단 — 운영진이 status로 후속 처리한다. */
    @Transactional
    public void report(Long userId, Long reviewId, ReviewReportRequest req) {
        Review review = repo.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "리뷰를 찾을 수 없습니다."));
        String reason = (req != null && req.reason() != null) ? req.reason().trim() : null;
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("REPORT_INVALID", "신고 사유는 필수입니다.");
        }
        if (reason.length() > REASON_MAX) {
            throw new BadRequestException("REPORT_INVALID", "신고 사유가 너무 깁니다.");
        }
        if (review.getUserId().equals(userId)) {
            throw new BadRequestException("REPORT_SELF", "본인 리뷰는 신고할 수 없습니다.");
        }
        if (reportRepo.existsByReviewIdAndUserId(reviewId, userId)) {
            throw new BadRequestException("REPORT_DUPLICATE", "이미 신고한 리뷰입니다.");
        }
        reportRepo.save(new ReviewReport(reviewId, userId, reason, req.detail()));
    }

    /** 대상의 avg_rating·review_count 동기 재계산 */
    private void recalc(ContentTarget t) {
        if (t.targetType() == ContentType.VENUE) {
            Double avg = repo.avgByVenue(t.targetId());
            long cnt = repo.countByVenueId(t.targetId());
            venueRepo.updateRating(t.targetId(), scale(avg), (int) cnt);
        } else {
            Double avg = repo.avgByPerformance(t.targetId());
            long cnt = repo.countByPerformanceId(t.targetId());
            performanceRepo.updateRating(t.targetId(), scale(avg), (int) cnt);
        }
    }

    private BigDecimal scale(Double avg) {
        return BigDecimal.valueOf(avg == null ? 0.0 : avg).setScale(1, RoundingMode.HALF_UP);
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new BadRequestException("INVALID_RATING", "평점은 1~5 사이여야 합니다.");
        }
    }
}
