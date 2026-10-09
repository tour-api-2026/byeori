package com.byeori.domain.activity;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추천의 재료가 되는 행동을 남긴다.
 *
 * 세 가지를 지킨다.
 *
 * ① **조회 응답을 막지 않는다.** 기록이 실패했다고 장소 상세가 안 보이면 안 된다. 비동기로
 *    떨어뜨리고, 터지면 로그만 남긴다. 호출부에서 예외를 신경 쓸 일이 없어야 한다.
 * ② **로그인한 사람만 남긴다.** userId 가 없으면 조용히 지나간다. 주인 없는 기록은
 *    개인화에 쓸 수 없는데 보관 범위만 넓힌다.
 * ③ **같은 곳을 금방 다시 봐도 한 번으로 센다.** 상세를 열고 뒤로 가고 다시 여는 건 흔한
 *    동작인데, 그대로 세면 한 번 본 곳이 여러 번 본 곳으로 둔갑해 인기 점수가 망가진다.
 *
 * REQUIRES_NEW 를 쓰는 이유: 호출한 조회가 트랜잭션 안이면 기록 실패가 그 트랜잭션을
 * 롤백 표시해 버린다. 기록은 조회와 운명을 같이하지 않아야 한다.
 */
@Service
public class ActivityLogger {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogger.class);

    /** 같은 사람·같은 항목을 이 시간 안에 다시 봐도 한 번으로 친다. */
    private static final int DEDUPE_MINUTES = 30;

    private final ViewLogRepository viewRepo;
    private final SearchLogRepository searchRepo;

    public ActivityLogger(ViewLogRepository viewRepo, SearchLogRepository searchRepo) {
        this.viewRepo = viewRepo;
        this.searchRepo = searchRepo;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void view(Long userId, String targetType, Long targetId, String source) {
        if (userId == null || targetId == null) return;
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(DEDUPE_MINUTES);
            if (viewRepo.existsByUserIdAndTargetTypeAndTargetIdAndViewedAtAfter(userId, targetType, targetId, since)) {
                return;
            }
            viewRepo.save(new ViewLog(userId, targetType, targetId, clip(source, 20)));
        } catch (Exception e) {
            log.warn("조회 기록 실패 user={} {} {}: {}", userId, targetType, targetId, e.toString());
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void search(Long userId, String keyword, String category, int resultCount) {
        if (userId == null || keyword == null || keyword.isBlank()) return;
        try {
            searchRepo.save(new SearchLog(userId, clip(keyword.strip(), 100), clip(category, 40), resultCount));
        } catch (Exception e) {
            log.warn("검색 기록 실패 user={} '{}': {}", userId, keyword, e.toString());
        }
    }

    /** 컬럼 길이를 넘기면 저장이 통째로 터진다. 기록 때문에 로그가 시끄러워질 이유가 없다. */
    private static String clip(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
