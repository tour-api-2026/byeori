package com.byeori.domain.venue;

import com.byeori.domain.venue.dto.VenueCreateRequest;
import com.byeori.domain.venue.dto.VenueDetailResponse;
import com.byeori.domain.venue.dto.VenueReportRequest;
import com.byeori.domain.venue.dto.VenueResponse;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository repo;
    private final VenueReportRepository reportRepo;

    public VenueService(VenueRepository repo, VenueReportRepository reportRepo) {
        this.repo = repo;
        this.reportRepo = reportRepo;
    }

    public Page<VenueResponse> list(String category, Boolean hanbokDiscount, String keyword, Pageable pageable) {
        return repo.search(category, hanbokDiscount, keyword, pageable).map(VenueResponse::from);
    }

    public VenueDetailResponse detail(Long id) {
        Venue v = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("VENUE_NOT_FOUND", "장소를 찾을 수 없습니다."));
        return VenueDetailResponse.from(v);
    }

    @Transactional
    public VenueDetailResponse createUserVenue(Long userId, VenueCreateRequest req) {
        if (req.name() == null || req.address() == null || req.lat() == null || req.lng() == null) {
            throw new BadRequestException("VENUE_INVALID", "이름·주소·좌표는 필수입니다.");
        }
        Venue v = repo.save(Venue.userVenue(userId, req.name(), req.address(), req.lat(), req.lng(),
                req.category(), req.phone(), req.homepageUrl(), req.operatingHours(), req.imageUrl(), req.description()));
        return VenueDetailResponse.from(v);
    }

    @Transactional
    public VenueDetailResponse updateUserVenue(Long userId, Long id, VenueCreateRequest req) {
        Venue v = own(userId, id);
        v.updateUserVenue(req.name(), req.address(), req.category(), req.phone(),
                req.homepageUrl(), req.operatingHours(), req.imageUrl(), req.description());
        return VenueDetailResponse.from(v);
    }

    @Transactional
    public void deleteUserVenue(Long userId, Long id) {
        Venue v = own(userId, id);
        repo.delete(v);
    }

    @Transactional
    public void report(Long userId, Long venueId, VenueReportRequest req) {
        repo.findById(venueId).orElseThrow(() -> new NotFoundException("VENUE_NOT_FOUND", "장소를 찾을 수 없습니다."));
        if (req.reason() == null || req.reason().isBlank()) {
            throw new BadRequestException("REPORT_INVALID", "신고 사유는 필수입니다.");
        }
        reportRepo.save(new VenueReport(venueId, userId, req.reason(), req.detail()));
    }

    private Venue own(Long userId, Long id) {
        Venue v = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("VENUE_NOT_FOUND", "장소를 찾을 수 없습니다."));
        if (!v.isOwnedBy(userId)) {
            throw new BadRequestException("VENUE_FORBIDDEN", "본인이 등록한 장소만 수정/삭제할 수 있습니다.");
        }
        return v;
    }
}
