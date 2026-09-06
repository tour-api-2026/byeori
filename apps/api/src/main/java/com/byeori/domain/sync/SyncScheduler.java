package com.byeori.domain.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 매일 새벽 동기화. 키 없으면 SyncService 내부에서 skip. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SyncScheduler {

    private final SyncService syncService;

    // 한국관광공사가 04:30에 원본을 갱신한다(공사 안내 기준).
    // 그 전에 받으면 하루 지난 데이터를 가져오므로 05:00에 돈다.
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void daily() {
        log.info("정기 동기화 시작");
        syncService.syncVenues();
        syncService.syncPerformances();
        syncService.syncFestivals();
        syncService.syncSeoulEvents();
    }
}
