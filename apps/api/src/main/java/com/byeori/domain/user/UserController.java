package com.byeori.domain.user;

import com.byeori.global.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 계정 관리. 탈퇴는 본인 토큰으로만 가능. */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    /** 계정 삭제(탈퇴). 개인 데이터 파기 + 등록 장소 익명화. */
    @DeleteMapping("/me")
    public ApiResponse<Void> deleteMe(@AuthenticationPrincipal Long userId) {
        service.deleteAccount(userId);
        return ApiResponse.ok(null);
    }

    /** 차단 요청 본문. */
    public record BlockRequest(Long userId) {}

    /** 내가 차단한 사용자 id 목록. */
    @GetMapping("/me/blocks")
    public ApiResponse<List<Long>> listBlocks(@AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.listBlocked(userId));
    }

    @PostMapping("/me/blocks")
    public ApiResponse<Void> block(@AuthenticationPrincipal Long userId, @RequestBody BlockRequest req) {
        service.block(userId, req == null ? null : req.userId());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/me/blocks/{targetUserId}")
    public ApiResponse<Void> unblock(@AuthenticationPrincipal Long userId,
                                     @PathVariable("targetUserId") Long targetUserId) {
        service.unblock(userId, targetUserId);
        return ApiResponse.ok(null);
    }
}
