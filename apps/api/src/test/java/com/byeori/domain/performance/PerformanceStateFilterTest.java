package com.byeori.domain.performance;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * state 필터는 저장된 컬럼이 아니라 기준일로 따져야 한다.
 *
 * 필터 SQL 과 Performance.stateOn 이 각각 구현돼 있어, 어긋나면 목록에 뜨는 것과
 * 카드에 찍히는 배지가 따로 논다. 같은 데이터로 둘을 대조한다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class PerformanceStateFilterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired PerformanceRepository repo;
    @Autowired EntityManager em;

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    /** 끝난 것·오늘 걸친 것·앞으로 올 것·경계값을 섞는다. */
    private static final Map<String, String[]> FIXTURES = Map.of(
            "작년에 끝남",      new String[]{"2026-08-07", "2026-08-07"},
            "어제 끝남",        new String[]{"2026-09-01", "2026-10-04"},
            "오늘 시작",        new String[]{"2026-10-05", "2026-12-01"},
            "오늘 끝남",        new String[]{"2026-01-01", "2026-10-05"},
            "오늘 하루짜리",     new String[]{"2026-10-05", "2026-10-05"},
            "기간 중",          new String[]{"2026-10-01", "2026-10-31"},
            "내일 시작",        new String[]{"2026-10-06", "2026-10-10"},
            "연말 예정",        new String[]{"2026-12-26", "2026-12-26"}
    );

    @BeforeEach
    void seed() {
        em.createNativeQuery("delete from performances where title like 'TEST_%'").executeUpdate();
        FIXTURES.forEach((label, d) -> em.createNativeQuery(
                        "insert into performances (title, start_date, end_date, state, source, traditional) "
                                + "values (:t, cast(:s as date), cast(:e as date), 'ONGOING', 'MANUAL', true)")
                .setParameter("t", "TEST_" + label)
                .setParameter("s", d[0])
                .setParameter("e", d[1])
                .executeUpdate());
        em.flush();
        em.clear();
    }

    private List<String> titlesFor(String state) {
        return repo.search(state, TODAY, null, null, "TEST_", true, PageRequest.of(0, 50))
                .getContent().stream().map(Performance::getTitle).sorted().toList();
    }

    private List<String> expectedFor(String state) {
        return FIXTURES.entrySet().stream()
                .filter(e -> Performance.stateOn(
                        LocalDate.parse(e.getValue()[0]), LocalDate.parse(e.getValue()[1]), TODAY).equals(state))
                .map(e -> "TEST_" + e.getKey())
                .sorted().toList();
    }

    @Test
    @DisplayName("필터 결과가 stateOn 판정과 정확히 일치한다")
    void 필터와_판정이_일치() {
        for (String state : List.of("ONGOING", "UPCOMING", "ENDED")) {
            assertThat(titlesFor(state))
                    .as("state=%s", state)
                    .containsExactlyElementsOf(expectedFor(state));
        }
    }

    @Test
    @DisplayName("저장된 state 가 전부 ONGOING 이어도 끝난 행사는 '진행 중'에 안 뜬다")
    void 저장값에_속지_않는다() {
        // 픽스처는 전부 state='ONGOING' 으로 넣었다 — 이번 버그와 같은 상태
        assertThat(titlesFor("ONGOING"))
                .doesNotContain("TEST_작년에 끝남", "TEST_어제 끝남", "TEST_내일 시작");
        assertThat(titlesFor("ENDED"))
                .containsExactlyInAnyOrder("TEST_작년에 끝남", "TEST_어제 끝남");
    }

    @Test
    @DisplayName("세 상태를 합치면 전체와 같다 — 빠지거나 겹치는 게 없다")
    void 분할이_완전하다() {
        var all = repo.search(null, TODAY, null, null, "TEST_", true, PageRequest.of(0, 50))
                .getContent().stream().map(Performance::getTitle).collect(Collectors.toSet());
        var union = List.of("ONGOING", "UPCOMING", "ENDED").stream()
                .flatMap(s -> titlesFor(s).stream()).collect(Collectors.toSet());
        assertThat(union).isEqualTo(all);
        assertThat(union).hasSize(FIXTURES.size());
    }
}
