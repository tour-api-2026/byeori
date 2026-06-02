package com.byeori.domain.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuratedCourseItemRepository extends JpaRepository<CuratedCourseItem, Long> {
    List<CuratedCourseItem> findByCourseIdOrderBySortOrderAsc(Long courseId);
}
