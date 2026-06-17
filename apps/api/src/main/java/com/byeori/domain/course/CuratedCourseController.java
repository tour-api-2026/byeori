package com.byeori.domain.course;

import com.byeori.domain.course.dto.CuratedCourseDetailResponse;
import com.byeori.domain.course.dto.CuratedCourseResponse;
import com.byeori.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/curated-courses")
public class CuratedCourseController {

    private final CuratedCourseService service;

    public CuratedCourseController(CuratedCourseService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<CuratedCourseResponse>> list(
            @RequestParam(name = "theme", required = false) String theme) {
        return ApiResponse.ok(service.list(theme));
    }

    @GetMapping("/{id}")
    public ApiResponse<CuratedCourseDetailResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(service.detail(id));
    }
}
