package com.byeori.domain.review;

import com.byeori.domain.review.dto.ReviewReportRequest;
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

    /** 공개 조회. 토큰이 있으면 차단한 사용자의 리뷰를 걸러낸다(비로그인은 userId=null). */
    @GetMapping("/reviews")
    public ApiResponse<List<ReviewResponse>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(name = "targetType") String targetType,
            @RequestParam(name = "targetId") Long targetId) {
        return ApiResponse.ok(service.listByTarget(targetType, targetId, userId));
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

    @PostMapping("/reviews/{id}/reports")
    public ApiResponse<Void> report(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id,
            @RequestBody ReviewReportRequest req) {
        service.report(userId, id, req);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/reviews/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id) {
        service.delete(userId, id);
        return ApiResponse.ok(null);
    }
}
