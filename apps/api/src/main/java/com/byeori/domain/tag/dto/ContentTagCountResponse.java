package com.byeori.domain.tag.dto;

/** 대상별 태그 집계: "분위기 좋음 N명" + 내 투표 여부 */
public record ContentTagCountResponse(Long commentTagId, String name, long count, boolean voted) {
}
