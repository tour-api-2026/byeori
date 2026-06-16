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

    /** KOPIS 공연으로 신규 생성 */
    public static Performance fromKopis(String kopisId, String title, String genre, String posterImageUrl,
                                        LocalDate startDate, LocalDate endDate, String state, String externalBookingUrl) {
        Performance p = new Performance();
        p.kopisId = kopisId;
        p.title = title;
        p.genre = genre;
        p.posterImageUrl = posterImageUrl;
        p.startDate = startDate;
        p.endDate = endDate;
        p.state = state;
        p.externalBookingUrl = externalBookingUrl;
        p.source = "KOPIS";
        p.avgRating = BigDecimal.ZERO;
        p.reviewCount = 0;
        p.createdAt = LocalDateTime.now();
        p.syncedAt = LocalDateTime.now();
        return p;
    }

    /** KOPIS 재동기화 갱신 */
    public void updateFromKopis(String title, String genre, String posterImageUrl,
                                LocalDate startDate, LocalDate endDate, String state) {
        if (title != null) this.title = title;
        if (genre != null) this.genre = genre;
        if (posterImageUrl != null) this.posterImageUrl = posterImageUrl;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        if (state != null) this.state = state;
        this.syncedAt = LocalDateTime.now();
    }
}
