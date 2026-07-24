package com.byeori.domain.course;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "curated_courses")
@Getter
public class CuratedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String theme;
    private String coverImageUrl;
    private BigDecimal durationHours;
    private String status;
    private LocalDateTime createdAt;
}
