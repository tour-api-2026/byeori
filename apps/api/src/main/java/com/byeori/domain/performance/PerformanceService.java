package com.byeori.domain.performance;

import com.byeori.domain.performance.dto.PerformanceResponse;
import com.byeori.global.exception.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PerformanceService {

    private final PerformanceRepository repo;

    public PerformanceService(PerformanceRepository repo) {
        this.repo = repo;
    }

    public Page<PerformanceResponse> list(String state, String genre, Long venueId, String keyword, Pageable pageable) {
        return repo.search(state, genre, venueId, keyword, pageable).map(PerformanceResponse::from);
    }

    public PerformanceResponse detail(Long id) {
        Performance p = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("PERFORMANCE_NOT_FOUND", "공연을 찾을 수 없습니다."));
        return PerformanceResponse.from(p);
    }

    public List<PerformanceResponse> byVenue(Long venueId) {
        return repo.findByVenueIdOrderByStartDateAsc(venueId).stream().map(PerformanceResponse::from).toList();
    }
}
