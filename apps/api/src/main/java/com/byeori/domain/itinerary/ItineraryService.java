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

    public ItineraryService(ItineraryRepository repo, ItineraryItemRepository itemRepo,
                            CuratedCourseItemRepository courseItemRepo,
                            VenueRepository venueRepo, PerformanceRepository performanceRepo) {
        this.repo = repo;
        this.itemRepo = itemRepo;
        this.courseItemRepo = courseItemRepo;
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
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
