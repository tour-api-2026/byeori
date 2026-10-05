package com.byeori.domain.performance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
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
 * 장소 상세의 행사 목록은 진행 중·예정만 보여준다.
 *
 * 끝난 행사는 지우지 않고 DB 에 그대로 둔다 — 다른 화면·통계에서 쓸 수 있고,
 * 안 보여줄 뿐이다. 보여주지 않을 행을 응답에 담지 않도록 질의에서 거른다
 * (한 장소에 과거 공연이 수백 건 쌓일 수 있다).
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class VenuePerformanceListTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired PerformanceRepository repo;
    @Autowired EntityManager em;

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private Long venueId;

    @BeforeEach
    void seed() {
        venueId = ((Number) em.createNativeQuery(
                "insert into venues (name, address, lat, lng, category) "
                        + "values ('TEST_장소', '서울 종로구', 37.5, 127.0, '문화') returning id")
                .getSingleResult()).longValue();
        record Row(String title, String start, String end) {}
        List.of(
                new Row("TEST_작년에 끝남", "2026-05-01", "2026-08-31"),
                new Row("TEST_어제 끝남", "2026-09-01", "2026-10-04"),
                new Row("TEST_오늘 끝남", "2026-01-01", "2026-10-05"),
                new Row("TEST_진행 중", "2026-10-01", "2026-10-31"),
                new Row("TEST_예정", "2026-12-01", "2026-12-05")
        ).forEach(r -> em.createNativeQuery(
                        "insert into performances (venue_id, title, start_date, end_date, state, source) "
                                + "values (:v, :t, cast(:s as date), cast(:e as date), 'ONGOING', 'MANUAL')")
                .setParameter("v", venueId).setParameter("t", r.title())
                .setParameter("s", r.start()).setParameter("e", r.end())
                .executeUpdate());
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("끝난 행사는 빠지고 진행 중·예정만 남는다")
    void 끝난_행사_제외() {
        List<String> titles = repo.findVisibleByVenue(venueId, TODAY, PageRequest.of(0, 50)).stream()
                .map(Performance::getTitle).toList();
        assertThat(titles).containsExactly("TEST_오늘 끝남", "TEST_진행 중", "TEST_예정");
        assertThat(titles).doesNotContain("TEST_작년에 끝남", "TEST_어제 끝남");
    }

    @Test
    @DisplayName("오늘 끝나는 행사는 아직 보여준다 — 종료일 당일은 진행 중")
    void 종료일_당일은_포함() {
        assertThat(repo.findVisibleByVenue(venueId, TODAY, PageRequest.of(0, 50)).stream().map(Performance::getTitle))
                .contains("TEST_오늘 끝남");
    }

    @Test
    @DisplayName("DB 에는 끝난 행사가 그대로 남아 있다 — 지우는 게 아니라 거르는 것")
    void DB_에는_남는다() {
        Long total = (Long) em.createQuery(
                        "select count(p) from Performance p where p.venueId = :v", Long.class)
                .setParameter("v", venueId).getSingleResult();
        assertThat(total).isEqualTo(5);
    }
}
