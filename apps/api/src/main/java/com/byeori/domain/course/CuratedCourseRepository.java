package com.byeori.domain.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuratedCourseRepository extends JpaRepository<CuratedCourse, Long> {

    /** 같은 마이그레이션으로 들어간 코스는 생성 시각이 같아 순서가 흔들린다. id 로 동점을 깬다. */
    @Query("""
            select c from CuratedCourse c
            where c.status = 'ACTIVE'
              and (:theme is null or c.theme = :theme)
            order by c.createdAt desc, c.id asc
            """)
    List<CuratedCourse> findActive(@Param("theme") String theme);
}
