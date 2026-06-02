package com.byeori.domain.review;

import com.byeori.domain.performance.PerformanceRepository;
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

    private final ReviewRepository repo;
    private final VenueRepository venueRepo;
    private final PerformanceRepository performanceRepo;

    public ReviewService(ReviewRepository repo, VenueRepository venueRepo, PerformanceRepository performanceRepo) {
        this.repo = repo;
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
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
