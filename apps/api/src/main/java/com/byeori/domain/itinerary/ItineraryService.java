package com.byeori.domain.itinerary;

import com.byeori.domain.course.CuratedCourseItem;
import com.byeori.domain.course.CuratedCourseItemRepository;
import com.byeori.domain.itinerary.dto.ItineraryDtos.*;
import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.content.ContentTarget;
import com.byeori.global.content.ContentType;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import com.byeori.global.external.KakaoMobilityClient;
import com.byeori.global.external.dto.KakaoRoute;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ItineraryService {

    private final ItineraryRepository repo;
    private final ItineraryItemRepository itemRepo;
    private final CuratedCourseItemRepository courseItemRepo;
    private final VenueRepository venueRepo;
    private final PerformanceRepository performanceRepo;
    private final KakaoMobilityClient mobilityClient;

    public ItineraryService(ItineraryRepository repo, ItineraryItemRepository itemRepo,
                            CuratedCourseItemRepository courseItemRepo,
                            VenueRepository venueRepo, PerformanceRepository performanceRepo,
                            KakaoMobilityClient mobilityClient) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.courseItemRepo = courseItemRepo;
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
        this.mobilityClient = mobilityClient;
    }

    public List<Summary> listMine(Long userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(i -> Summary.from(i, itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(i.getId()).size()))
                .toList();
    }

    public Detail get(Long userId, Long id) {
        Itinerary i = own(userId, id);
        List<ItemResponse> items = itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(id).stream()
                .map(this::toItemResponse).toList();
        return Detail.from(i, items);
    }

    /**
     * 여행 일지의 방문지들을 순서대로 잇는 도로 경로 계산(카카오모빌리티).
     * 좌표 없는 항목은 건너뛰고, 좌표 있는 방문지가 2곳 이상일 때만 경로를 반환.
     */
    public RouteResponse route(Long userId, Long id, String priority) {
        own(userId, id);
        if (!mobilityClient.enabled()) {
            throw new BadRequestException("ROUTE_UNAVAILABLE", "길찾기 키가 설정되지 않았습니다.");
        }
        List<RouteStop> stops = new ArrayList<>();
        List<double[]> coords = new ArrayList<>(); // [경도, 위도] 순서(카카오 요청용)
        int order = 0;
        for (ItineraryItem item : itemRepo.findByItineraryIdOrderByVisitDateAscSortOrderAsc(id)) {
            BigDecimal lat, lng;
            String name, type;
            Long targetId;
            if (item.getPerformanceId() != null) {
                Performance p = performanceRepo.findById(item.getPerformanceId()).orElse(null);
                if (p == null) continue;
                lat = p.getLat(); lng = p.getLng(); name = p.getTitle();
                type = "PERFORMANCE"; targetId = p.getId();
            } else if (item.getVenueId() != null) {
                Venue v = venueRepo.findById(item.getVenueId()).orElse(null);
                if (v == null) continue;
                lat = v.getLat(); lng = v.getLng(); name = v.getName();
                type = "VENUE"; targetId = v.getId();
            } else {
                continue;
            }
            if (lat == null || lng == null) continue; // 좌표 없는 항목은 경로에서 제외
            stops.add(new RouteStop(order++, type, targetId, name, lat.doubleValue(), lng.doubleValue()));
            coords.add(new double[]{lng.doubleValue(), lat.doubleValue()});
        }
        if (coords.size() < 2) {
            throw new BadRequestException("ROUTE_TOO_FEW_POINTS", "경로를 그리려면 좌표가 있는 방문지가 2곳 이상이어야 합니다.");
        }
        String pr = priority == null || priority.isBlank() ? "RECOMMEND" : priority;
        KakaoRoute r = mobilityClient.directions(coords, pr);
        if (r == null) {
            throw new BadRequestException("ROUTE_FAILED", "경로를 계산하지 못했습니다. 잠시 후 다시 시도해주세요.");
        }
        // path: [경도,위도] → [위도,경도] (지도 LatLng 순서)
        List<double[]> path = r.path().stream().map(p -> new double[]{p[1], p[0]}).toList();
        List<RouteLeg> legs = r.legs().stream().map(l -> new RouteLeg(l[0], l[1], l[2])).toList();
        return new RouteResponse(r.distance(), r.duration(), pr, stops, legs, path);
    }

    @Transactional
    public Detail create(Long userId, CreateRequest req) {
        String sourceType = "CURATED".equals(req.sourceType()) ? "CURATED" : "CUSTOM";
        Itinerary saved = repo.save(new Itinerary(userId, req.title(), req.startDate(), req.endDate(),
                sourceType, sourceType.equals("CURATED") ? req.sourceCourseId() : null));

        // 추천 코스 복사 (CURATED): 코스 항목을 일지 항목으로 독립 복사
        if (sourceType.equals("CURATED") && req.sourceCourseId() != null) {
            List<CuratedCourseItem> courseItems = courseItemRepo.findByCourseIdOrderBySortOrderAsc(req.sourceCourseId());
            for (CuratedCourseItem ci : courseItems) {
                itemRepo.save(new ItineraryItem(saved.getId(), ci.getPerformanceId(), ci.getVenueId(),
                        req.startDate(), ci.getSortOrder(), ci.getRecommendedTime(), ci.getNote()));
            }
        }
        return get(userId, saved.getId());
    }

    @Transactional
    public Detail update(Long userId, Long id, UpdateRequest req) {
        Itinerary i = own(userId, id);
        i.update(req.title(), req.startDate(), req.endDate());
        return get(userId, id);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        own(userId, id);
        itemRepo.deleteByItineraryId(id);
        repo.deleteById(id);
    }

    @Transactional
    public ItemResponse addItem(Long userId, Long id, ItemRequest req) {
        own(userId, id);
        ContentTarget t = new ContentTarget(ContentType.from(req.targetType()), req.targetId());
        ItineraryItem saved = itemRepo.save(new ItineraryItem(id, t.performanceId(), t.venueId(),
                req.visitDate(), req.sortOrder(), req.plannedTime(), req.memo()));
        return toItemResponse(saved);
    }

    @Transactional
    public ItemResponse updateItem(Long userId, Long id, Long itemId, ItemRequest req) {
        own(userId, id);
        ItineraryItem item = itemRepo.findById(itemId)
                .orElseThrow(() -> new NotFoundException("ITEM_NOT_FOUND", "일정 항목을 찾을 수 없습니다."));
        if (!item.getItineraryId().equals(id)) throw new BadRequestException("ITEM_MISMATCH", "해당 일지의 항목이 아닙니다.");
        item.update(req.visitDate(), req.sortOrder(), req.plannedTime(), req.memo());
        return toItemResponse(item);
    }

    @Transactional
    public void deleteItem(Long userId, Long id, Long itemId) {
        own(userId, id);
        itemRepo.findById(itemId).ifPresent(item -> {
            if (!item.getItineraryId().equals(id)) throw new BadRequestException("ITEM_MISMATCH", "해당 일지의 항목이 아닙니다.");
            itemRepo.delete(item);
        });
    }

    private Itinerary own(Long userId, Long id) {
        Itinerary i = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("ITINERARY_NOT_FOUND", "여행 일지를 찾을 수 없습니다."));
        if (!i.getUserId().equals(userId)) throw new BadRequestException("ITINERARY_FORBIDDEN", "본인 일지만 접근할 수 있습니다.");
        return i;
    }

    private ItemResponse toItemResponse(ItineraryItem item) {
        ContentTarget t = ContentTarget.of(item.getPerformanceId(), item.getVenueId());
        String name = null, image = null;
        if (t.targetType() == ContentType.VENUE) {
            Venue v = venueRepo.findById(t.targetId()).orElse(null);
            if (v != null) { name = v.getName(); image = v.getImageUrl(); }
        } else {
            Performance p = performanceRepo.findById(t.targetId()).orElse(null);
            if (p != null) { name = p.getTitle(); image = p.getPosterImageUrl(); }
        }
        return new ItemResponse(item.getId(), t.targetType().name(), t.targetId(), name, image,
                item.getVisitDate(), item.getSortOrder(), item.getPlannedTime(), item.getMemo());
    }
}
