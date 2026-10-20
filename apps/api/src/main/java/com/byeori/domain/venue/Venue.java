package com.byeori.domain.venue;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.byeori.global.i18n.Lang;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "venues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private String operatingHours;
    private String phone;
    private String homepageUrl;

    @Column(columnDefinition = "text")
    private String description;

    private String imageUrl;
    private String category;
    private boolean hanbokDiscount;
    private String hanbokDiscountDesc;
    private String status;
    private BigDecimal avgRating;
    private Integer reviewCount;
    private String source;
    private String kopisId;
    private String tourContentId;

    /**
     * 상세 화면 실시간 조회에 쓸 공사 콘텐츠 ID.
     * tourContentId는 동기화 upsert 키라 유니크 제약이 걸려 있어, 시드 장소가 같은 장소의
     * 실제 ID를 가리키려면 별도 컬럼이 필요하다. 표시 전용이므로 중복을 허용한다.
     */
    @Column(name = "detail_content_id")
    private String detailContentId;
    private LocalDateTime syncedAt;
    private Long createdByUserId;
    /** 카카오 로컬에서 골라 루트에 넣은 개인 장소의 카카오 장소 ID. */
    private String kakaoPlaceId;
    private String visibility;
    private LocalDateTime createdAt;

    /**
     * 이름·설명의 번역본. 사용자 요청 경로는 읽기만 하고, 쓰기는 번역 배치뿐이다.
     *
     * 컬럼 이름을 명시한다 — 스프링 네이밍은 대문자 앞에만 밑줄을 넣는 규칙이 어긋나면
     * (distanceM 이 distancem 이 된 적이 있다) 컬럼을 못 찾는다. 이름을 못 박아 두면 안전하다.
     */
    @Column(name = "name_en") private String nameEn;
    @Column(name = "name_ja") private String nameJa;
    @Column(name = "name_zh") private String nameZh;
    @Column(name = "desc_en", columnDefinition = "text") private String descEn;
    @Column(name = "desc_ja", columnDefinition = "text") private String descJa;
    @Column(name = "desc_zh", columnDefinition = "text") private String descZh;
    /** 원본 이름의 sha256. 이름이 바뀐 행만 다시 번역하려고 둔다. */
    @Column(name = "name_hash") private String nameHash;
    /** AI | TOURAPI. 나중에 공식 번역으로 덮어쓸 때 고르는 기준. */
    @Column(name = "tr_source") private String trSource;

    public void applyTranslation(Lang lang, String text) {
        switch (lang) {
            case EN -> this.nameEn = text;
            case JA -> this.nameJa = text;
            case ZH -> this.nameZh = text;
            case KO -> { }   // 원본이 한국어다 — 덮어쓸 것이 없다
        }
        this.trSource = "AI";
    }

    public void markHash(String hash) { this.nameHash = hash; }

    /** 선택한 언어의 이름. 번역이 없으면 한국어 원본. */
    public String nameIn(Lang lang) {
        String t = switch (lang) { case EN -> nameEn; case JA -> nameJa; case ZH -> nameZh; case KO -> null; };
        return (t == null || t.isBlank()) ? name : t;
    }

    /** USER 등록 장소(거지맵식 공유) 생성 */
    public static Venue userVenue(Long userId, String name, String address, BigDecimal lat, BigDecimal lng,
                                  String category, String phone, String homepageUrl, String operatingHours,
                                  String imageUrl, String description) {
        Venue v = new Venue();
        v.name = name;
        v.address = address;
        v.lat = lat;
        v.lng = lng;
        v.category = category;
        v.phone = phone;
        v.homepageUrl = homepageUrl;
        v.operatingHours = operatingHours;
        v.imageUrl = imageUrl;
        v.description = description;
        v.source = "USER";
        v.visibility = "PUBLIC";
        v.status = "ACTIVE";
        v.hanbokDiscount = false;
        v.avgRating = BigDecimal.ZERO;
        v.reviewCount = 0;
        v.createdByUserId = userId;
        v.createdAt = LocalDateTime.now();
        return v;
    }

    /**
     * 카카오 로컬 검색에서 골라 루트에 넣은 장소. 고른 사람만 쓰는 장소라 지도·검색·목록에는
     * 나오지 않는다(목록 조회는 모두 visibility = 'PUBLIC' 만 본다).
     */
    public static Venue privatePlace(Long userId, String kakaoPlaceId, String name, String address,
                                     BigDecimal lat, BigDecimal lng, String category, String phone) {
        Venue v = new Venue();
        v.kakaoPlaceId = kakaoPlaceId;
        v.name = name;
        v.address = address;
        v.lat = lat;
        v.lng = lng;
        v.category = category;
        v.phone = phone;
        v.source = "KAKAO";
        v.visibility = "PRIVATE";
        v.status = "ACTIVE";
        v.hanbokDiscount = false;
        v.avgRating = BigDecimal.ZERO;
        v.reviewCount = 0;
        v.createdByUserId = userId;
        v.createdAt = LocalDateTime.now();
        return v;
    }

    public void updateUserVenue(String name, String address, String category, String phone,
                                String homepageUrl, String operatingHours, String imageUrl, String description) {
        if (name != null) this.name = name;
        if (address != null) this.address = address;
        if (category != null) this.category = category;
        if (phone != null) this.phone = phone;
        if (homepageUrl != null) this.homepageUrl = homepageUrl;
        if (operatingHours != null) this.operatingHours = operatingHours;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (description != null) this.description = description;
    }

    public boolean isOwnedBy(Long userId) {
        return "USER".equals(this.source) && userId != null && userId.equals(this.createdByUserId);
    }

    /** TourAPI 항목으로 신규 생성 */
    public static Venue fromTourApi(String tourContentId, String name, String address, BigDecimal lat, BigDecimal lng,
                                    String category, String imageUrl, String phone) {
        Venue v = new Venue();
        v.tourContentId = tourContentId;
        v.name = name;
        v.address = address;
        v.lat = lat;
        v.lng = lng;
        v.category = category;
        v.imageUrl = imageUrl;
        v.phone = phone;
        v.source = "TOURAPI";
        v.visibility = "PUBLIC";
        v.status = "ACTIVE";
        v.hanbokDiscount = false;
        v.avgRating = BigDecimal.ZERO;
        v.reviewCount = 0;
        v.createdAt = LocalDateTime.now();
        v.syncedAt = LocalDateTime.now();
        return v;
    }

    /** TourAPI 재동기화 갱신(한복혜택 등 큐레이션 필드는 보존) */
    public void updateFromTourApi(String name, String address, BigDecimal lat, BigDecimal lng,
                                  String category, String imageUrl, String phone) {
        if (name != null) this.name = name;
        if (address != null) this.address = address;
        if (lat != null) this.lat = lat;
        if (lng != null) this.lng = lng;
        if (category != null) this.category = category;
        if (imageUrl != null) this.imageUrl = imageUrl;
        if (phone != null) this.phone = phone;
        this.status = "ACTIVE";   // 내려갔다가 다시 노출된 콘텐츠를 되살린다
        this.syncedAt = LocalDateTime.now();
    }

    /**
     * 공사에서 내린 콘텐츠(showflag=0)를 우리 쪽에서도 내린다.
     * 조회는 모두 status='ACTIVE'로 거르므로 목록·지도·검색에서 함께 빠진다.
     * 리뷰·위시리스트가 걸려 있을 수 있어 삭제하지 않고 상태만 바꾼다.
     */
    public void deactivateBySync() {
        this.status = "INACTIVE";
        this.syncedAt = LocalDateTime.now();
    }
}
