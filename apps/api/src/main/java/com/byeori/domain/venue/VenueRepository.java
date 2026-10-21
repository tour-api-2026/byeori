package com.byeori.domain.venue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    @Modifying(clearAutomatically = true)
    /** 이름 매칭용 id·이름만. 엔티티로 3만 행을 올리지 않으려고 따로 둔다. */
    @Query("select v.id, v.name from Venue v where v.name is not null")
    List<Object[]> findIdAndName();

    @Query("update Venue v set v.avgRating = :avg, v.reviewCount = :cnt where v.id = :id")
    void updateRating(@Param("id") Long id, @Param("avg") BigDecimal avg, @Param("cnt") int cnt);

    /** 내가 등록한 장소. 루트에 넣으려고 카카오에서 고른 개인 장소(source=KAKAO)는 빼야 해서 source 로 거른다. */
    List<Venue> findByCreatedByUserIdAndSourceOrderByCreatedAtDesc(Long userId, String source);

    /** 같은 사용자가 카카오에서 같은 곳을 다시 고르면 재사용한다. */
    Optional<Venue> findFirstByCreatedByUserIdAndKakaoPlaceId(Long userId, String kakaoPlaceId);

    Optional<Venue> findByTourContentId(String tourContentId);

    /** 증분 동기화 커서의 기본값. 실행 기록이 아직 없을 때 여기서부터 받는다. */
    @Query("select max(v.syncedAt) from Venue v where v.source = 'TOURAPI'")
    Optional<LocalDateTime> findMaxSyncedAt();

    /** 실시간 조회 결과를 자체 정보(한복 혜택·평점)와 붙이기 위한 일괄 조회. */
    List<Venue> findByTourContentIdIn(Collection<String> tourContentIds);

    /**
     * 시드로 들어온 장소는 tour_content_id가 자리표시자(TA-126508)라 공사 콘텐츠 ID와 맞물리지 않는다.
     * 진짜 ID는 detail_content_id에 있으므로 실시간 결과를 붙일 때 이쪽도 함께 본다.
     */
    List<Venue> findByDetailContentIdIn(Collection<String> detailContentIds);

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
    List<Venue> findInBounds(@Param("minLat") BigDecimal minLat,
                             @Param("maxLat") BigDecimal maxLat,
                             @Param("minLng") BigDecimal minLng,
                             @Param("maxLng") BigDecimal maxLng,
                             @Param("category") String category,
                             Pageable pageable);

    /**
     * 넓은 화면(전국 뷰)용 표본. 위와 달리 평점·id 순으로 뽑으면 수집 순서상 한 지역이
     * 300건을 독식해 지도 한 곳만 뭉친다(실제로 전부 대구가 나왔다).
     * id 해시로 섞어 전국에 고르게 흩뿌린다. 해시라 요청마다 결과가 흔들리지 않는다.
     */
    @Query(value = """
            select * from venues
            where status = 'ACTIVE' and visibility = 'PUBLIC'
              and lat between :minLat and :maxLat
              and lng between :minLng and :maxLng
              and (cast(:category as varchar) is null or category = :category)
            order by mod(id * 2654435761, 1000003)
            limit :limit
            """, nativeQuery = true)
    List<Venue> sampleInBounds(@Param("minLat") BigDecimal minLat,
                               @Param("maxLat") BigDecimal maxLat,
                               @Param("minLng") BigDecimal minLng,
                               @Param("maxLng") BigDecimal maxLng,
                               @Param("category") String category,
                               @Param("limit") int limit);

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
              and (:keyword is null or v.name like %:keyword%
                   or lower(v.nameEn) like lower(concat('%', :keyword, '%'))
                   or lower(v.nameJa) like lower(concat('%', :keyword, '%'))
                   or lower(v.nameZh) like lower(concat('%', :keyword, '%')))
            order by case when v.imageUrl is null or v.imageUrl = '' then 1 else 0 end,
                     v.avgRating desc, v.reviewCount desc, v.id asc
            """)
    Page<Venue> search(@Param("category") String category,
                       @Param("hanbokDiscount") Boolean hanbokDiscount,
                       @Param("keyword") String keyword,
                       Pageable pageable);

    /**
     * 같은 검색을 '현재 위치에서 가까운 순'으로.
     *
     * 정렬을 클라이언트에 맡기면 안 된다 — '카페'는 766건인데 응답은 잘려 나가므로,
     * 받은 뒤 정렬해 봐야 진짜 가까운 곳은 이미 빠져 있다. 고르는 단계에서 거리를 써야 한다.
     *
     * 거리는 제곱거리로 비교한다. 정렬에는 순서만 맞으면 되고 실제 미터는 화면에서 계산한다.
     * 경도 1도는 위도 1도보다 짧으므로(위도 36도에서 약 0.81배) 그만큼 눌러서 비교한다.
     * 이 보정 없이 비교하면 동서로 떨어진 곳이 실제보다 가깝게 잡힌다.
     */
    @Query("""
            select v from Venue v
            where v.status = 'ACTIVE' and v.visibility = 'PUBLIC'
              and v.lat is not null and v.lng is not null
              and (:category is null or v.category = :category)
              and (:hanbokDiscount is null or v.hanbokDiscount = :hanbokDiscount)
              and (:keyword is null or v.name like %:keyword%
                   or lower(v.nameEn) like lower(concat('%', :keyword, '%'))
                   or lower(v.nameJa) like lower(concat('%', :keyword, '%'))
                   or lower(v.nameZh) like lower(concat('%', :keyword, '%')))
            order by (v.lat - :lat) * (v.lat - :lat)
                   + (v.lng - :lng) * (v.lng - :lng) * 0.656,
                     v.id asc
            """)
    Page<Venue> searchNear(@Param("category") String category,
                           @Param("hanbokDiscount") Boolean hanbokDiscount,
                           @Param("keyword") String keyword,
                           @Param("lat") BigDecimal lat,
                           @Param("lng") BigDecimal lng,
                           Pageable pageable);

    /**
     * AI 루트 후보. 사진 있는 장소만, 매번 순서를 섞어 뽑는다.
     * 같은 조건으로 "다시 만들기"를 눌렀을 때 AI에게 다른 후보가 가야 결과가 달라진다.
     */
    @Query(value = """
            select * from venues
            where status = 'ACTIVE' and visibility = 'PUBLIC'
              and image_url is not null and image_url <> ''
              and lat between :minLat and :maxLat
              and lng between :minLng and :maxLng
              and category = :category
            order by random()
            limit :limit
            """, nativeQuery = true)
    List<Venue> sampleForRoute(@Param("minLat") BigDecimal minLat,
                               @Param("maxLat") BigDecimal maxLat,
                               @Param("minLng") BigDecimal minLng,
                               @Param("maxLng") BigDecimal maxLng,
                               @Param("category") String category,
                               @Param("limit") int limit);

    /**
     * 맞춤 추천 후보. 분류마다 제 몫을 주고, 고른 분류에는 더 준다.
     *
     * 전에는 search(null, ...) 로 200건을 받았다. 그 정렬이 'avg_rating desc, id asc' 인데
     * 별점 있는 장소가 33,906곳 중 4곳이라 사실상 id 순이 되어, 후보 200건이 문화 198 ·
     * 체험 1 · 한복 1 로 나왔다. 음식·공예를 고른 사람에게는 맞는 후보가 0건이라 취향
     * 항(가중치 0.40)이 전부 0이 되고, 관심사항을 바꿔도 추천이 그대로였다.
     *
     * 그래서 분류별로 창(window)을 나눠 뽑는다. 분류 하나가 낮은 id 를 다 가져가는 일이
     * 없어, 공예 52건이나 전통시장 828건도 후보에 들어온다. 고르지 않은 분류에도 몫을
     * 남기는 이유는 발견이다 — 고른 것만 보여주면 새로운 것을 만날 길이 막힌다.
     *
     * 지역은 걸러내지 않고 **우선순위로만** 쓴다. 걸러내면 그 지역에 공예가 4건뿐일 때
     * 칸이 비는데, 정렬로 두면 모자란 칸이 전국에서 저절로 채워진다.
     *
     * :lat 이 null 이면 거리 항은 상수가 되어 id 순으로 떨어진다 — 홈은 위치 권한을 새로
     * 묻지 않으므로 좌표 없이 오는 경우가 보통이다.
     *
     * 마지막 정렬이 중요하다. **분류를 번갈아** 내보낸다(각 분류의 1번끼리, 다음은 2번끼리).
     * 리뷰·별점이 사실상 없어서 관심 분류끼리는 점수가 완전히 동점이 되고, 그러면 최종
     * 순서가 DB 가 행을 돌려주는 순서에 좌우된다. 실제로 그 상태에서 공예(id 34685)가
     * 맛집(id 2288)에 밀려 한 건도 안 올라왔다 — 사용자가 공예를 골랐는데도. 번갈아
     * 내보내면 분류마다 앞자리를 얻고, 같은 관심사면 매번 같은 결과가 나온다.
     *
     * 안쪽에서 id 와 순번만 고르고 바깥에서 행을 가져온다. 창 함수가 만든 rn 열이 엔티티
     * 매핑에 섞이지 않게 하려는 것이다. 운영 데이터(33,906건)에서 68ms, 쿼리 한 번이다.
     *
     * @param categories  고른 분류. 빈 목록은 바인딩이 깨지므로 부르는 쪽이 한 칸은 채운다.
     * @param regionRegex "부산|제주" 처럼 이어 붙인 관심 지역. 없으면 null.
     */
    @Query(value = """
            select v2.* from venues v2
            join (
              select t.id, t.rn, t.category from (
                select v.id, v.category, row_number() over (
                    partition by v.category
                    order by case when cast(:regionRegex as text) is not null
                                   and v.address ~ cast(:regionRegex as text) then 0 else 1 end,
                             case when v.image_url is null or v.image_url = '' then 1 else 0 end,
                             case when cast(:lat as numeric) is null then 0
                                  else (v.lat - cast(:lat as numeric)) * (v.lat - cast(:lat as numeric))
                                     + (v.lng - cast(:lng as numeric)) * (v.lng - cast(:lng as numeric)) * 0.656
                             end,
                             v.id) rn
                from venues v
                where v.status = 'ACTIVE' and v.visibility = 'PUBLIC') t
              where t.rn <= case when t.category in (:categories) then :liked else :other end) s
              on s.id = v2.id
            order by s.rn, s.category
            """, nativeQuery = true)
    List<Venue> findFeedCandidates(@Param("categories") java.util.Collection<String> categories,
                                   @Param("regionRegex") String regionRegex,
                                   @Param("lat") BigDecimal lat,
                                   @Param("lng") BigDecimal lng,
                                   @Param("liked") int liked,
                                   @Param("other") int other);

    /**
     * 번역이 필요한 행 중 실패가 :max 번 미만인 것. 해당 언어 번역이 비었거나 이름이 바뀌어
     * 해시가 어긋난 행이 "필요한" 행이다. 해시가 맞고 번역이 있는 행은 영원히 다시 번역하지
     * 않는다 — 이 조건이 뒤집히면 매일 전량을 세 언어로 다시 번역해 비용이 수백 배가 된다.
     * digest() 는 V28 이 만든 pgcrypto 확장이다.
     *
     * 아래는 그 위에 얹은 규칙이다. 실패가 적은 행이 먼저, 같으면 id 순이다.
     *
     * 모델이 끝내 못 하는 행(일·중 출력에 한글이 남거나 응답에서 빠지는 것)은 해시가 안 찍혀
     * 큐 맨 앞에 영원히 남는다. 실패 횟수로 뒤로 보내고, :max 번 실패하면 이 언어에서는 뺀다 —
     * 안 그러면 그런 행이 한도를 넘는 날부터 새 행에 순서가 오지 않는다. 이름이 바뀐 행
     * (해시가 있는데 어긋남)은 실패 횟수와 무관하게 다시 대상이다.
     */
    @Query(value = """
            SELECT * FROM venues
             WHERE name IS NOT NULL AND name <> ''
               AND (name_hash IS NULL
                    OR name_hash <> encode(digest(name, 'sha256'), 'hex')
                    OR (:lang = 'EN' AND name_en IS NULL)
                    OR (:lang = 'JA' AND name_ja IS NULL)
                    OR (:lang = 'ZH' AND name_zh IS NULL))
               AND (CASE WHEN :lang = 'EN' THEN tr_fail_en WHEN :lang = 'JA' THEN tr_fail_ja ELSE tr_fail_zh END < :max
                    OR (name_hash IS NOT NULL AND name_hash <> encode(digest(name, 'sha256'), 'hex')))
             ORDER BY CASE WHEN :lang = 'EN' THEN tr_fail_en WHEN :lang = 'JA' THEN tr_fail_ja ELSE tr_fail_zh END, id
             LIMIT :limit
            """, nativeQuery = true)
    List<Venue> findNeedingTranslation(@Param("lang") String lang, @Param("limit") int limit, @Param("max") int max);

    /** 위 조건에서 실패 횟수 때문에 빠진 행 수. 배치가 "건너뛰는 중" 이라고 로그에 남기려고 센다. */
    @Query(value = """
            SELECT count(*) FROM venues
             WHERE name IS NOT NULL AND name <> ''
               AND (name_hash IS NULL
                    OR (:lang = 'EN' AND name_en IS NULL)
                    OR (:lang = 'JA' AND name_ja IS NULL)
                    OR (:lang = 'ZH' AND name_zh IS NULL))
               AND (name_hash IS NULL OR name_hash = encode(digest(name, 'sha256'), 'hex'))
               AND CASE WHEN :lang = 'EN' THEN tr_fail_en WHEN :lang = 'JA' THEN tr_fail_ja ELSE tr_fail_zh END >= :max
            """, nativeQuery = true)
    long countGivenUp(@Param("lang") String lang, @Param("max") int max);
}
