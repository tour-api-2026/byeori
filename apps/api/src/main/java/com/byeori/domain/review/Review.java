package com.byeori.domain.review;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long performanceId;
    private Long venueId;
    private Integer rating;

    @Column(columnDefinition = "text")
    private String content;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Review(Long userId, Long performanceId, Long venueId, Integer rating, String content) {
        this.userId = userId;
        this.performanceId = performanceId;
        this.venueId = venueId;
        this.rating = rating;
        this.content = content;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void update(Integer rating, String content) {
        this.rating = rating;
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }
}
