package com.byeori.domain.translation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 상세 설명 번역의 하루 전체 상한.
 *
 * 상세 조회는 로그인 없이 열려 있어(관광객이 둘러보는 주 화면이다) 사용자별 한도를 걸 수 없다.
 * 장소 id 를 훑으면 수만 건의 번역을 우리 계정으로 일으킬 수 있으므로, 대신 하루 총량을 막는다.
 * 상한에 닿으면 한국어 설명을 주고 — 번역 실패와 같은 길이다 — 내일 다시 번역된다.
 * 이미 저장된 번역을 읽는 것은 세지 않는다. 서버가 한 대라 메모리로 센다(AiQuota 와 같다).
 */
@Component
@Slf4j
public class DetailTranslationCap {

    private final int limit;
    private final Clock clock;
    private LocalDate day;
    private int used;
    private boolean logged;

    @Autowired
    public DetailTranslationCap(@Value("${byeori.translation.detail-daily-cap:500}") int limit) {
        this(limit, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    DetailTranslationCap(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    /** 오늘 남은 몫. 상태를 바꾸지 않는다 — 테스트가 '깎였는지'를 보는 데 쓴다. */
    public synchronized int remaining() {
        LocalDate today = LocalDate.now(clock);
        return today.equals(day) ? Math.max(0, limit - used) : limit;
    }

    /** 한 번 쓸 수 있으면 차감하고 true. 상한에 닿는 첫 순간 하루 한 번만 로그를 남긴다. */
    public synchronized boolean tryAcquire() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            day = today;
            used = 0;
            logged = false;
        }
        if (used >= limit) {
            if (!logged) {
                log.warn("상세 설명 번역 하루 상한({})에 닿았다. 오늘은 한국어 설명을 준다.", limit);
                logged = true;
            }
            return false;
        }
        used++;
        return true;
    }
}
