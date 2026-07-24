package com.byeori.domain.venue;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "venue_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VenueReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long venueId;
    private Long userId;
    private String reason;

    @Column(columnDefinition = "text")
    private String detail;

    private String status;
    private LocalDateTime createdAt;

    public VenueReport(Long venueId, Long userId, String reason, String detail) {
        this.venueId = venueId;
        this.userId = userId;
        this.reason = reason;
        this.detail = detail;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }
}
