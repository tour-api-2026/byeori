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
        return repo.search(state, genre, venueId, keyword, traditional, pageable).map(PerformanceResponse::from);
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
        return d == null ? res : res.withOverview(d.overview());
    }

    public List<PerformanceResponse> byVenue(Long venueId) {
        return repo.findByVenueIdOrderByStartDateAsc(venueId).stream().map(PerformanceResponse::from).toList();
    }
}
