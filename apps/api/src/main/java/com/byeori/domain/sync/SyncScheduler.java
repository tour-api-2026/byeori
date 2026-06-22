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

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void daily() {
        log.info("정기 동기화 시작");
        syncService.syncVenues();
        syncService.syncPerformances();
        syncService.syncFestivals();
    }
}
