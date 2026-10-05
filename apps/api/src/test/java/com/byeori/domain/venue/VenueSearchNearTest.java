package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 키워드 검색을 현재 위치에서 가까운 순으로 준다.
 *
 * 정렬을 클라이언트에 맡기면 안 된다. '카페'는 766건인데 응답은 50건으로 잘리므로,
 * 받은 50건 안에서 정렬해 봐야 진짜 가까운 곳은 이미 빠져 있다. 서버가 거리로 고르고
 * 거리로 정렬해야 한다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class VenueSearchNearTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired VenueRepository repo;
    @Autowired EntityManager em;

    // 서울시청 근처를 기준점으로 둔다.
    private static final BigDecimal ME_LAT = new BigDecimal("37.5663");
    private static final BigDecimal ME_LNG = new BigDecimal("126.9779");

    @BeforeEach
    void seed() {
        em.createNativeQuery("delete from venues where name like 'TEST_%'").executeUpdate();
        record Spot(String name, String lat, String lng) {}
        List.of(
                new Spot("TEST_카페 가까움", "37.5670", "126.9780"),   // 약 80m
                new Spot("TEST_카페 보통",   "37.5750", "126.9800"),   // 약 1km
                new Spot("TEST_카페 멀리",   "37.6500", "127.0500"),   // 약 10km
                new Spot("TEST_카페 제주",   "33.4996", "126.5312")    // 약 450km
        ).forEach(s -> em.createNativeQuery(
                        "insert into venues (name, address, lat, lng, category, status, visibility) "
                                + "values (:n, '주소', cast(:la as numeric), cast(:lo as numeric), '카페', 'ACTIVE', 'PUBLIC')")
                .setParameter("n", s.name()).setParameter("la", s.lat()).setParameter("lo", s.lng())
                .executeUpdate());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("가까운 순으로 정렬된다")
    void 거리순() {
        List<String> names = repo.searchNear(null, null, "TEST_카페", ME_LAT, ME_LNG, PageRequest.of(0, 10))
                .getContent().stream().map(Venue::getName).toList();
        assertThat(names).containsExactly(
                "TEST_카페 가까움", "TEST_카페 보통", "TEST_카페 멀리", "TEST_카페 제주");
    }

    @Test
    @DisplayName("잘라도 가까운 쪽이 남는다 — 이 정렬이 서버에 있어야 하는 이유")
    void 잘라도_가까운_것부터() {
        List<String> names = repo.searchNear(null, null, "TEST_카페", ME_LAT, ME_LNG, PageRequest.of(0, 2))
                .getContent().stream().map(Venue::getName).toList();
        assertThat(names).containsExactly("TEST_카페 가까움", "TEST_카페 보통");
    }

    @Test
    @DisplayName("카테고리 조건은 그대로 걸린다")
    void 카테고리_병행() {
        assertThat(repo.searchNear("맛집", null, "TEST_카페", ME_LAT, ME_LNG, PageRequest.of(0, 10)))
                .isEmpty();
        assertThat(repo.searchNear("카페", null, "TEST_카페", ME_LAT, ME_LNG, PageRequest.of(0, 10)))
                .hasSize(4);
    }
}
