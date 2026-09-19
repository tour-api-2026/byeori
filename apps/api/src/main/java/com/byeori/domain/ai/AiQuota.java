package com.byeori.domain.ai;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 하루 생성 횟수 상한. 사용자당 한도와 서비스 전체 한도를 함께 센다.
 *
 * 비용이 걸린 기능이라 스크립트로 계속 부르면 요금이 무한히 늘 수 있다. 서버가 한 대라
 * 메모리에서 세면 충분하다. 재시작하면 초기화되지만, 그래도 하루 상한 이상은 나가지 않는다.
 */
class AiQuota {

    private final int perUser;
    private final int total;
    private final Clock clock;

    private LocalDate day;
    private final Map<Long, Integer> used = new HashMap<>();
    private int usedTotal;

    AiQuota(int perUser, int total, Clock clock) {
        this.perUser = perUser;
        this.total = total;
        this.clock = clock;
    }

    /** 한 번 쓸 수 있으면 차감하고 true. */
    synchronized boolean tryAcquire(Long userId) {
        rollover();
        if (usedTotal >= total || used.getOrDefault(userId, 0) >= perUser) return false;
        used.merge(userId, 1, Integer::sum);
        usedTotal++;
        return true;
    }

    /** AI 호출이 실패해 결과를 못 줬으면 되돌린다. 실패한 시도로 한도가 깎이면 억울하다. */
    synchronized void refund(Long userId) {
        rollover();
        used.computeIfPresent(userId, (k, v) -> v > 1 ? v - 1 : null);
        if (usedTotal > 0) usedTotal--;
    }

    synchronized int remaining(Long userId) {
        rollover();
        return Math.max(0, Math.min(perUser - used.getOrDefault(userId, 0), total - usedTotal));
    }

    synchronized boolean totalExhausted() {
        rollover();
        return usedTotal >= total;
    }

    private void rollover() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            used.clear();
            usedTotal = 0;
        }
    }
}
