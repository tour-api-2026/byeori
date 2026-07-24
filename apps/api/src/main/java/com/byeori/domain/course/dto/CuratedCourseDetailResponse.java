package com.byeori.domain.course.dto;

import com.byeori.domain.course.CuratedCourse;
import java.math.BigDecimal;
import java.util.List;

public record CuratedCourseDetailResponse(
        Long id, String title, String description, String theme,
        String coverImageUrl, BigDecimal durationHours, List<CourseItemResponse> items) {

    public static CuratedCourseDetailResponse from(CuratedCourse c, List<CourseItemResponse> items) {
        return new CuratedCourseDetailResponse(
                c.getId(), c.getTitle(), c.getDescription(), c.getTheme(),
                c.getCoverImageUrl(), c.getDurationHours(), items);
    }
}
