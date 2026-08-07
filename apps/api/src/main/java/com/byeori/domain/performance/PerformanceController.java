package com.byeori.domain.performance;

import com.byeori.domain.performance.dto.PerformanceResponse;
import com.byeori.global.response.ApiResponse;
import com.byeori.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/performances")
public class PerformanceController {

    private final PerformanceService service;

    public PerformanceController(PerformanceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<PageResponse<PerformanceResponse>> list(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "genre", required = false) String genre,
            @RequestParam(name = "venueId", required = false) Long venueId,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "traditional", required = false) Boolean traditional,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Page<PerformanceResponse> result = service.list(state, genre, venueId, keyword, traditional,
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "startDate")));
        return ApiResponse.ok(PageResponse.of(result));
    }

    @GetMapping("/{id}")
    public ApiResponse<PerformanceResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }
}
