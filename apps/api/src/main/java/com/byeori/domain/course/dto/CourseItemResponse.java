package com.byeori.domain.course.dto;

/** 코스 항목: 다형성 타깃을 targetType/targetId + 표시정보로 노출 */
public record CourseItemResponse(
        Long id, String targetType, Long targetId, String name, String imageUrl,
        int sortOrder, String recommendedTime, String note) {
}
