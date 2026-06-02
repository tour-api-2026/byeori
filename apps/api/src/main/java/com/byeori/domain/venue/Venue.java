package com.byeori.domain.venue;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "venues")
@Getter
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
}
