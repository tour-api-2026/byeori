package com.byeori.domain.performance;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    java.util.Optional<Performance> findByKopisId(String kopisId);

    java.util.Optional<Performance> findByTourContentId(String tourContentId);

    java.util.Optional<Performance> findBySeoulId(String seoulId);

    @Modifying(clearAutomatically = true)
    @Query("update Performance p set p.avgRating = :avg, p.reviewCount = :cnt where p.id = :id")
    void updateRating(@Param("id") Long id, @Param("avg") BigDecimal avg, @Param("cnt") int cnt);

    /** 정렬 미지정이면 페이지 간 중복·누락이 생긴다. 포스터 있는 것 → 최근 시작 순으로 고정한다. */
    @Query("""
            select p from Performance p
            where (:state is null or p.state = :state)
              and (:genre is null or p.genre = :genre)
              and (:venueId is null or p.venueId = :venueId)
              and (:keyword is null or p.title like %:keyword%)
              and (:traditional is null or p.traditional = :traditional)
            order by case when p.posterImageUrl is null or p.posterImageUrl = '' then 1 else 0 end,
                     p.startDate desc, p.id asc
            """)
    Page<Performance> search(@Param("state") String state,
                             @Param("genre") String genre,
                             @Param("venueId") Long venueId,
                             @Param("keyword") String keyword,
                             @Param("traditional") Boolean traditional,
                             Pageable pageable);

    List<Performance> findByVenueIdOrderByStartDateAsc(Long venueId);
}
