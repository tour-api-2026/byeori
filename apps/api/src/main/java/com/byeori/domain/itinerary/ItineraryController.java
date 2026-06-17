package com.byeori.domain.itinerary;

import com.byeori.domain.itinerary.dto.ItineraryDtos.*;
import com.byeori.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ItineraryController {

    private static final String UID = "X-Demo-User-Id";
    private final ItineraryService service;

    public ItineraryController(ItineraryService service) {
        this.service = service;
    }

    @GetMapping("/users/me/itineraries")
    public ApiResponse<List<Summary>> listMine(@RequestHeader(name = UID, defaultValue = "2") Long userId) {
        return ApiResponse.ok(service.listMine(userId));
    }

    @PostMapping("/itineraries")
    public ApiResponse<Detail> create(@RequestHeader(name = UID, defaultValue = "2") Long userId,
                                      @RequestBody CreateRequest req) {
        return ApiResponse.ok(service.create(userId, req));
    }

    @GetMapping("/itineraries/{id}")
    public ApiResponse<Detail> get(@RequestHeader(name = UID, defaultValue = "2") Long userId,
                                   @PathVariable("id") Long id) {
        return ApiResponse.ok(service.get(userId, id));
    }

    @PatchMapping("/itineraries/{id}")
    public ApiResponse<Detail> update(@RequestHeader(name = UID, defaultValue = "2") Long userId,
                                      @PathVariable("id") Long id, @RequestBody UpdateRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @DeleteMapping("/itineraries/{id}")
    public ApiResponse<Void> delete(@RequestHeader(name = UID, defaultValue = "2") Long userId,
                                    @PathVariable("id") Long id) {
        service.delete(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/itineraries/{id}/items")
    public ApiResponse<ItemResponse> addItem(@RequestHeader(name = UID, defaultValue = "2") Long userId,
                                             @PathVariable("id") Long id, @RequestBody ItemRequest req) {
        return ApiResponse.ok(service.addItem(userId, id, req));
    }

    @PatchMapping("/itineraries/{id}/items/{itemId}")
    public ApiResponse<ItemResponse> updateItem(@RequestHeader(name = UID, defaultValue = "2") Long userId,
                                                @PathVariable("id") Long id, @PathVariable("itemId") Long itemId,
                                                @RequestBody ItemRequest req) {
        return ApiResponse.ok(service.updateItem(userId, id, itemId, req));
    }

    @DeleteMapping("/itineraries/{id}/items/{itemId}")
    public ApiResponse<Void> deleteItem(@RequestHeader(name = UID, defaultValue = "2") Long userId,
                                        @PathVariable("id") Long id, @PathVariable("itemId") Long itemId) {
        service.deleteItem(userId, id, itemId);
        return ApiResponse.ok(null);
    }
}
