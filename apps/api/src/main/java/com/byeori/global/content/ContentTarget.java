package com.byeori.global.content;

import com.byeori.global.exception.BadRequestException;

/**
 * 다형성 타깃 값객체. API는 {targetType, targetId}로 주고받고,
 * DB는 performance_id / venue_id 두 컬럼 중 하나로 저장한다. 변환을 여기서 일원화.
 */
public record ContentTarget(ContentType targetType, Long targetId) {

    public ContentTarget {
        if (targetType == null || targetId == null) {
            throw new BadRequestException("INVALID_TARGET", "targetType, targetId는 필수입니다.");
        }
    }

    public Long performanceId() {
        return targetType == ContentType.PERFORMANCE ? targetId : null;
    }

    public Long venueId() {
        return targetType == ContentType.VENUE ? targetId : null;
    }

    /** DB 두 컬럼(performanceId XOR venueId)에서 값객체 복원 */
    public static ContentTarget of(Long performanceId, Long venueId) {
        if ((performanceId == null) == (venueId == null)) {
            throw new BadRequestException("INVALID_TARGET", "performance/venue 중 정확히 하나만 지정해야 합니다.");
        }
        return performanceId != null
                ? new ContentTarget(ContentType.PERFORMANCE, performanceId)
                : new ContentTarget(ContentType.VENUE, venueId);
    }
}
