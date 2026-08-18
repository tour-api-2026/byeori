package com.byeori.domain.review.dto;

/** 리뷰 신고 요청. reason은 필수(varchar(50)), detail은 선택. */
public record ReviewReportRequest(String reason, String detail) {
}
