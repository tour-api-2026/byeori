package com.byeori.domain.itinerary;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "itineraries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String sourceType;
    private Long sourceCourseId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Itinerary(Long userId, String title, LocalDate startDate, LocalDate endDate,
                     String sourceType, Long sourceCourseId) {
        this.userId = userId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sourceType = sourceType;
        this.sourceCourseId = sourceCourseId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(String title, LocalDate startDate, LocalDate endDate) {
        if (title != null) this.title = title;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        this.updatedAt = LocalDateTime.now();
    }
}
