package com.byeori.domain.itinerary.dto;

import com.byeori.domain.itinerary.Itinerary;
import java.time.LocalDate;
import java.util.List;

/** 여행일지 관련 요청/응답 DTO 모음 */
public final class ItineraryDtos {
    private ItineraryDtos() {}

    public record CreateRequest(
            String title, LocalDate startDate, LocalDate endDate,
            String sourceType, Long sourceCourseId) {}

    public record UpdateRequest(String title, LocalDate startDate, LocalDate endDate) {}

    public record ItemRequest(
            String targetType, Long targetId, LocalDate visitDate,
            Integer sortOrder, String plannedTime, String memo) {}

    public record ItemResponse(
            Long id, String targetType, Long targetId, String name, String imageUrl,
            LocalDate visitDate, int sortOrder, String plannedTime, String memo) {}

    public record Summary(
            Long id, String title, LocalDate startDate, LocalDate endDate, String sourceType, int itemCount) {
        public static Summary from(Itinerary i, int itemCount) {
            return new Summary(i.getId(), i.getTitle(), i.getStartDate(), i.getEndDate(), i.getSourceType(), itemCount);
        }
    }

    public record Detail(
            Long id, String title, LocalDate startDate, LocalDate endDate, String sourceType,
            List<ItemResponse> items) {
        public static Detail from(Itinerary i, List<ItemResponse> items) {
            return new Detail(i.getId(), i.getTitle(), i.getStartDate(), i.getEndDate(), i.getSourceType(), items);
        }
    }

    // ── 길찾기(여러 경유지 경로) ─────────────────────────────

    /** 경유지(방문지) 한 곳. order는 방문 순서(0부터). */
    public record RouteStop(
            int order, String targetType, Long targetId, String name, double lat, double lng) {}

    /** 구간(경유지 간) 거리·시간. */
    public record RouteLeg(int distance, int duration) {}

    /**
     * 경로 응답.
     * - distance: 총 거리(m), duration: 총 소요시간(초)
     * - stops: 좌표가 있어 경로에 포함된 방문지(순서대로)
     * - legs: 구간별 거리/시간
     * - path: 지도에 그릴 polyline 좌표열, 각 원소 [위도(lat), 경도(lng)]
     */
    public record RouteResponse(
            int distance, int duration, String priority,
            List<RouteStop> stops, List<RouteLeg> legs, List<double[]> path) {}
}
