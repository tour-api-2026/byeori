package com.byeori.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.byeori.domain.auth.dto.AuthDtos.LoginRequest;
import com.byeori.domain.auth.dto.AuthDtos.TokenResponse;
import com.byeori.domain.user.User;
import com.byeori.domain.user.UserRepository;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.security.JwtTokenProvider;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/** 아이디/비밀번호 로그인 — 관리자 계정과 심사용 계정의 권한 분리 검증. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceLoginTest {

    @Mock UserRepository userRepository;
    @Mock JwtTokenProvider tokenProvider;

    AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, null, null, tokenProvider);
        ReflectionTestUtils.setField(service, "adminId", "admin");
        ReflectionTestUtils.setField(service, "adminPassword", "admin-pw");
        ReflectionTestUtils.setField(service, "reviewId", "review");
        ReflectionTestUtils.setField(service, "reviewPassword", "review-pw");

        when(userRepository.findByAuthProviderAndProviderUserId(anyString(), anyString()))
                .thenReturn(Optional.empty());
        // DB가 id를 채우는 동작 재현 — 이후 generateAccess(id, role) 호출이 실제와 같아진다
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });
        when(tokenProvider.generateAccess(anyLong(), anyString())).thenReturn("access");
        when(tokenProvider.generateRefresh(anyLong(), anyString())).thenReturn("refresh");
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void 관리자_자격증명은_ADMIN_권한() {
        TokenResponse res = service.login(new LoginRequest("admin", "admin-pw"));

        assertThat(res.accessToken()).isEqualTo("access");
        User created = savedUser();
        assertThat(created.getRole()).isEqualTo("ADMIN");
        assertThat(created.getAuthProvider()).isEqualTo("ADMIN");
    }

    @Test
    void 심사용_자격증명은_USER_권한() {
        TokenResponse res = service.login(new LoginRequest("review", "review-pw"));

        assertThat(res.accessToken()).isEqualTo("access");
        User created = savedUser();
        // 심사자에게 ADMIN 권한이 넘어가면 장소 등록·삭제까지 열린다 — USER여야 함
        assertThat(created.getRole()).isEqualTo("USER");
        assertThat(created.getAuthProvider()).isEqualTo("REVIEW");
    }

    @Test
    void 심사용_아이디에_관리자_비밀번호는_거부() {
        assertThatThrownBy(() -> service.login(new LoginRequest("review", "admin-pw")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 심사용_계정_미설정이면_빈_자격증명은_거부() {
        ReflectionTestUtils.setField(service, "reviewId", "");
        ReflectionTestUtils.setField(service, "reviewPassword", "");

        assertThatThrownBy(() -> service.login(new LoginRequest("", "")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest(null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 잘못된_자격증명은_거부() {
        assertThatThrownBy(() -> service.login(new LoginRequest("admin", "wrong")))
                .isInstanceOf(BadRequestException.class);
    }
}
