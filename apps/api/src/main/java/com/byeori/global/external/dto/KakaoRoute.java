package com.byeori.global.external.dto;

import java.util.List;

/**
 * 카카오모빌리티 길찾기 결과(파싱본).
 * - distance: 총 거리(m), duration: 총 소요시간(초)
 * - path: 지도에 그릴 polyline 좌표열. 각 원소 [경도(lng), 위도(lat)] (WGS84)
 * - legs: 구간(경유지 간)별 [거리(m), 시간(초), 구간이 끝나는 path 인덱스(exclusive)]
 */
public record KakaoRoute(
        int distance,
        int duration,
        List<double[]> path,
        List<int[]> legs
) {}
