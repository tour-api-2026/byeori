package com.byeori.domain.venue;

import com.byeori.domain.performance.PerformanceService;
import com.byeori.domain.performance.dto.PerformanceResponse;
import com.byeori.domain.venue.dto.VenueCreateRequest;
import com.byeori.domain.venue.dto.VenueDetailResponse;
import com.byeori.domain.venue.dto.VenueReportRequest;
import com.byeori.domain.venue.dto.VenueResponse;
import com.byeori.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.byeori.global.response.PageResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/venues")
public class VenueController {

    private final VenueService service;
    private final PerformanceService performanceService;

    public VenueController(VenueService service, PerformanceService performanceService) {
        this.service = service;
        this.performanceService = performanceService;
    }

    @GetMapping
    public ApiResponse<PageResponse<VenueResponse>> list(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "hanbokDiscount", required = false) Boolean hanbokDiscount,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<VenueResponse> result = service.list(category, hanbokDiscount, keyword,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "reviewCount")));
        return ApiResponse.ok(PageResponse.of(result));
    }

    /**
     * 지도 주변 조회. 지도가 보고 있는 좌표·반경만 공사 OpenAPI로 실시간 조회한다.
     * 전국 데이터를 미리 받아 거르는 방식 대신, 화면이 필요한 만큼만 그때그때 부른다.
     */
    @GetMapping("/nearby")
    public ApiResponse<List<VenueResponse>> nearby(
            @RequestParam(name = "lat") double lat,
            @RequestParam(name = "lng") double lng,
            @RequestParam(name = "radius", defaultValue = "3000") int radius,
            @RequestParam(name = "category", required = false) String category) {
        return ApiResponse.ok(service.nearby(lat, lng, radius, category));
    }

    /**
     * 키워드 검색. 저장된 목록 대신 공사 OpenAPI를 실시간으로 조회한다.
     * 실패하거나 결과가 없으면 빈 목록이 오고, 앱이 저장된 검색으로 대체한다.
     */
    @GetMapping("/search")
    public ApiResponse<List<VenueResponse>> search(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "hanbokDiscount", required = false) Boolean hanbokDiscount,
            @RequestParam(name = "size", defaultValue = "60") int size) {
        return ApiResponse.ok(service.searchLive(keyword, category, hanbokDiscount, Math.min(size, 100)));
    }

    @GetMapping("/mine")
    public ApiResponse<List<VenueResponse>> mine(
            @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.listMine(userId));
    }

    @GetMapping("/{id}")
    public ApiResponse<VenueDetailResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/{id}/performances")
    public ApiResponse<List<PerformanceResponse>> performances(@PathVariable("id") Long id) {
        return ApiResponse.ok(performanceService.byVenue(id));
    }

    @PostMapping
    public ApiResponse<VenueDetailResponse> create(
            @AuthenticationPrincipal Long userId,
            @RequestBody VenueCreateRequest req) {
        return ApiResponse.ok(service.createUserVenue(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<VenueDetailResponse> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id, @RequestBody VenueCreateRequest req) {
        return ApiResponse.ok(service.updateUserVenue(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id) {
        service.deleteUserVenue(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reports")
    public ApiResponse<Void> report(
            @AuthenticationPrincipal Long userId,
            @PathVariable("id") Long id, @RequestBody VenueReportRequest req) {
        service.report(userId, id, req);
        return ApiResponse.ok(null);
    }
}
