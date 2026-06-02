package com.byeori.domain.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuratedCourseRepository extends JpaRepository<CuratedCourse, Long> {

    @Query("""
            select c from CuratedCourse c
            where c.status = 'ACTIVE'
              and (:theme is null or c.theme = :theme)
            order by c.createdAt desc
            """)
    List<CuratedCourse> findActive(@Param("theme") String theme);
}
