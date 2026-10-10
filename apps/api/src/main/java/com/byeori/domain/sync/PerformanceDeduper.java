package com.byeori.domain.sync;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출처가 다른 같은 행사를 한 줄만 보이게 한다.
 *
 * 한 번 치우는 일이 아니다. 매일 05:00 동기화가 KOPIS·서울 열린데이터에서 각각 받아오고,
 * 중복 방지는 출처 안에서만 돌기 때문에(kopis_id / seoul_id) 치워도 다음날 또 생긴다.
 * 그래서 동기화 끝에 매번 쓸고 간다({@link SyncScheduler#daily()}).
 *
 * 넣는 지점에서 검사하지 않고 끝나고 한 번 쓰는 이유:
 * <ul>
 *   <li>넣는 경로가 셋(공연·축제·서울 행사)이라 한 곳만 빠뜨려도 새는 길이 생긴다.</li>
 *   <li>동기화가 제목을 고치므로, 어제 중복이 아니던 것이 오늘 중복이 된다. 매번 다시
 *       판정하면 저절로 맞춰지고, 제목이 갈라지면 숨김도 저절로 풀린다.</li>
 * </ul>
 *
 * 판정은 보수적이다 — 기간(시작일·종료일)이 같고, 출처가 다르고,
 * {@link PerformanceDuplicateMatcher}가 제목으로 같은 행사라고 할 때만 가린다.
 * 놓치는 쪽이 다른 행사를 하나로 합치는 쪽보다 낫다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceDeduper {

    private final PerformanceRepository perfRepo;

    /**
     * 쓸기 결과. 숫자가 평소와 다르면 규칙이나 원본이 달라진 신호라 로그에 남긴다.
     *
     * @param candidates 살펴본 행사 수(아직 끝나지 않은 것)
     * @param hidden     그 중 가려진 수
     * @param added      이번에 새로 가린 수
     * @param released   이번에 숨김이 풀린 수
     */
    public record Summary(int candidates, int hidden, int added, int released) {
        public String describe() {
            return "후보 %d건 중 %d건 숨김 (새로 %d건, 해제 %d건)".formatted(candidates, hidden, added, released);
        }
    }

    /**
     * 아직 끝나지 않은 행사를 전부 다시 판정한다.
     *
     * 끝난 행사는 손대지 않는다. 목록에 나오지 않으니 가릴 이유가 없고, 과거 데이터를
     * 건드리지 않는 쪽이 안전하다 — 이미 가려진 채 끝난 줄은 그대로 남는다.
     */
    @Transactional
    public Summary sweep() {
        LocalDate today = LocalDate.now();
        List<Performance> candidates = perfRepo.findDedupeCandidates(today);

        int added = 0;
        int released = 0;
        int hidden = 0;
        for (List<Performance> group : groupByPeriod(candidates).values()) {
            for (Map.Entry<Performance, Long> decision : decide(group).entrySet()) {
                Performance p = decision.getKey();
                Long keeper = decision.getValue();
                boolean was = p.getDuplicateOf() != null;
                boolean now = keeper != null;
                if (now) hidden++;
                if (now && !was) added++;
                if (!now && was) released++;
                if (!Objects.equals(p.getDuplicateOf(), keeper)) p.markDuplicateOf(keeper);
            }
        }

        Summary summary = new Summary(candidates.size(), hidden, added, released);
        log.info("행사 중복 쓸기: {}", summary.describe());
        return summary;
    }

    /** 기간이 같은 것끼리만 비교한다. 같은 날짜에 보통 수십 건이라 비교 비용이 작다. */
    private Map<String, List<Performance>> groupByPeriod(List<Performance> candidates) {
        Map<String, List<Performance>> groups = new LinkedHashMap<>();
        for (Performance p : candidates) {
            String key = p.getStartDate() + "~" + p.getEndDate();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        return groups;
    }

    /**
     * 같은 기간 안에서 각 행사가 누구의 중복인지(또는 아닌지) 정한다.
     *
     * 남길 쪽을 먼저 보도록 정렬해서 훑는다. 그러면 A→B→C 처럼 숨김이 이어지지 않고
     * 모두 한 줄을 가리킨다. 이미 가려진 줄은 남길 쪽이 될 수 없다.
     */
    private Map<Performance, Long> decide(List<Performance> group) {
        Map<Performance, Long> decisions = new LinkedHashMap<>();
        if (group.size() < 2) {
            group.forEach(p -> decisions.put(p, null));
            return decisions;
        }

        List<Performance> ordered = new ArrayList<>(group);
        ordered.sort(KEEPER_FIRST);

        List<Performance> keepers = new ArrayList<>();
        for (Performance p : ordered) {
            Performance keeper = keepers.stream()
                    .filter(k -> isDuplicate(p, k))
                    .findFirst().orElse(null);
            if (keeper != null) {
                decisions.put(p, keeper.getId());
            } else {
                decisions.put(p, null);
                keepers.add(p);
            }
        }
        return decisions;
    }

    /**
     * 남길 쪽을 고르는 순서.
     *
     * ① 장소가 이어진 줄 — 장소 상세에서 보이고, 지도에 찍히고, 루트에 담을 수 있다.
     * ② KOPIS — 예매처 링크와 포스터가 서울 열린데이터보다 고르게 들어 있다.
     * ③ id 가 작은 줄 — 먼저 들어온 것이고, 무엇보다 매번 같은 답이 나온다.
     */
    private static final Comparator<Performance> KEEPER_FIRST = Comparator
            .comparing((Performance p) -> p.getVenueId() == null)
            .thenComparing(p -> !"KOPIS".equals(p.getSource()))
            .thenComparing(Performance::getId);

    /** 출처가 다르고 제목이 같은 행사를 가리킬 때만 중복으로 본다. */
    private boolean isDuplicate(Performance a, Performance b) {
        if (a.getSource() == null || b.getSource() == null) return false;
        // 같은 출처 안의 중복은 외부 id 로 이미 막혀 있다. 그래도 남아 있다면 원본이
        // 정말 두 건으로 올린 것이므로 우리가 판단할 일이 아니다.
        if (a.getSource().equals(b.getSource())) return false;
        return PerformanceDuplicateMatcher.sameEvent(a.getTitle(), b.getTitle());
    }
}
