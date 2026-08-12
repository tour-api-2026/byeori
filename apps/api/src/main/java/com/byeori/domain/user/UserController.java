package com.byeori.domain.user;

import com.byeori.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
}
