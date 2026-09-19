package com.byeori.domain.venue;

import com.byeori.domain.venue.dto.VenueCreateRequest;
import com.byeori.domain.venue.dto.VenueDetailResponse;
import com.byeori.domain.venue.dto.VenueReportRequest;
import com.byeori.domain.venue.dto.VenueResponse;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import com.byeori.domain.sync.CategoryMapper;
import com.byeori.global.external.TourApiClient;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VenueService {

    /** 이보다 넓게 보고 있으면 전국 뷰로 보고 표본을 고르게 뽑는다. */
    private static final int WIDE_RADIUS = 20_000;

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
    /**
     * 지도 주변·지역 조회. 저장된 데이터로 응답한다.
     *
     * 예전에는 공사 locationBasedList2 를 요청마다 호출했는데, 목록은 명칭·주소·좌표만
     * 쓰므로 실시간일 필요가 없다. 저장분으로 주면 571ms → 330ms 로 줄고, 공사 API 가
     * 느리거나 인증키가 막혀도 목록은 그대로 뜬다.
     *
     * 대신 상세 화면은 열 때마다 공사 API 를 호출한다(detail 참고). 이용시간·휴무일처럼
     * 자주 바뀌는 정보는 저장해 두면 금세 낡기 때문이다.
     */
    public List<VenueResponse> nearby(double lat, double lng, int radius, String category) {
        // 전국이 보이도록 축소한 구간은 한 지역이 목록을 독식하지 않게 고르게 흩뿌려 뽑는다.
        if (radius > WIDE_RADIUS) return wideFromStore(lat, lng, radius, category, 300);
        return nearbyFromStore(lat, lng, radius, category, 50);
    }

    /**
     * 키워드 검색. 저장된 목록 대신 공사 OpenAPI를 실시간으로 조회한다.
     *
     * 저장분은 관광지·문화시설·음식점 세 유형뿐이라 숙박·쇼핑·레포츠가 빠져 있었다.
     *
     * 한복 혜택과 평점은 공사 데이터에 없는 자체 정보라 API 단계에서 거를 수 없다.
     * 콘텐츠 ID로 우리 레코드를 붙인 뒤 여기서 거르고 정렬한다.
     */
    /**
     * 명칭 검색. 저장된 데이터에서 찾는다.
     *
     * 공사 searchKeyword2 를 쓰면 저장에 없는 장소까지 나오지만, 수집 범위를 넓혀
     * (관광지·문화시설·음식점·레포츠·전통시장·공예·한옥) 그 격차가 거의 사라졌다.
     * 검색은 입력할 때마다 도는 경로라 응답이 빠른 쪽이 낫다.
     */
    public List<VenueResponse> searchLive(String keyword, String category, Boolean hanbokDiscount, int rows) {
        if (keyword == null || keyword.isBlank()) return List.of();
        var page = PageRequest.of(0, Math.min(rows, 100));
        return toResponses(repo.search(nullIfBlank(category), hanbokDiscount, keyword.trim(), page).getContent());
    }

    /** 좌표·반경을 사각 영역으로. 위도 1도 ≈ 111km, 경도 1도 ≈ 88km(한국 위도 기준). */
    private record Bounds(BigDecimal minLat, BigDecimal maxLat, BigDecimal minLng, BigDecimal maxLng) {
        static Bounds of(double lat, double lng, int radius) {
            double dLat = radius / 111_000.0, dLng = radius / 88_000.0;
            return new Bounds(BigDecimal.valueOf(lat - dLat), BigDecimal.valueOf(lat + dLat),
                    BigDecimal.valueOf(lng - dLng), BigDecimal.valueOf(lng + dLng));
        }
    }

    private static String nullIfBlank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /**
     * 전국 뷰용 표본. 평점·id 순으로 뽑으면 수집 순서상 한 지역이 목록을 독식하므로
     * (실제로 300건이 전부 대구였다) 해시로 섞어 고르게 흩뿌린다.
     */
    private List<VenueResponse> wideFromStore(double lat, double lng, int radius, String category, int limit) {
        Bounds b = Bounds.of(lat, lng, radius);
        return toResponses(repo.sampleInBounds(
                b.minLat(), b.maxLat(), b.minLng(), b.maxLng(), nullIfBlank(category), limit));
    }

    /** 보고 있는 영역 안에서 좋은 것부터 준다. */
    private List<VenueResponse> nearbyFromStore(double lat, double lng, int radius, String category, int limit) {
        Bounds b = Bounds.of(lat, lng, radius);
        return toResponses(repo.findInBounds(
                b.minLat(), b.maxLat(), b.minLng(), b.maxLng(), nullIfBlank(category), PageRequest.of(0, limit)));
    }

    private static List<VenueResponse> toResponses(List<Venue> venues) {
        return venues.stream().map(VenueResponse::from).toList();
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
                .or(() -> repo.findByDetailContentIdIn(List.of(contentId)).stream().findFirst());
        if (stored.isPresent()) {
            return VenueDetailResponse.from(stored.get(), tourClient.detail(contentId));
        }
        var basic = tourClient.basic(contentId);
        if (basic == null) {
            throw new NotFoundException("VENUE_NOT_FOUND", "장소를 찾을 수 없습니다.");
        }
        String category = CategoryMapper.fromTour(basic.contentTypeId(), basic.lclsSystm2(), basic.lclsSystm3());
        if (category == null) category = "문화";   // 우리 분류 밖이어도 상세는 보여준다
        return VenueDetailResponse.fromTour(basic, tourClient.detail(contentId), category);
    }

    public VenueDetailResponse detail(Long id) {
        Venue v = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("VENUE_NOT_FOUND", "장소를 찾을 수 없습니다."));
        return VenueDetailResponse.from(v, tourClient.detail(v.getDetailContentId()));
    }

    public List<VenueResponse> listMine(Long userId) {
        return repo.findByCreatedByUserIdAndSourceOrderByCreatedAtDesc(userId, "USER").stream().map(VenueResponse::from).toList();
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
