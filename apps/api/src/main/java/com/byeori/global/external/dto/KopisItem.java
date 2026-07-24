package com.byeori.global.external.dto;

/** KOPIS 공연목록 항목(필요 필드만). */
public record KopisItem(
        String mt20id,        // 공연 ID
        String prfnm,         // 공연명
        String poster,        // 포스터 URL
        String prfpdfrom,     // 시작일 yyyy.MM.dd
        String prfpdto,       // 종료일 yyyy.MM.dd
        String genrenm,       // 장르
        String prfstate       // 공연상태(공연예정/공연중/공연완료)
) {}
