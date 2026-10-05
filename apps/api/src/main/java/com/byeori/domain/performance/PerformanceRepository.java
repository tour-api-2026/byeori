package com.byeori.domain.performance;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /**
     * 정렬 미지정이면 페이지 간 중복·누락이 생긴다. 포스터 있는 것 → 최근 시작 순으로 고정한다.
     *
     * state 는 저장된 컬럼이 아니라 :today 기준으로 따진다. 그 컬럼은 동기화 시점 값이라
     * 시간이 지나면 틀어진다(Performance.stateOn 주석 참고). 판정은 그 메서드와 같은 규칙이다.
     */
    @Query("""
            select p from Performance p
            where (:state is null
                   or (:state = 'ENDED'
                       and p.endDate is not null and p.endDate < :today)
                   or (:state = 'ONGOING'
                       and p.startDate is not null and p.endDate is not null
                       and p.startDate <= :today and p.endDate >= :today)
                   or (:state = 'UPCOMING'
                       and (p.endDate is null or p.endDate >= :today)
                       and (p.startDate is null or p.endDate is null or p.startDate > :today)))
              and (:genre is null or p.genre = :genre)
              and (:venueId is null or p.venueId = :venueId)
              and (:keyword is null or p.title like %:keyword%)
              and (:traditional is null or p.traditional = :traditional)
            order by case when p.posterImageUrl is null or p.posterImageUrl = '' then 1 else 0 end,
                     p.startDate desc, p.id asc
            """)
    Page<Performance> search(@Param("state") String state,
                             @Param("today") LocalDate today,
                             @Param("genre") String genre,
                             @Param("venueId") Long venueId,
                             @Param("keyword") String keyword,
                             @Param("traditional") Boolean traditional,
                             Pageable pageable);

    /**
     * 장소 상세에 띄울 행사 — 진행 중·예정만.
     *
     * 끝난 행사는 DB 에 그대로 두되 여기서 거른다. 한 장소에 과거 공연이 수백 건 쌓일 수
     * 있어 보여주지 않을 행을 응답에 담지 않는다. endDate >= today 가 곧 '끝나지 않았다'로,
     * Performance.stateOn 의 ENDED 조건과 같은 경계다(종료일 당일은 아직 진행 중).
     */
    @Query("""
            select p from Performance p
            where p.venueId = :venueId
              and (p.endDate is null or p.endDate >= :today)
            order by p.startDate asc, p.id asc
            """)
    List<Performance> findVisibleByVenue(@Param("venueId") Long venueId, @Param("today") LocalDate today);

    /** AI 루트 후보: 그날 열리는, 좌표가 있는 행사. */
    @Query("""
            select p from Performance p
            where p.startDate <= :date and p.endDate >= :date
              and p.lat between :minLat and :maxLat
              and p.lng between :minLng and :maxLng
            order by p.traditional desc, p.id asc
            """)
    List<Performance> findOnDateInBounds(@Param("date") LocalDate date,
                                         @Param("minLat") BigDecimal minLat,
                                         @Param("maxLat") BigDecimal maxLat,
                                         @Param("minLng") BigDecimal minLng,
                                         @Param("maxLng") BigDecimal maxLng,
                                         Pageable pageable);
}
