package com.byeori.domain.review;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByVenueIdOrderByCreatedAtDesc(Long venueId);
    List<Review> findByPerformanceIdOrderByCreatedAtDesc(Long performanceId);
    List<Review> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByVenueId(Long venueId);
    long countByPerformanceId(Long performanceId);

    @Query("select avg(r.rating) from Review r where r.venueId = :id")
    Double avgByVenue(@Param("id") Long id);

    @Query("select avg(r.rating) from Review r where r.performanceId = :id")
    Double avgByPerformance(@Param("id") Long id);
}
