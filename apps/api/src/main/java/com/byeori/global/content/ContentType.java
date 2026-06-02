package com.byeori.global.content;

import com.byeori.global.exception.BadRequestException;

/** 다형성 대상 종류 (찜·리뷰·태그·코스·일지 공용) */
public enum ContentType {
    PERFORMANCE,
    VENUE;

    public static ContentType from(String value) {
        try {
            return ContentType.valueOf(value);
        } catch (Exception e) {
            throw new BadRequestException("INVALID_TARGET_TYPE", "targetType은 PERFORMANCE 또는 VENUE 여야 합니다.");
        }
    }
}
