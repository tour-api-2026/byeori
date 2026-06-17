package com.byeori.domain.wishlist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {
    List<Wishlist> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Wishlist> findByUserIdAndPerformanceId(Long userId, Long performanceId);
    Optional<Wishlist> findByUserIdAndVenueId(Long userId, Long venueId);
    boolean existsByUserIdAndPerformanceId(Long userId, Long performanceId);
    boolean existsByUserIdAndVenueId(Long userId, Long venueId);
}
