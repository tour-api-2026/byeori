package com.byeori.domain.course;

import com.byeori.domain.course.dto.CourseItemResponse;
import com.byeori.domain.course.dto.CuratedCourseDetailResponse;
import com.byeori.domain.course.dto.CuratedCourseResponse;
import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.exception.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CuratedCourseService {

    private final CuratedCourseRepository courseRepo;
    private final CuratedCourseItemRepository itemRepo;
    private final VenueRepository venueRepo;
    private final PerformanceRepository performanceRepo;

    public CuratedCourseService(CuratedCourseRepository courseRepo, CuratedCourseItemRepository itemRepo,
                                VenueRepository venueRepo, PerformanceRepository performanceRepo) {
        this.courseRepo = courseRepo;
        this.itemRepo = itemRepo;
        this.venueRepo = venueRepo;
        this.performanceRepo = performanceRepo;
    }

    public List<CuratedCourseResponse> list(String theme) {
        return courseRepo.findActive(theme).stream().map(CuratedCourseResponse::from).toList();
    }

    public CuratedCourseDetailResponse detail(Long id) {
        CuratedCourse course = courseRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND", "추천 코스를 찾을 수 없습니다."));
        List<CourseItemResponse> items = itemRepo.findByCourseIdOrderBySortOrderAsc(id).stream()
                .map(this::toItemResponse).toList();
        return CuratedCourseDetailResponse.from(course, items);
    }

    private CourseItemResponse toItemResponse(CuratedCourseItem item) {
        if (item.getVenueId() != null) {
            Venue v = venueRepo.findById(item.getVenueId()).orElse(null);
            return new CourseItemResponse(item.getId(), "VENUE", item.getVenueId(),
                    v == null ? null : v.getName(), v == null ? null : v.getImageUrl(),
                    item.getSortOrder(), item.getRecommendedTime(), item.getNote());
        }
        Performance p = performanceRepo.findById(item.getPerformanceId()).orElse(null);
        return new CourseItemResponse(item.getId(), "PERFORMANCE", item.getPerformanceId(),
                p == null ? null : p.getTitle(), p == null ? null : p.getPosterImageUrl(),
                item.getSortOrder(), item.getRecommendedTime(), item.getNote());
    }
}
