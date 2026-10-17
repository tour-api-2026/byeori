package com.byeori.global.external.dto;

import java.util.List;

/**
 * 카카오모빌리티 길찾기 결과(파싱본).
 * - distance: 총 거리(m), duration: 총 소요시간(초)
 * - path: 지도에 그릴 polyline 좌표열. 각 원소 [경도(lng), 위도(lat)] (WGS84)
 *
 * 구간별 요약은 담지 않는다. 구간마다 따로 부르므로 응답에 section 이 하나뿐이고,
 * 그 거리·시간은 distance·duration 과 같은 값이다.
 */
public record KakaoRoute(
        int distance,
        int duration,
        List<double[]> path
) {}
