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
    /**
     * 번역의 출처. HUMAN = 사람이 번역해 scripts/import-translations.py 로 넣은 것(배치가 건드리지
     * 않고, 일부 언어만 AI 가 채워도 HUMAN 으로 남는다). AI = 배치가 만든 것. TOURAPI = 공사 다국어
     * 서비스용으로 계획만 해 둔 값이고 지금은 쓰지 않는다. 이름이 바뀌면 null 로 비워진다.
     */
    @Column(name = "tr_source") private String trSource;

    /**
     * 언어별 연속 실패 횟수. TranslationBatch.MAX_ATTEMPTS 에 닿으면 그 언어는 큐에서 빠진다.
     * 성공하면 0 으로 돌아간다. 컬럼은 V30.
     */
    @Column(name = "tr_fail_en") private short trFailEn;
    @Column(name = "tr_fail_ja") private short trFailJa;
    @Column(name = "tr_fail_zh") private short trFailZh;

    /** 이번 배치에서 이 언어 번역을 못 받았다. */
    public void recordTranslationFailure(Lang lang) {
        switch (lang) {
            case EN -> { if (trFailEn < Short.MAX_VALUE) trFailEn++; }
            case JA -> { if (trFailJa < Short.MAX_VALUE) trFailJa++; }
            case ZH -> { if (trFailZh < Short.MAX_VALUE) trFailZh++; }
            case KO -> { }
        }
    }

    private void resetFailure(Lang lang) {
        switch (lang) {
            case EN -> trFailEn = 0;
            case JA -> trFailJa = 0;
            case ZH -> trFailZh = 0;
            case KO -> { }
        }
    }

    public void applyTranslation(Lang lang, String text) {
        switch (lang) {
            case EN -> this.nameEn = text;
            case JA -> this.nameJa = text;
            case ZH -> this.nameZh = text;
            case KO -> { }   // 원본이 한국어다 — 덮어쓸 것이 없다
        }
        // 사람이 번역한 행은 일부 언어만 AI 가 채워도 HUMAN 으로 남긴다. 이 표시가 "기계가 사람의
        // 작업을 덮어쓰지 않게" 하는 유일한 근거라 내리면 안 된다.
        if (!"HUMAN".equals(this.trSource)) this.trSource = "AI";
        resetFailure(lang);
    }

    public boolean isHumanTranslated() { return "HUMAN".equals(trSource); }

    public void markHash(String hash) { this.nameHash = hash; }

    /** 이름이 바뀌었을 때 옛 이름의 번역을 모두 비운다. 비워야 세 언어가 모두 다시 대상이 된다. */
    public void clearTranslations() {
        this.nameEn = null;
        this.nameJa = null;
        this.nameZh = null;
        this.trFailEn = 0;
        this.trFailJa = 0;
        this.trFailZh = 0;
        this.trSource = null;   // 옛 이름의 번역이 사라졌으니 출처도 비운다. 새로 쓰면 AI 가 된다
    }

    /** 선택한 언어의 이름. 번역이 없으면 한국어 원본. */
    public String nameIn(Lang lang) {
        String t = switch (lang) { case EN -> nameEn; case JA -> nameJa; case ZH -> nameZh; case KO -> null; };
        return (t == null || t.isBlank()) ? name : t;
    }

    /** 선택한 언어의 설명. 없으면 null — 부르는 쪽이 한국어로 떨어뜨린다. */
    public String descIn(Lang lang) {
        return switch (lang) { case EN -> descEn; case JA -> descJa; case ZH -> descZh; case KO -> null; };
    }

    /** 처음 열릴 때 번역한 긴 설명을 저장한다. 이름 번역(trSource)과는 별개라 출처를 건드리지 않는다. */
    public void applyDescription(Lang lang, String text) {
        switch (lang) {
            case EN -> this.descEn = text;
            case JA -> this.descJa = text;
            case ZH -> this.descZh = text;
            case KO -> { }
        }
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
