package com.byeori.global.external.dto;

import java.util.List;

/**
 * OSRM 도보 경로(파싱본).
 * - distance: 총 거리(m), duration: 총 소요시간(초)
 * - path: 지도에 그릴 좌표열. 각 원소 [위도(lat), 경도(lng)] — KakaoRoute 와 달리 이미 뒤집혀 있다.
 */
public record OsrmRoute(int distance, int duration, List<double[]> path) {}
