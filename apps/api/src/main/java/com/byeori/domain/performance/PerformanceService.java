package com.byeori.domain.performance;

import com.byeori.domain.performance.dto.PerformanceResponse;
import com.byeori.global.exception.NotFoundException;
import com.byeori.global.external.TourApiClient;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PerformanceService {

    private final PerformanceRepository repo;
    private final TourApiClient tourClient;

    public PerformanceService(PerformanceRepository repo, TourApiClient tourClient) {
        this.repo = repo;
        this.tourClient = tourClient;
    }

    public Page<PerformanceResponse> list(String state, String genre, Long venueId, String keyword,
                                          Boolean traditional, Pageable pageable) {
        return repo.search(state, java.time.LocalDate.now(), genre, venueId, keyword, traditional, pageable)
                .map(PerformanceResponse::from);
    }

    /**
     * 행사 상세. 공사에서 받은 축제는 소개글을 그 시점에 조회해 함께 준다.
     * KOPIS·서울 행사는 콘텐츠 ID가 없어 저장된 정보만으로 화면을 만든다.
     * 조회가 실패해도 화면은 떠야 하므로 소개글만 비워 응답한다.
     */
    public PerformanceResponse detail(Long id) {
        Performance p = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("PERFORMANCE_NOT_FOUND", "공연을 찾을 수 없습니다."));
        PerformanceResponse res = PerformanceResponse.from(p);
        if (p.getTourContentId() == null) return res;
        var d = tourClient.detail(p.getTourContentId());
        return d == null ? res : res.withLive(d.overview(), firstUrl(d.homepage()));
    }

    /** 공사 homepage 는 "www.example.com" 처럼 설명이 섞여 오기도 한다. 주소만 뽑는다. */
    private static String firstUrl(String raw) {
        if (raw == null || raw.isBlank()) return null;
        var m = java.util.regex.Pattern.compile("(https?://\\S+|www\\.\\S+)").matcher(raw);
        if (!m.find()) return null;
        String u = m.group(1).replaceAll("[,)\\]}'\"]+$", "");
        return u.startsWith("http") ? u : "https://" + u;
    }

    /**
     * 장소 상세용. 끝난 행사는 빼고 진행 중·예정만, 임박한 순으로 몇 건만 준다.
     *
     * 공연시설명 매칭이 붙으면서 예술의전당처럼 145건이 달리는 장소가 생겼다.
     * 상세 화면의 곁다리 섹션이라 다 내려봐야 화면도 응답도 무거워지기만 한다.
     */
    private static final int VENUE_PERFORMANCE_LIMIT = 10;

    public List<PerformanceResponse> byVenue(Long venueId) {
        return repo.findVisibleByVenue(venueId, java.time.LocalDate.now(),
                        org.springframework.data.domain.PageRequest.of(0, VENUE_PERFORMANCE_LIMIT))
                .stream().map(PerformanceResponse::from).toList();
    }
}
