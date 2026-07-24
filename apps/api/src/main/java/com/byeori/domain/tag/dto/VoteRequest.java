package com.byeori.domain.tag.dto;

public record VoteRequest(Long commentTagId, String targetType, Long targetId) {
}
