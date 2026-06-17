package com.byeori.domain.course.dto;

import com.byeori.domain.course.CuratedCourse;
import java.math.BigDecimal;

public record CuratedCourseResponse(
        Long id, String title, String description, String theme,
        String coverImageUrl, BigDecimal durationHours) {

    public static CuratedCourseResponse from(CuratedCourse c) {
        return new CuratedCourseResponse(
                c.getId(), c.getTitle(), c.getDescription(), c.getTheme(),
                c.getCoverImageUrl(), c.getDurationHours());
    }
}
