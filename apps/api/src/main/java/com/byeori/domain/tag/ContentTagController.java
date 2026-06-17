package com.byeori.domain.tag;

import com.byeori.domain.tag.dto.ContentTagCountResponse;
import com.byeori.domain.tag.dto.VoteRequest;
import com.byeori.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ContentTagController {

    private static final String UID = "X-Demo-User-Id";
    private final ContentTagService service;

    public ContentTagController(ContentTagService service) {
        this.service = service;
    }

    @GetMapping("/content-tags")
    public ApiResponse<List<ContentTagCountResponse>> aggregate(
            @RequestHeader(name = UID, defaultValue = "2") Long userId,
            @RequestParam(name = "targetType") String targetType,
            @RequestParam(name = "targetId") Long targetId) {
        return ApiResponse.ok(service.aggregate(userId, targetType, targetId));
    }

    @PostMapping("/content-tag-votes")
    public ApiResponse<Void> vote(
            @RequestHeader(name = UID, defaultValue = "2") Long userId,
            @RequestBody VoteRequest req) {
        service.vote(userId, req);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/content-tag-votes")
    public ApiResponse<Void> unvote(
            @RequestHeader(name = UID, defaultValue = "2") Long userId,
            @RequestBody VoteRequest req) {
        service.unvote(userId, req);
        return ApiResponse.ok(null);
    }
}
