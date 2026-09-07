package com.byeori.domain.venue;

import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    @Modifying(clearAutomatically = true)
    @Query("update Venue v set v.avgRating = :avg, v.reviewCount = :cnt where v.id = :id")
    void updateRating(@Param("id") Long id, @Param("avg") BigDecimal avg, @Param("cnt") int cnt);

    java.util.List<Venue> findByCreatedByUserIdOrderByCreatedAtDesc(Long userId);

    java.util.Optional<Venue> findByTourContentId(String tourContentId);

    /** 실시간 조회 결과를 자체 정보(한복 혜택·평점)와 붙이기 위한 일괄 조회. */
    java.util.List<Venue> findByTourContentIdIn(java.util.Collection<String> tourContentIds);

    /**
     * 시드로 들어온 장소는 tour_content_id가 자리표시자(TA-126508)라 공사 콘텐츠 ID와 맞물리지 않는다.
     * 진짜 ID는 detail_content_id에 있으므로 실시간 결과를 붙일 때 이쪽도 함께 본다.
     */
    java.util.List<Venue> findByDetailContentIdIn(java.util.Collection<String> detailContentIds);

    /**
     * 공사 API 장애 시의 지도 대체 조회. 보고 있는 사각 영역 안의 장소만 돌려준다.
     * 전국 상위 목록으로 대체하면 화면 밖 장소만 잡혀 지도가 텅 비어 보인다.
     */
    @Query("""
            select v from Venue v
            where v.status = 'ACTIVE' and v.visibility = 'PUBLIC'
              and v.lat between :minLat and :maxLat
              and v.lng between :minLng and :maxLng
              and (:category is null or v.category = :category)
            order by case when v.imageUrl is null or v.imageUrl = '' then 1 else 0 end,
                     v.avgRating desc, v.reviewCount desc, v.id asc
            """)
    java.util.List<Venue> findInBounds(@Param("minLat") java.math.BigDecimal minLat,
                                       @Param("maxLat") java.math.BigDecimal maxLat,
                                       @Param("minLng") java.math.BigDecimal minLng,
                                       @Param("maxLng") java.math.BigDecimal maxLng,
                                       @Param("category") String category,
                                       Pageable pageable);

    /**
     * 정렬이 없으면 DB가 돌려주는 순서가 임의라 페이지 간 중복·누락이 생기고,
     * 홈의 "맞춤 추천"에 사진 없는 장소가 먼저 뜨기도 한다.
     * 사진 있는 것 → 평점 높은 것 → 리뷰 많은 것 순으로 고정하고, 마지막에 id로 동점을 깬다.
     */
    @Query("""
            select v from Venue v
            where v.status = 'ACTIVE' and v.visibility = 'PUBLIC'
              and (:category is null or v.category = :category)
              and (:hanbokDiscount is null or v.hanbokDiscount = :hanbokDiscount)
              and (:keyword is null or v.name like %:keyword%)
            order by case when v.imageUrl is null or v.imageUrl = '' then 1 else 0 end,
                     v.avgRating desc, v.reviewCount desc, v.id asc
            """)
    Page<Venue> search(@Param("category") String category,
                       @Param("hanbokDiscount") Boolean hanbokDiscount,
                       @Param("keyword") String keyword,
                       Pageable pageable);
}
