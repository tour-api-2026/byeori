package com.byeori.domain.sync;

import com.byeori.global.external.SyncProperties;
import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.RegionCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 대상 도시명 → 법정동코드(시도/시군구)로 변환. ldongCode2 동적 조회. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegionResolver {

    private final TourApiClient tourClient;

    /** 동기화에 사용할 법정동 지역 단위. */
    public record Region(String regnCd, String signguCd, String label) {}

    public List<Region> resolve() {
        List<Region> out = new ArrayList<>();
        List<RegionCode> sidos = tourClient.ldongCodes(null); // 시도 목록
        if (sidos.isEmpty()) {
            log.warn("ldongCode2 시도 목록 비어있음 → 지역 해석 불가");
            return out;
        }
        for (SyncProperties.Target t : SyncProperties.TARGETS) {
            RegionCode sido = sidos.stream()
                    .filter(s -> s.name() != null && containsAny(s.name(), t.sidoKeywords()))
                    .findFirst().orElse(null);
            if (sido == null) {
                log.warn("시도 매칭 실패: {}", t.label());
                continue;
            }
            if (t.sigunguKeyword() == null) {
                out.add(new Region(sido.code(), null, t.label()));
            } else {
                List<RegionCode> sgg = tourClient.ldongCodes(sido.code());
                List<RegionCode> matched = sgg.stream()
                        .filter(s -> s.name() != null && s.name().contains(t.sigunguKeyword()))
                        .toList();
                if (matched.isEmpty()) {
                    log.warn("시군구 매칭 실패: {} (시도 {})", t.label(), sido.name());
                } else {
                    matched.forEach(s -> out.add(new Region(sido.code(), s.code(), t.label())));
                }
            }
        }
        log.info("지역 해석 완료: {}개 단위", out.size());
        return out;
    }

    private static boolean containsAny(String name, String[] keywords) {
        for (String k : keywords) if (name.contains(k)) return true;
        return false;
    }
}
