package com.byeori.domain.course.dto;

import com.byeori.domain.course.CuratedCourse;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import java.util.List;

public record CuratedCourseDetailResponse(
        Long id, String title, String description, String theme,
        String coverImageUrl, BigDecimal durationHours, List<CourseItemResponse> items) {

    public static CuratedCourseDetailResponse from(CuratedCourse c, Lang lang, List<CourseItemResponse> items) {
        return new CuratedCourseDetailResponse(
                c.getId(), c.titleIn(lang), c.descIn(lang), c.getTheme(),
                c.getCoverImageUrl(), c.getDurationHours(), items);
    }
}
