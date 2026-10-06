package com.byeori.domain.performance;

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
 * 좌표로 주변 행사를 찾는다.
 *
 * 공연시설명 매칭으로 이은 건 1,659건인데, 이름이 우리 DB에 없어 못 이은 행사가 1,419건
 * 남는다(소극장·사설 공연장 등). 그쪽도 좌표는 있으므로 "여기서 열린다" 대신
 * "이 근처에서 열린다"로는 보여줄 수 있다 — 그건 거리만 맞으면 틀릴 수 없는 말이다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class NearbyPerformanceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired PerformanceRepository repo;
    @Autowired EntityManager em;

    private static final BigDecimal LAT = new BigDecimal("37.5663");   // 서울시청
    private static final BigDecimal LNG = new BigDecimal("126.9779");

    @BeforeEach
    void seed() {
        em.createNativeQuery("delete from performances where title like 'TEST_%'").executeUpdate();
        record Row(String title, String lat, String lng, String start, String end) {}
        List.of(
                new Row("TEST_바로옆",   "37.5670", "126.9780", "2026-10-01", "2026-12-31"),  // 약 80m
                new Row("TEST_1km",     "37.5750", "126.9800", "2026-10-01", "2026-12-31"),
                new Row("TEST_3km",     "37.5930", "126.9900", "2026-10-01", "2026-12-31"),
                new Row("TEST_먼곳",     "37.6500", "127.0500", "2026-10-01", "2026-12-31"),  // 약 10km
                new Row("TEST_끝난행사", "37.5665", "126.9781", "2026-05-01", "2026-08-31"),  // 가깝지만 종료
                new Row("TEST_좌표없음", null,      null,       "2026-10-01", "2026-12-31")
        ).forEach(r -> em.createNativeQuery(
                        "insert into performances (title, lat, lng, start_date, end_date, state, source) "
                                + "values (:t, cast(:la as numeric), cast(:lo as numeric), "
                                + "cast(:s as date), cast(:e as date), 'ONGOING', 'MANUAL')")
                .setParameter("t", r.title()).setParameter("la", r.lat()).setParameter("lo", r.lng())
                .setParameter("s", r.start()).setParameter("e", r.end())
                .executeUpdate());
        em.flush();
        em.clear();
    }

    private List<String> near(double radiusKm) {
        BigDecimal d = BigDecimal.valueOf(radiusKm / 111.0);   // 위도 1도 ≈ 111km
        return repo.findNearbyOngoing(LAT, LNG, LAT.subtract(d), LAT.add(d),
                        LNG.subtract(d), LNG.add(d), PageRequest.of(0, 20))
                .stream().map(Performance::getTitle).toList();
    }

    @Test
    @DisplayName("가까운 순으로 준다")
    void 거리순() {
        assertThat(near(20)).containsExactly("TEST_바로옆", "TEST_1km", "TEST_3km", "TEST_먼곳");
    }

    @Test
    @DisplayName("끝난 행사는 빼고 준다 — '지금 근처에서 열리는' 것만 의미가 있다")
    void 끝난_행사_제외() {
        assertThat(near(20)).doesNotContain("TEST_끝난행사");
    }

    @Test
    @DisplayName("좌표 없는 행사는 애초에 대상이 아니다")
    void 좌표_없음_제외() {
        assertThat(near(20)).doesNotContain("TEST_좌표없음");
    }

    @Test
    @DisplayName("반경 밖은 들어오지 않는다")
    void 반경_제한() {
        assertThat(near(2)).containsExactly("TEST_바로옆", "TEST_1km");
    }
}
