package com.byeori.domain.activity;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 90일 지난 행동 기록을 지운다.
 *
 * 취향은 변하고 행사는 어차피 끝난다. 2년 전 조회가 오늘 추천을 흔들면 오히려 나쁘다.
 * 지우지 않으면 테이블이 한없이 커지기도 한다.
 *
 * 새벽 4시에 돈다 — 기존 데이터 동기화(5시)보다 앞서서, 둘이 겹치지 않게.
 */
@Component
public class ActivityRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActivityRetentionScheduler.class);
    private static final int KEEP_DAYS = 90;

    private final ViewLogRepository viewRepo;
    private final SearchLogRepository searchRepo;

    public ActivityRetentionScheduler(ViewLogRepository viewRepo, SearchLogRepository searchRepo) {
        this.viewRepo = viewRepo;
        this.searchRepo = searchRepo;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void purge() {
        LocalDateTime before = LocalDateTime.now().minusDays(KEEP_DAYS);
        int views = viewRepo.deleteOlderThan(before);
        int searches = searchRepo.deleteOlderThan(before);
        if (views + searches > 0) {
            log.info("행동 기록 정리: 조회 {}건, 검색 {}건 삭제 ({}일 경과)", views, searches, KEEP_DAYS);
        }
    }
}
