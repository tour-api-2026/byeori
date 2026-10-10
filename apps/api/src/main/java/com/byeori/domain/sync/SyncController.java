package com.byeori.domain.sync;

import com.byeori.global.response.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수동 백필 트리거(최초 적재용).
 * TODO: 실로그인 도입 시 ROLE_ADMIN 가드 추가(현재 무인증 임시 개방).
 */
@RestController
@RequestMapping("/api/v1/admin/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final PerformanceDeduper deduper;

    /**
     * only=all(기본)|incremental|venues|performances|festivals|seoul|dedupe 로 대상 선택.
     *
     * incremental = 변경분만(정기 동기화와 같은 경로, 약 3건 호출)
     * venues      = 전량 재수집(약 350건 호출) — 초기 적재·정합성 복구용
     * dedupe      = 외부 호출 없이 중복 쓸기만 다시 실행(판정 규칙을 고친 뒤 확인용)
     */
    @PostMapping("/trigger")
    public ApiResponse<Map<String, Integer>> trigger(
            @RequestParam(name = "only", defaultValue = "all") String only,
            @RequestParam(name = "since", required = false) String since) {
        if (only.equals("incremental")) {
            // since(yyyyMMdd)를 주면 그 날짜부터 다시 받는다. 장애로 며칠 걸렀을 때
            // 구간을 지정해 따라잡거나, 동작을 확인할 때 쓴다.
            return ApiResponse.ok(Map.of("incremental", syncService.syncVenuesIncremental(since)));
        }
        if (only.equals("dedupe")) {
            PerformanceDeduper.Summary s = deduper.sweep();
            return ApiResponse.ok(Map.of("hidden", s.hidden(), "added", s.added(), "released", s.released()));
        }
        boolean all = only.equals("all");
        int venues = (all || only.equals("venues")) ? syncService.syncVenues() : -1;
        int performances = (all || only.equals("performances")) ? syncService.syncPerformances() : -1;
        int festivals = (all || only.equals("festivals")) ? syncService.syncFestivals() : -1;
        int seoul = (all || only.equals("seoul")) ? syncService.syncSeoulEvents() : -1;
        // 받아온 뒤에 쓴다. 출처 하나만 다시 받아도 다른 출처와의 중복이 새로 생긴다.
        int hidden = deduper.sweep().hidden();
        return ApiResponse.ok(Map.of("venues", venues, "performances", performances,
                "festivals", festivals, "seoul", seoul, "duplicatesHidden", hidden));
    }
}
