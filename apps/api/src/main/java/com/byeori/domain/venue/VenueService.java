package com.byeori.domain.venue;

import com.byeori.domain.venue.dto.VenueCreateRequest;
import com.byeori.domain.venue.dto.VenueDetailResponse;
import com.byeori.domain.venue.dto.VenueReportRequest;
import com.byeori.domain.venue.dto.VenueResponse;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import com.byeori.global.external.TourApiClient;
import java.util.List;
import com.byeori.global.external.dto.TourItem;
import com.byeori.domain.sync.CategoryMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VenueService {

    /** 공사 locationBasedList2가 받는 최대 반경(m). */
    private static final int TOUR_RADIUS_MAX = 20_000;

    private final VenueRepository repo;
    private final VenueReportRepository reportRepo;
    private final TourApiClient tourClient;

    public VenueService(VenueRepository repo, VenueReportRepository reportRepo, TourApiClient tourClient) {
        this.repo = repo;
        this.reportRepo = reportRepo;
        this.tourClient = tourClient;
    }

    public Page<VenueResponse> list(String category, Boolean hanbokDiscount, String keyword, Pageable pageable) {
        return repo.search(category, hanbokDiscount, keyword, pageable).map(VenueResponse::from);
    }

    /**
     * 지도 주변 조회. 보고 있는 영역만 한국관광공사 OpenAPI로 실시간 조회한다.
     *
     * 전국 데이터를 미리 받아두고 거르는 대신, 지도가 멈춘 좌표 기준 반경만 그때그때 부른다.
     * 공사 응답에는 한복 혜택·평점 같은 자체 정보가 없으므로, 콘텐츠 ID로 우리 레코드를 찾아
     * 붙인다. 저장된 장소가 없으면 id는 null이고 상세로 들어갈 때 콘텐츠 ID로 조회한다.
     *
     * 공사 API가 실패하면 빈 목록이 오므로, 그때는 저장된 데이터로 대체해 화면이 비지 않게 한다.
     */
    public java.util.List<VenueResponse> nearby(double lat, double lng, int radius, String category) {
        // 공사 locationBasedList2는 반경 상한이 20km다. 그보다 넓게 보고 있으면
        // 실시간으로는 화면 한가운데만 채워져 나머지가 텅 빈다(전국 뷰에서 서울 주변만
        // 마커가 뜨던 원인). 이 구간은 저장 스냅샷으로 화면 전체를 덮는다.
        if (radius > TOUR_RADIUS_MAX) return wideFromStore(lat, lng, radius, category, 300);

        int typeId = CategoryMapper.toTourContentTypeId(category);
        var items = tourClient.locationBasedList(lng, lat, radius, typeId, 50);
        // 공사 API가 실패하면 같은 영역의 저장 데이터로 대체한다. 전국 상위 목록으로 대체하면
        // 화면 밖 장소만 잡혀 지도가 비어 보이므로, 보고 있는 사각 영역으로 좁혀서 찾는다.
        if (items.isEmpty()) return nearbyFromStore(lat, lng, radius, category, 50);

        return enrich(items);
    }

    /**
     * 키워드 검색. 저장된 목록 대신 공사 OpenAPI를 실시간으로 조회한다.
     *
     * 저장분은 관광지·문화시설·음식점 세 유형뿐이라 숙박·쇼핑·레포츠가 빠져 있었다.
     * searchKeyword2는 전 유형을 대상으로 해 '한옥' 기준 57건 → 210건으로 늘어난다.
     *
     * 한복 혜택과 평점은 공사 데이터에 없는 자체 정보라 API 단계에서 거를 수 없다.
     * 콘텐츠 ID로 우리 레코드를 붙인 뒤 여기서 거르고 정렬한다.
     */
    public java.util.List<VenueResponse> searchLive(String keyword, String category, Boolean hanbokDiscount, int rows) {
        if (keyword == null || keyword.isBlank()) return List.of();
        var items = tourClient.searchKeyword(keyword, CategoryMapper.toTourContentTypeId(category), rows);
        if (items.isEmpty()) return List.of();

        var out = enrich(items);
        if (Boolean.TRUE.equals(hanbokDiscount)) {
            out = out.stream().filter(VenueResponse::hanbokDiscount).toList();
        }
        // 저장 목록과 같은 기준으로 정렬한다: 이미지 있는 것 먼저, 그 다음 평점·리뷰 수.
        return out.stream()
                .sorted(java.util.Comparator
                        .comparing((VenueResponse v) -> v.imageUrl() == null || v.imageUrl().isBlank())
                        .thenComparing(VenueResponse::avgRating, java.util.Comparator.reverseOrder())
                        .thenComparing(VenueResponse::reviewCount, java.util.Comparator.reverseOrder()))
                .toList();
    }

    /** 전국 뷰용 표본. 한 지역이 목록을 독식하지 않도록 고르게 흩뿌려 뽑는다. */
    private java.util.List<VenueResponse> wideFromStore(double lat, double lng, int radius, String category, int limit) {
        double dLat = radius / 111_000.0, dLng = radius / 88_000.0;
        return repo.sampleInBounds(
                        java.math.BigDecimal.valueOf(lat - dLat), java.math.BigDecimal.valueOf(lat + dLat),
                        java.math.BigDecimal.valueOf(lng - dLng), java.math.BigDecimal.valueOf(lng + dLng),
                        category == null || category.isBlank() ? null : category, limit)
                .stream().map(VenueResponse::from).toList();
    }

    /** 저장 스냅샷 조회. 위도 1도 ≈ 111km, 경도 1도 ≈ 88km(한국 위도 기준)로 사각 영역을 잡는다. */
    private java.util.List<VenueResponse> nearbyFromStore(double lat, double lng, int radius, String category, int limit) {
        double dLat = radius / 111_000.0, dLng = radius / 88_000.0;
        return repo.findInBounds(
                        java.math.BigDecimal.valueOf(lat - dLat), java.math.BigDecimal.valueOf(lat + dLat),
                        java.math.BigDecimal.valueOf(lng - dLng), java.math.BigDecimal.valueOf(lng + dLng),
                        category == null || category.isBlank() ? null : category,
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .stream().map(VenueResponse::from).toList();
    }

    /** 공사 응답에 우리 레코드(한복 혜택·평점)를 콘텐츠 ID로 붙인다. 저장분이 없으면 id는 null. */
    private java.util.List<VenueResponse> enrich(java.util.List<TourItem> items) {
        var ids = items.stream().map(TourItem::contentId).filter(java.util.Objects::nonNull).toList();
        // 콘텐츠 ID는 두 컬럼에 나뉘어 있다. 시드 장소(경복궁·창경궁 등 한복 혜택 보유)는
        // tour_content_id가 자리표시자라 detail_content_id로만 맞물린다. 둘 다 훑는다.
        var mine = new java.util.HashMap<String, Venue>();
        repo.findByTourContentIdIn(ids).forEach(v -> mine.putIfAbsent(v.getTourContentId(), v));
        repo.findByDetailContentIdIn(ids).forEach(v -> mine.put(v.getDetailContentId(), v));

        return items.stream()
                .filter(it -> it.contentId() != null && it.mapy() != null && it.mapx() != null)
                .map(it -> {
                    Venue v = mine.get(it.contentId());
                    String cat = CategoryMapper.fromTour(it.contentTypeId(), it.lclsSystm2(), it.lclsSystm3());
                    return new VenueResponse(
                            v != null ? v.getId() : null,
                            it.title(), it.addr1(), cat, it.firstImage(),
                            v != null && v.isHanbokDiscount(),
                            v != null ? v.getHanbokDiscountDesc() : null,
                            v != null ? v.getAvgRating() : java.math.BigDecimal.ZERO,
                            v != null && v.getReviewCount() != null ? v.getReviewCount() : 0,
                            "TOURAPI",
                            new java.math.BigDecimal(it.mapy()), new java.math.BigDecimal(it.mapx()),
                            it.contentId());
                })
                .toList();
    }

    /**
     * 장소 상세. 한국관광공사 콘텐츠 ID가 있는 장소는 공사 OpenAPI를 실시간으로 조회해
     * 개요·이용시간·휴무일·문의처를 함께 내려준다(동기화 목록에는 없는 항목들이다).
     * 조회가 실패하거나 느려도 화면은 떠야 하므로 실패 시 저장된 정보만으로 응답한다.
     */
    /**
     * 상세 조회. 실시간 결과의 절반 가까이는 우리 DB에 없어(경복궁 반경 2km 기준 50건 중 24건)
     * 우리 id가 없다. 숫자면 우리 레코드, 아니면 공사 콘텐츠 ID로 본다.
     */
    public VenueDetailResponse detailByKey(String key) {
        if (key != null && key.chars().allMatch(Character::isDigit) && key.length() < 19) {
            Long id = Long.valueOf(key);
            // 콘텐츠 ID도 숫자라 우리 id와 겹칠 수 있다. 우리 레코드가 먼저다.
            var mine = repo.findById(id);
            if (mine.isPresent()) return VenueDetailResponse.from(mine.get(), tourClient.detail(mine.get().getDetailContentId()));
        }
        return detailByContentId(key);
    }

    private VenueDetailResponse detailByContentId(String contentId) {
        // 저장분이 있으면 자체 정보(한복 혜택·평점)까지 붙은 쪽을 쓴다.
        var stored = repo.findByTourContentId(contentId)
                .or(() -> repo.findByDetailContentIdIn(java.util.List.of(contentId)).stream().findFirst());
        if (stored.isPresent()) {
            return VenueDetailResponse.from(stored.get(), tourClient.detail(contentId));
        }
        var basic = tourClient.basic(contentId);
        if (basic == null) {
            throw new NotFoundException("VENUE_NOT_FOUND", "장소를 찾을 수 없습니다.");
        }
        String category = CategoryMapper.fromTour(basic.contentTypeId(), basic.lclsSystm2(), basic.lclsSystm3());
        return VenueDetailResponse.fromTour(basic, tourClient.detail(contentId), category);
    }

    public VenueDetailResponse detail(Long id) {
        Venue v = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("VENUE_NOT_FOUND", "장소를 찾을 수 없습니다."));
        return VenueDetailResponse.from(v, tourClient.detail(v.getDetailContentId()));
    }

    public java.util.List<VenueResponse> listMine(Long userId) {
        return repo.findByCreatedByUserIdOrderByCreatedAtDesc(userId).stream().map(VenueResponse::from).toList();
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
