package com.byeori.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 사용자 차단 — 구글 UGC 정책이 요구하는 차단 수단. */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class UserBlockTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired UserService userService;
    @Autowired EntityManager em;

    private Long me;
    private Long other;

    private Long newUser(String name) {
        return ((Number) em.createNativeQuery(
                "insert into users (name, auth_provider) values ('" + name + "', 'KAKAO') returning id")
                .getSingleResult()).longValue();
    }

    @BeforeEach
    void setUp() {
        me = newUser("차단하는사람");
        other = newUser("차단당하는사람");
        em.flush();
    }

    @Test
    void 차단하면_목록에_남는다() {
        userService.block(me, other);

        assertThat(userService.listBlocked(me)).containsExactly(other);
    }

    @Test
    void 같은_사용자를_두_번_차단해도_한_건만_남는다() {
        userService.block(me, other);
        userService.block(me, other);

        assertThat(userService.listBlocked(me)).containsExactly(other);
    }

    @Test
    void 자기_자신은_차단할_수_없다() {
        assertThatThrownBy(() -> userService.block(me, me))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 없는_사용자는_차단할_수_없다() {
        assertThatThrownBy(() -> userService.block(me, 99999999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 차단_해제하면_목록에서_사라진다() {
        userService.block(me, other);
        userService.unblock(me, other);

        assertThat(userService.listBlocked(me)).isEmpty();
    }

    @Test
    void 차단은_단방향이라_상대의_목록은_비어_있다() {
        userService.block(me, other);

        assertThat(userService.listBlocked(other)).isEmpty();
    }
}
