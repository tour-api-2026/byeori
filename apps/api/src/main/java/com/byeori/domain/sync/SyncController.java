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

    /** only=all(기본)|venues|performances|festivals|seoul 로 대상 선택. */
    @PostMapping("/trigger")
    public ApiResponse<Map<String, Integer>> trigger(@RequestParam(name = "only", defaultValue = "all") String only) {
        boolean all = only.equals("all");
        int venues = (all || only.equals("venues")) ? syncService.syncVenues() : -1;
        int performances = (all || only.equals("performances")) ? syncService.syncPerformances() : -1;
        int festivals = (all || only.equals("festivals")) ? syncService.syncFestivals() : -1;
        int seoul = (all || only.equals("seoul")) ? syncService.syncSeoulEvents() : -1;
        return ApiResponse.ok(Map.of("venues", venues, "performances", performances, "festivals", festivals, "seoul", seoul));
    }
}
