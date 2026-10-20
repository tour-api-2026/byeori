package com.byeori.domain.performance;

import com.byeori.domain.activity.ActivityLogger;
import com.byeori.domain.performance.dto.PerformanceResponse;
import java.util.List;
import com.byeori.global.response.ApiResponse;
import com.byeori.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/performances")
public class PerformanceController {

    private final PerformanceService service;
    private final ActivityLogger activity;

    public PerformanceController(PerformanceService service, ActivityLogger activity) {
        this.service = service;
        this.activity = activity;
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
                PageRequest.of(page, size, sortFor(state)));
        return ApiResponse.ok(PageResponse.of(result));
    }

    /**
     * 상태마다 사용자가 기대하는 순서가 다르다.
     *
     *   예정   — 시작일 오름차순. 오늘과 가까운 것부터 본다. 먼 미래부터 보여 주면
     *            당장 갈 수 있는 행사가 목록 맨 끝에 묻힌다(실제로 그랬다).
     *   진행중 — 종료일 오름차순. 곧 끝나는 것부터 — 놓치기 전에 가라는 뜻이다.
     *   종료   — 종료일 내림차순. 최근에 끝난 것부터.
     *
     * id 를 마지막에 더해 순서를 확정한다. 같은 날짜가 많아(축제는 한 날에 수십 건)
     * 동점이 생기는데, 동점의 순서가 요청마다 달라지면 페이지를 넘길 때 어떤 행사는
     * 두 번 보이고 어떤 행사는 영영 안 보인다.
     */
    private static Sort sortFor(String state) {
        if ("ENDED".equals(state)) {
            return Sort.by(Sort.Order.desc("endDate"), Sort.Order.asc("id"));
        }
        if ("ONGOING".equals(state)) {
            return Sort.by(Sort.Order.asc("endDate"), Sort.Order.asc("id"));
        }
        // UPCOMING, 그리고 상태를 안 준 전체 목록
        return Sort.by(Sort.Order.asc("startDate"), Sort.Order.asc("id"));
    }

    /** 좌표 주변 행사. 장소 상세의 '주변에서 열리는 행사' 가 쓴다. */
    @GetMapping("/nearby")
    public ApiResponse<List<PerformanceResponse>> nearby(
            @RequestParam(name = "lat") java.math.BigDecimal lat,
            @RequestParam(name = "lng") java.math.BigDecimal lng,
            @RequestParam(name = "radius", defaultValue = "2000") int radius,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ApiResponse.ok(service.nearby(lat, lng, Math.min(radius, 20000), Math.min(size, 50)));
    }

    @GetMapping("/{id}")
    public ApiResponse<PerformanceResponse> detail(@AuthenticationPrincipal Long userId,
                                                   @PathVariable("id") Long id,
                                                   @RequestParam(name = "from", required = false) String from) {
        activity.view(userId, "PERFORMANCE", id, from);
        return ApiResponse.ok(service.detail(id));
    }
}
