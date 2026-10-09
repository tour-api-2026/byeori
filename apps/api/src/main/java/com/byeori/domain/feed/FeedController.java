package com.byeori.domain.feed;

import com.byeori.global.response.ApiResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * '당신을 위한 추천'.
 *
 * 기존 목록 API 는 그대로 둔다. 추천이 이상할 때 예전 화면으로 되돌릴 길이 있어야 한다.
 * 로그인해야 부를 수 있다 — '당신을 위한'인데 당신이 없으면 의미가 없다.
 */
@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private static final int MAX_SIZE = 50;

    private final FeedService service;

    public FeedController(FeedService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<FeedItem>> feed(@AuthenticationPrincipal Long userId,
                                            @RequestParam(name = "lat", required = false) Double lat,
                                            @RequestParam(name = "lng", required = false) Double lng,
                                            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(service.forUser(userId, lat, lng, Math.min(Math.max(size, 1), MAX_SIZE)));
    }
}
