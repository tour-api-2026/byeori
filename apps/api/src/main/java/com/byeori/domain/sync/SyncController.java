package com.byeori.domain.sync;

import com.byeori.domain.translation.TranslationBatch;
import com.byeori.global.response.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수동 백필 트리거(최초 적재용). SecurityConfig 가 /api/v1/admin/** 에 ADMIN 권한을 요구한다.
 */
@RestController
@RequestMapping("/api/v1/admin/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;
    private final PerformanceDeduper deduper;
    private final TranslationBatch translationBatch;
    private final MultilangSyncService multilangSync;

    /**
     * only=all(기본)|incremental|venues|performances|festivals|seoul|dedupe|translate|multilang 로 대상 선택.
     *
     * incremental = 변경분만(정기 동기화와 같은 경로, 약 3건 호출)
     * venues      = 전량 재수집(약 350건 호출) — 초기 적재·정합성 복구용
     * dedupe      = 외부 호출 없이 중복 쓸기만 다시 실행(판정 규칙을 고친 뒤 확인용)
     * translate   = 이름 번역만 실행(외부는 OpenAI 만). limit 은 언어·종류별 최대 행 수로,
     *               생략하면 정기 작업과 같은 5,000. 첫 전량 번역은 한 요청에
     *               몰지 말고 2000 안팎으로 나눠 반복한다(끊기면 어디까지 됐는지 알기
     *               어렵다). 품질 확인은 작은 값(예: 100)으로 먼저 한다.
     */
    @PostMapping("/trigger")
    public ApiResponse<Map<String, Integer>> trigger(
            @RequestParam(name = "only", defaultValue = "all") String only,
            @RequestParam(name = "since", required = false) String since,
            @RequestParam(name = "limit", required = false) Integer limit) {
        if (only.equals("translate")) {
            // 사용자 경로가 아니라 관리자 트리거다. AI 를 부르는 곳은 이 배치뿐이다.
            int cap = (limit == null || limit < 1) ? TranslationBatch.MAX_PER_RUN : limit;
            return ApiResponse.ok(Map.of("translated", translationBatch.run(cap)));
        }
        if (only.equals("incremental")) {
            // since(yyyyMMdd)를 주면 그 날짜부터 다시 받는다. 장애로 며칠 걸렀을 때
            // 구간을 지정해 따라잡거나, 동작을 확인할 때 쓴다.
            return ApiResponse.ok(Map.of("incremental", syncService.syncVenuesIncremental(since)));
        }
        if (only.equals("multilang")) {
            // 공사 다국어판에서 공식 번역 이름을 가져와 붙인다. AI 를 부르지 않는다.
            return ApiResponse.ok(Map.of("multilang", multilangSync.syncAll()));
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
