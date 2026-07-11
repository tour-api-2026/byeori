package com.byeori.domain.sync;

import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.RegionCode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 수집 대상 지역 해석. 전국 모든 시도를 법정동코드(ldongCode2)로 동적 조회. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegionResolver {

    private final TourApiClient tourClient;

    /** 동기화에 사용할 법정동 지역 단위. */
    public record Region(String regnCd, String signguCd, String label) {}

    public List<Region> resolve() {
        List<Region> out = new ArrayList<>();
        List<RegionCode> sidos = tourClient.ldongCodes(null); // 시도 목록(전국)
        if (sidos.isEmpty()) {
            log.warn("ldongCode2 시도 목록 비어있음 → 지역 해석 불가");
            return out;
        }
        // 전국: 모든 시도를 대상으로 수집(시군구 null = 시도 전체).
        // 특정 도시로 제한하려면 SyncProperties.TARGETS 매칭 방식으로 되돌리면 된다.
        for (RegionCode sido : sidos) {
            if (sido.code() == null || sido.name() == null) continue;
            out.add(new Region(sido.code(), null, sido.name()));
        }
        log.info("지역 해석 완료(전국): {}개 시도", out.size());
        return out;
    }
}
