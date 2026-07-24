package com.byeori.domain.course;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "curated_course_items")
@Getter
public class CuratedCourseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long courseId;
    private Long performanceId;
    private Long venueId;
    private Integer sortOrder;
    private String recommendedTime;

    @Column(columnDefinition = "text")
    private String note;
}
