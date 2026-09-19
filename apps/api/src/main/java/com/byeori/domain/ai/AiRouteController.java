package com.byeori.domain.ai;

import com.byeori.domain.ai.AiRouteDtos.GenerateRequest;
import com.byeori.domain.ai.AiRouteDtos.Preview;
import com.byeori.domain.ai.AiRouteDtos.Status;
import com.byeori.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 루트 생성. 생성은 로그인 필수(비용 상한을 사용자 단위로 건다), 상태 조회는 공개. */
@RestController
@RequestMapping("/api/v1/ai/routes")
@RequiredArgsConstructor
public class AiRouteController {

    private final AiRouteService service;

    @GetMapping("/status")
    public ApiResponse<Status> status(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.status(userId));
    }

    /** 미리보기만 만든다. 저장은 POST /itineraries (sourceType=AI, items 포함). */
    @PostMapping
    public ApiResponse<Preview> generate(@AuthenticationPrincipal Long userId, @RequestBody GenerateRequest req) {
        return ApiResponse.ok(service.generate(userId, req));
    }
}
