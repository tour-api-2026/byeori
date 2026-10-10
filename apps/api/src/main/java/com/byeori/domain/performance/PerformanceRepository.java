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
              and p.duplicateOf is null
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
              and p.duplicateOf is null
            order by p.startDate asc, p.id asc
            """)
    List<Performance> findVisibleByVenue(@Param("venueId") Long venueId, @Param("today") LocalDate today,
                                         Pageable pageable);

    /**
     * 좌표 주변에서 지금 열리거나 곧 열릴 행사. 가까운 순.
     *
     * 공연시설명으로 이은 행사가 1,659건인데, 이름이 우리 DB 에 없어 못 이은 게 1,419건
     * 남는다(소극장·사설 공연장 등). 그쪽도 좌표는 있으므로 "여기서 열린다" 대신
     * "이 근처에서 열린다"로는 보여줄 수 있다 — 거리만 맞으면 틀릴 수 없는 말이다.
     *
     * 사각 범위로 먼저 추린 뒤 제곱거리로 정렬한다. 정렬에는 순서만 맞으면 되고,
     * 경도는 위도보다 짧으므로(위도 36도에서 약 0.81배) 눌러서 비교한다.
     */
    @Query("""
            select p from Performance p
            where p.lat between :minLat and :maxLat
              and p.lng between :minLng and :maxLng
              and (p.endDate is null or p.endDate >= current_date)
              and p.duplicateOf is null
            order by (p.lat - :lat) * (p.lat - :lat)
                   + (p.lng - :lng) * (p.lng - :lng) * 0.656,
                     p.id asc
            """)
    List<Performance> findNearbyOngoing(@Param("lat") BigDecimal lat,
                                        @Param("lng") BigDecimal lng,
                                        @Param("minLat") BigDecimal minLat,
                                        @Param("maxLat") BigDecimal maxLat,
                                        @Param("minLng") BigDecimal minLng,
                                        @Param("maxLng") BigDecimal maxLng,
                                        Pageable pageable);

    /** AI 루트 후보: 그날 열리는, 좌표가 있는 행사. */
    @Query("""
            select p from Performance p
            where p.startDate <= :date and p.endDate >= :date
              and p.lat between :minLat and :maxLat
              and p.lng between :minLng and :maxLng
              and p.duplicateOf is null
            order by p.traditional desc, p.id asc
            """)

    List<Performance> findOnDateInBounds(@Param("date") LocalDate date,
                                         @Param("minLat") BigDecimal minLat,
                                         @Param("maxLat") BigDecimal maxLat,
                                         @Param("minLng") BigDecimal minLng,
                                         @Param("maxLng") BigDecimal maxLng,
                                         Pageable pageable);

    /**
     * 추천 후보: 지금 하거나 곧 시작하는 행사.
     *
     * 11,961건을 전부 점수 매길 수 없으므로 여기서 먼저 줄인다. 시작일 순으로 끊는 이유는
     * 임박한 것이 추천 가치가 높아서다 — 반년 뒤 공연을 오늘 권해도 할 수 있는 게 없다.
     */
    @Query("""
            select p from Performance p
            where (p.endDate is null or p.endDate >= :today)
              and (p.startDate is null or p.startDate <= :until)
              and p.duplicateOf is null
            order by p.startDate asc, p.id asc
            """)
    List<Performance> findUpcomingCandidates(@Param("today") LocalDate today,
                                             @Param("until") LocalDate until,
                                             Pageable pageable);

    /**
     * 중복 쓸기 대상 — 아직 끝나지 않고 기간이 분명한 행사. 가려진 줄도 함께 받는다.
     *
     * 가려진 줄을 빼면 숨김을 풀 수 없다. 매번 전체를 다시 판정해야 제목이 바뀐 뒤에도
     * 답이 맞는다(PerformanceDeduper 참고). 시작일·종료일이 같을 때만 비교하므로
     * 둘 중 하나라도 비면 애초에 후보가 아니다.
     */
    @Query("""
            select p from Performance p
            where p.startDate is not null
              and p.endDate is not null and p.endDate >= :today
            order by p.startDate asc, p.endDate asc, p.id asc
            """)
    List<Performance> findDedupeCandidates(@Param("today") LocalDate today);
}
