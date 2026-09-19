package com.byeori.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.byeori.domain.auth.dto.AuthDtos.UpdateProfileRequest;
import com.byeori.domain.auth.dto.AuthDtos.UserSummary;
import com.byeori.domain.upload.UploadedImage;
import com.byeori.domain.upload.UploadedImageRepository;
import com.byeori.domain.user.User;
import com.byeori.domain.user.UserRepository;
import com.byeori.global.exception.BadRequestException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 프로필 수정 — 닉네임 검증, 사진은 본인 업로드만, 재로그인이 고친 값을 덮어쓰지 않음. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceProfileTest {

    static final String MINE = "a".repeat(32);
    static final String OTHERS = "b".repeat(32);
    static final String KAKAO_IMG = "https://k.kakaocdn.net/profile.jpg";

    @Mock UserRepository userRepository;
    @Mock UploadedImageRepository uploadedImageRepository;

    AuthService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, uploadedImageRepository, null, null, null);
        user = User.social("KAKAO", "123", "카카오이름", "a@b.c", KAKAO_IMG);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(uploadedImageRepository.findById(MINE))
                .thenReturn(Optional.of(UploadedImage.of(MINE, "image/jpeg", new byte[1], 1L)));
        when(uploadedImageRepository.findById(OTHERS))
                .thenReturn(Optional.of(UploadedImage.of(OTHERS, "image/jpeg", new byte[1], 2L)));
    }

    @Test
    void 닉네임과_본인_업로드_사진으로_바꾼다() {
        String url = "https://byeori.ernebi.org/api/v1/uploads/images/" + MINE;

        UserSummary res = service.updateMe(1L, new UpdateProfileRequest("  벼리여행자 ", url));

        assertThat(res.name()).isEqualTo("벼리여행자");
        assertThat(res.profileImageUrl()).isEqualTo(url);
    }

    @Test
    void 지금_사진은_그대로_둘_수_있고_빈_값이면_지운다() {
        assertThat(service.updateMe(1L, new UpdateProfileRequest("새이름", KAKAO_IMG)).profileImageUrl())
                .isEqualTo(KAKAO_IMG);
        assertThat(service.updateMe(1L, new UpdateProfileRequest("새이름", "")).profileImageUrl())
                .isNull();
    }

    @Test
    void 닉네임은_2자에서_20자() {
        assertThatThrownBy(() -> service.updateMe(1L, new UpdateProfileRequest(" 가 ", null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.updateMe(1L, new UpdateProfileRequest("가".repeat(21), null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 남의_업로드나_외부_URL은_거부한다() {
        assertThatThrownBy(() -> service.updateMe(1L,
                new UpdateProfileRequest("새이름", "/api/v1/uploads/images/" + OTHERS)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.updateMe(1L,
                new UpdateProfileRequest("새이름", "https://evil.example/track.png")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 재로그인은_고친_닉네임과_사진을_덮어쓰지_않는다() {
        service.updateMe(1L, new UpdateProfileRequest("벼리여행자", null));

        user.syncFromProvider("new@b.c");

        assertThat(user.getName()).isEqualTo("벼리여행자");
        assertThat(user.getProfileImageUrl()).isNull(); // 지운 사진이 카카오 사진으로 되살아나지 않는다
        assertThat(user.getEmail()).isEqualTo("new@b.c");
    }
}
