package com.byeori.domain.itinerary.dto;

import com.byeori.domain.itinerary.Itinerary;
import java.time.LocalDate;
import java.util.List;

/** 여행일지 관련 요청/응답 DTO 모음 */
public final class ItineraryDtos {
    private ItineraryDtos() {}

    /** items 는 AI 루트를 저장할 때 방문지를 한 번에 넣는 용도(sourceType=AI). */
    public record CreateRequest(
            String title, LocalDate startDate, LocalDate endDate,
            String sourceType, Long sourceCourseId, List<ItemRequest> items) {}

    public record UpdateRequest(String title, LocalDate startDate, LocalDate endDate) {}

    public record ItemRequest(
            String targetType, Long targetId, LocalDate visitDate,
            Integer sortOrder, String plannedTime, String memo) {}

    /** lat/lng 는 장소 추가 창이 "이 루트 주변"을 보여주는 기준점으로 쓴다. */
    /** 목록 카드에 한 줄로 찍히는 방문지. 카드는 번호와 이름만 쓴다. */
    public record SummaryStop(Long id, String name) {}

    public record ItemResponse(
            Long id, String targetType, Long targetId, String name, String imageUrl,
            LocalDate visitDate, int sortOrder, String plannedTime, String memo, Double lat, Double lng) {}

    /** 카카오에서 고른 장소를 루트에 넣는다. 서버가 개인 장소(PRIVATE)로 저장한 뒤 항목을 만든다. */
    public record PlaceItemRequest(
            String kakaoPlaceId, String name, String address, String category, String phone,
            Double lat, Double lng, LocalDate visitDate, Integer sortOrder) {}

    /**
     * 목록용 요약.
     *
     * stops 는 카드에 보여줄 앞 몇 곳이다. 전에는 목록이 개수만 주고 카드마다 상세를 따로
     * 받아 왔다 — 루트가 다섯이면 요청이 여섯 번이고, 제목은 먼저 뜨는데 방문지 줄만 뒤늦게
     * 나타났다. 서버는 어차피 개수를 세느라 항목을 전부 읽으므로, 앞쪽을 함께 실어 보내면
     * 추가 조회 없이 화면이 한 번에 완성된다. 전체 개수는 itemCount 가 그대로 알려 준다.
     */
    public record Summary(
            Long id, String title, LocalDate startDate, LocalDate endDate, String sourceType,
            int itemCount, List<SummaryStop> stops) {
        public static Summary from(Itinerary i, int itemCount, List<SummaryStop> stops) {
            return new Summary(i.getId(), i.getTitle(), i.getStartDate(), i.getEndDate(), i.getSourceType(),
                    itemCount, stops);
        }
    }

    public record Detail(
            Long id, String title, LocalDate startDate, LocalDate endDate, String sourceType,
            List<ItemResponse> items) {
        public static Detail from(Itinerary i, List<ItemResponse> items) {
            return new Detail(i.getId(), i.getTitle(), i.getStartDate(), i.getEndDate(), i.getSourceType(), items);
        }
    }

    /**
     * 하루치 순서를 통째로 다시 매긴다.
     *
     * 항목 하나의 sortOrder 만 고치는 길(ItemRequest)로는 순서를 바꿀 수 없다. 2번을 1번으로
     * 올려도 원래 1번이 그대로 1번이라 같은 값이 둘이 되고, 그때 누가 위에 오는지는 DB가 정한다.
     * 그래서 그날 전체를 받아 0부터 다시 적는다.
     */
    public record ReorderRequest(LocalDate visitDate, List<Long> itemIds) {}

    // ── 공유 링크 ─────────────────────────────

    /** 공유 토큰 발급 응답. 앱이 이 값으로 링크를 만든다. */
    public record ShareResponse(String token) {}

    /**
     * 토큰 하나당 한 곳. ItemResponse 와 닮았지만 **일부러 따로 둔다**.
     *
     * 같은 record 를 돌려쓰면 나중에 ItemResponse 에 개인 필드를 하나 더할 때 그게 조용히
     * 공개된다. 공유로 나가는 모양은 여기에만 적어 둔다.
     *
     * 빠진 것: memo(숙소 비번 같은 걸 적어 둔 사람이 있다), id, userId.
     * targetType·targetId 는 남긴다 — 받은 사람이 장소를 눌러 상세로 들어갈 수 있어야 한다.
     */
    public record SharedStop(
            String targetType, Long targetId, String name, String imageUrl,
            LocalDate visitDate, int sortOrder, String plannedTime, Double lat, Double lng) {}

    /** 링크를 연 사람이 보는 루트. 누구 것인지는 담지 않는다. */
    public record SharedDetail(
            String title, LocalDate startDate, LocalDate endDate, List<SharedStop> stops) {}

    // ── 길찾기(여러 경유지 경로) ─────────────────────────────

    /** 경유지(방문지) 한 곳. order는 방문 순서(0부터). */
    public record RouteStop(
            int order, String targetType, Long targetId, String name, double lat, double lng) {}

    /**
     * 구간별 거리·시간과 이동 수단. mode 는 WALK | DRIVE | STRAIGHT.
     *
     * points 는 이 구간이 전체 path 에서 차지하는 점의 수다. 첫 구간은 제 경로 전부, 이후 구간은
     * 앞 구간과 겹치는 첫 점을 뺀 만큼이다. 합이 path 길이와 같으므로 지도가 구간별로 자를 수 있다.
     * 이 필드가 생기기 전에 저장된 행은 0 으로 읽힌다 — 0 이하면 자를 수 없다는 뜻이다.
     */
    public record RouteLeg(int distance, int duration, String mode, int points) {}

    /**
     * 경로 응답.
     * - distance: 총 거리(m), duration: 총 소요시간(초)
     * - mode: 요청한 모드(AUTO|WALK|DRIVE). 구간별 실제 수단은 legs[].mode 를 본다.
     * - stops: 좌표가 있어 경로에 포함된 방문지(순서대로)
     * - legs: 구간별 거리/시간/수단
     * - path: 지도에 그릴 polyline 좌표열, 각 원소 [위도(lat), 경도(lng)]
     */
    public record RouteResponse(
            int distance, int duration, String mode,
            List<RouteStop> stops, List<RouteLeg> legs, List<double[]> path) {}
}
