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

    // 한국관광공사가 04:30에 원본을 갱신한다(공사 안내 기준). 그 이후에 받는다.
    //
    // 공사 데이터는 areaBasedSyncList2(공사가 로컬 저장용으로 제공하는 동기화
    // 오퍼레이션)로 마지막 성공 이후 변경분만 받는다. 전량을 다시 받으면 1회 약
    // 350건인데 증분은 보통 3건으로 끝나, 공사 권고인 1일 1회를 지키면서도 호출량이
    // 1% 수준이다. 전량 재수집이 필요하면 관리자 트리거(only=venues)로 수동 실행한다.
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void daily() {
        log.info("정기 동기화 시작");
        syncService.syncVenuesIncremental();
        syncService.syncPerformances();
        syncService.syncFestivals();
        syncService.syncSeoulEvents();
    }
}
