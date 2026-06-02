package com.byeori.domain.performance;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "performances")
@Getter
public class Performance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long venueId;
    private String title;
    private String genre;
    private String posterImageUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private String state;
    private String externalBookingUrl;
    private BigDecimal avgRating;
    private Integer reviewCount;
    private LocalDateTime createdAt;
    private String source;
    private String kopisId;
    private String tourContentId;
    private LocalDateTime syncedAt;
}
