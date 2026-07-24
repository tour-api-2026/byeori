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
