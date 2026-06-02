package com.byeori.domain.wishlist;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.domain.wishlist.dto.WishlistRequest;
import com.byeori.domain.wishlist.dto.WishlistResponse;
import com.byeori.global.content.ContentTarget;
import com.byeori.global.content.ContentType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository repo;
    private final VenueRepository venueRepo;
    private final PerformanceRepository performanceRepo;

    public WishlistService(WishlistRepository repo, VenueRepository venueRepo, PerformanceRepository performanceRepo) {
        this.repo = repo;
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
    }

    public List<WishlistResponse> list(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public WishlistResponse add(Long userId, WishlistRequest req) {
        ContentTarget target = new ContentTarget(ContentType.from(req.targetType()), req.targetId());
        Wishlist existing = target.targetType() == ContentType.PERFORMANCE
                ? repo.findByUserIdAndPerformanceId(userId, target.targetId()).orElse(null)
                : repo.findByUserIdAndVenueId(userId, target.targetId()).orElse(null);
        Wishlist saved = existing != null ? existing
                : repo.save(new Wishlist(userId, target.performanceId(), target.venueId()));
        return toResponse(saved);
    }

    @Transactional
    public void remove(Long userId, String targetType, Long targetId) {
        ContentTarget target = new ContentTarget(ContentType.from(targetType), targetId);
        if (target.targetType() == ContentType.PERFORMANCE) {
            repo.findByUserIdAndPerformanceId(userId, targetId).ifPresent(repo::delete);
        } else {
            repo.findByUserIdAndVenueId(userId, targetId).ifPresent(repo::delete);
        }
    }

    private WishlistResponse toResponse(Wishlist w) {
        ContentTarget t = ContentTarget.of(w.getPerformanceId(), w.getVenueId());
        if (t.targetType() == ContentType.VENUE) {
            Venue v = venueRepo.findById(t.targetId()).orElse(null);
            return new WishlistResponse(w.getId(), "VENUE", t.targetId(),
                    v == null ? null : v.getName(), v == null ? null : v.getImageUrl(),
                    v == null ? BigDecimal.ZERO : v.getAvgRating(), v == null || v.getReviewCount() == null ? 0 : v.getReviewCount());
        }
        Performance p = performanceRepo.findById(t.targetId()).orElse(null);
        return new WishlistResponse(w.getId(), "PERFORMANCE", t.targetId(),
                p == null ? null : p.getTitle(), p == null ? null : p.getPosterImageUrl(),
                p == null ? BigDecimal.ZERO : p.getAvgRating(), p == null || p.getReviewCount() == null ? 0 : p.getReviewCount());
    }
}
