package com.byeori.domain.wishlist;

import com.byeori.domain.wishlist.dto.WishlistRequest;
import com.byeori.domain.wishlist.dto.WishlistResponse;
import com.byeori.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class WishlistController {

    private final WishlistService service;

    public WishlistController(WishlistService service) {
        this.service = service;
    }

    @GetMapping("/users/me/wishlists")
    public ApiResponse<List<WishlistResponse>> list(
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "2") Long userId) {
        return ApiResponse.ok(service.list(userId));
    }

    @PostMapping("/wishlists")
    public ApiResponse<WishlistResponse> add(
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "2") Long userId,
            @RequestBody WishlistRequest req) {
        return ApiResponse.ok(service.add(userId, req));
    }

    @DeleteMapping("/wishlists")
    public ApiResponse<Void> remove(
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "2") Long userId,
            @RequestParam(name = "targetType") String targetType,
            @RequestParam(name = "targetId") Long targetId) {
        service.remove(userId, targetType, targetId);
        return ApiResponse.ok(null);
    }
}
