package com.byeori.domain.itinerary;

import com.byeori.domain.itinerary.dto.ItineraryDtos.*;
import com.byeori.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ItineraryController {

    private final ItineraryService service;

    public ItineraryController(ItineraryService service) {
        this.service = service;
    }

    @GetMapping("/users/me/itineraries")
    public ApiResponse<List<Summary>> listMine(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.listMine(userId));
    }

    @PostMapping("/itineraries")
    public ApiResponse<Detail> create(@AuthenticationPrincipal Long userId,
                                      @RequestBody CreateRequest req) {
        return ApiResponse.ok(service.create(userId, req));
    }

    @GetMapping("/itineraries/{id}")
    public ApiResponse<Detail> get(@AuthenticationPrincipal Long userId,
                                   @PathVariable("id") Long id) {
        return ApiResponse.ok(service.get(userId, id));
    }

    /** 공유 링크 발급(소유자만). 이미 있으면 같은 토큰을 돌려준다. */
    @PostMapping("/itineraries/{id}/share")
    public ApiResponse<ShareResponse> share(@AuthenticationPrincipal Long userId,
                                            @PathVariable("id") Long id) {
        return ApiResponse.ok(service.share(userId, id));
    }

    /**
     * 공유 링크로 보는 루트. 로그인하지 않은 사람도 연다.
     *
     * 경로를 /itineraries 밑에 두지 않은 이유: SecurityConfig 가 /api/v1/itineraries/** 를
     * 통째로 authenticated 로 막고 있어서, 그 아래에 두면 예외를 하나 뚫어야 한다.
     * 공개하려는 것만 /shared 아래에 모아 두면 보호 규칙을 건드릴 일이 없다.
     */
    @GetMapping("/shared/itineraries/{token}")
    public ApiResponse<SharedDetail> shared(@PathVariable("token") String token) {
        return ApiResponse.ok(service.getShared(token));
    }

    /** 방문지들을 순서대로 잇는 도로 경로(polyline·거리·시간). priority=RECOMMEND|TIME|DISTANCE */
    @GetMapping("/itineraries/{id}/route")
    public ApiResponse<RouteResponse> route(@AuthenticationPrincipal Long userId,
                                            @PathVariable("id") Long id,
                                            @RequestParam(name = "priority", defaultValue = "RECOMMEND") String priority) {
        return ApiResponse.ok(service.route(userId, id, priority));
    }

    @PatchMapping("/itineraries/{id}")
    public ApiResponse<Detail> update(@AuthenticationPrincipal Long userId,
                                      @PathVariable("id") Long id, @RequestBody UpdateRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @DeleteMapping("/itineraries/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId,
                                    @PathVariable("id") Long id) {
        service.delete(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/itineraries/{id}/items")
    public ApiResponse<ItemResponse> addItem(@AuthenticationPrincipal Long userId,
                                             @PathVariable("id") Long id, @RequestBody ItemRequest req) {
        return ApiResponse.ok(service.addItem(userId, id, req));
    }

    /** 카카오에서 고른 장소(벼리 DB 밖)를 루트에 넣는다. */
    @PostMapping("/itineraries/{id}/items/place")
    public ApiResponse<ItemResponse> addPlaceItem(@AuthenticationPrincipal Long userId,
                                                  @PathVariable("id") Long id,
                                                  @RequestBody PlaceItemRequest req) {
        return ApiResponse.ok(service.addPlaceItem(userId, id, req));
    }

    @PatchMapping("/itineraries/{id}/items/{itemId}")
    public ApiResponse<ItemResponse> updateItem(@AuthenticationPrincipal Long userId,
                                                @PathVariable("id") Long id, @PathVariable("itemId") Long itemId,
                                                @RequestBody ItemRequest req) {
        return ApiResponse.ok(service.updateItem(userId, id, itemId, req));
    }

    @DeleteMapping("/itineraries/{id}/items/{itemId}")
    public ApiResponse<Void> deleteItem(@AuthenticationPrincipal Long userId,
                                        @PathVariable("id") Long id, @PathVariable("itemId") Long itemId) {
        service.deleteItem(userId, id, itemId);
        return ApiResponse.ok(null);
    }
}
