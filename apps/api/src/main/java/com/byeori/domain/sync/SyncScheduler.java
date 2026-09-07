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

    // 공사 데이터는 더 이상 정기 수집하지 않는다.
    //
    // 지도·검색·상세·지역 탐색이 모두 실시간 호출로 바뀌어, 저장분은 공사 API가
    // 실패했을 때의 대체 표시로만 쓰인다. 대체용 스냅샷은 낡아도 무방하다
    // (명칭·주소·좌표는 거의 변하지 않는다).
    //
    // 게다가 전체 수집은 1회 약 350건을 호출하는데, 대량 수집을 돌린 뒤 하루이틀
    // 만에 인증키가 등록 해제되는 일이 반복됐다(9/1 수집 → 9/3 사망,
    // 9/6 수집 → 9/7 사망). 배경 호출을 없애 그 위험을 제거한다.
    // 재수집이 필요하면 POST /api/v1/admin/sync/trigger 로 수동 실행한다.
    //
    // 공사 외 출처(KOPIS·서울)는 별도 키라 영향이 없어 그대로 유지한다.
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void daily() {
        log.info("정기 동기화 시작 (공사 데이터 제외)");
        syncService.syncPerformances();
        syncService.syncSeoulEvents();
    }
}
