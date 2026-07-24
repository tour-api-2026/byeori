package com.byeori.domain.review;

import com.byeori.domain.review.dto.ReviewRequest;
import com.byeori.domain.review.dto.ReviewResponse;
import com.byeori.domain.review.dto.ReviewUpdateRequest;
import com.byeori.global.response.ApiResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewResponse>> list(
            @RequestParam(name = "targetType") String targetType,
            @RequestParam(name = "targetId") Long targetId) {
        return ApiResponse.ok(service.listByTarget(targetType, targetId));
    }

    @GetMapping("/users/me/reviews")
    public ApiResponse<List<ReviewResponse>> listMine(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.listMine(userId));
    }

    @PostMapping("/reviews")
    public ApiResponse<ReviewResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody ReviewRequest req) {
        return ApiResponse.ok(service.create(userId, req));
    }

    @PatchMapping("/reviews/{id}")
    public ApiResponse<ReviewResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id,
            @RequestBody ReviewUpdateRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @DeleteMapping("/reviews/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id) {
        service.delete(userId, id);
        return ApiResponse.ok(null);
    }
}
