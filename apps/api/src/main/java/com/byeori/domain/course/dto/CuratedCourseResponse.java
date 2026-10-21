package com.byeori.domain.course.dto;

import com.byeori.domain.course.CuratedCourse;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;

public record CuratedCourseResponse(
        Long id, String title, String description, String theme,
        String coverImageUrl, BigDecimal durationHours) {

    public static CuratedCourseResponse from(CuratedCourse c, Lang lang) {
        return new CuratedCourseResponse(
                c.getId(), c.titleIn(lang), c.descIn(lang), c.getTheme(),
                c.getCoverImageUrl(), c.getDurationHours());
    }
}
