package com.byeori.domain.venue;

import com.byeori.domain.performance.PerformanceService;
import com.byeori.domain.performance.dto.PerformanceResponse;
import com.byeori.domain.venue.dto.VenueCreateRequest;
import com.byeori.domain.venue.dto.VenueDetailResponse;
import com.byeori.domain.venue.dto.VenueReportRequest;
import com.byeori.domain.venue.dto.VenueResponse;
import com.byeori.global.response.ApiResponse;
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
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "2") Long userId,
            @RequestBody VenueCreateRequest req) {
        return ApiResponse.ok(service.createUserVenue(userId, req));
    }

    @PatchMapping("/{id}")
    public ApiResponse<VenueDetailResponse> update(
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "2") Long userId,
            @PathVariable("id") Long id, @RequestBody VenueCreateRequest req) {
        return ApiResponse.ok(service.updateUserVenue(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "2") Long userId,
            @PathVariable("id") Long id) {
        service.deleteUserVenue(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reports")
    public ApiResponse<Void> report(
            @RequestHeader(name = "X-Demo-User-Id", defaultValue = "2") Long userId,
            @PathVariable("id") Long id, @RequestBody VenueReportRequest req) {
        service.report(userId, id, req);
        return ApiResponse.ok(null);
    }
}
