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
}
