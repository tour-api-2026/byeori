package com.byeori.domain.venue;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private LocalDateTime syncedAt;
    private Long createdByUserId;
    private String visibility;
    private LocalDateTime createdAt;

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
        this.syncedAt = LocalDateTime.now();
    }
}
