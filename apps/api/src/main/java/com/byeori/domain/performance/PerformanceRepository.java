package com.byeori.domain.performance;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    @Query("""
            select p from Performance p
            where (:state is null or p.state = :state)
              and (:genre is null or p.genre = :genre)
              and (:venueId is null or p.venueId = :venueId)
              and (:keyword is null or p.title like %:keyword%)
            """)
    Page<Performance> search(@Param("state") String state,
                             @Param("genre") String genre,
                             @Param("venueId") Long venueId,
                             @Param("keyword") String keyword,
                             Pageable pageable);

    List<Performance> findByVenueIdOrderByStartDateAsc(Long venueId);
}
