package com.byeori.domain.venue;

import com.byeori.domain.venue.dto.VenueDetailResponse;
import com.byeori.domain.venue.dto.VenueResponse;
import com.byeori.global.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository repo;

    public VenueService(VenueRepository repo) {
        this.repo = repo;
    }

    public Page<VenueResponse> list(String category, Boolean hanbokDiscount, String keyword, Pageable pageable) {
        return repo.search(category, hanbokDiscount, keyword, pageable).map(VenueResponse::from);
    }

    public VenueDetailResponse detail(Long id) {
        Venue v = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("VENUE_NOT_FOUND", "장소를 찾을 수 없습니다."));
        return VenueDetailResponse.from(v);
    }
}
