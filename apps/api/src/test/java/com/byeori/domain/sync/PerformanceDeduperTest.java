package com.byeori.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
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
 * 출처가 다른 같은 행사를 한 줄만 보이게 한다.
 *
 * 한 번 치우는 일이 아니라 동기화마다 돌아야 하는 일이라, 여기서 확인하는 건 두 가지다 —
 * 같은 행사를 가리는가, 그리고 **가린 것을 되돌릴 수 있는가**(제목이 갈라지면 저절로
 * 풀리는가). 판정 규칙은 언젠가 틀리므로 되돌아오는 쪽이 더 중요하다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class PerformanceDeduperTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired PerformanceDeduper deduper;
    @Autowired PerformanceRepository repo;
    @Autowired EntityManager em;

    private static final LocalDate TODAY = LocalDate.now();
    private static final String START = TODAY.plusDays(10).toString();
    private static final String END = TODAY.plusDays(11).toString();

    @BeforeEach
    void seed() {
        em.createNativeQuery("delete from performances where title like 'TEST_%'").executeUpdate();
        // 사용자가 화면에서 나란히 본 그 쌍(KOPIS 6384 / SEOUL 10448)을 날짜만 옮겨 재현한다.
        insert("TEST_제4회 대한민국 국악 관현악 축제, 강원특별자치도립 국악 관현악단", "KOPIS", START, END, null);
        insert("TEST_[세종문화회관] 2026 대한민국국악관현악축제 [강원특별자치도립국악관현악단]", "SEOUL", START, END, null);
        // 운영 데이터에서 위 중복과 같은 날짜 묶음에 들어 있던 다른 행사.
        // 공통 토막이 "국악관현악" 5자뿐이라 남아야 한다.
        insert("TEST_청사국악관현악단과 함께 하는 협연의 밤", "KOPIS", START, END, null);
        // 기간이 다르면 비교 대상이 아니다 — 해마다 같은 이름으로 열리는 축제를 지키는 선이다.
        insert("TEST_대한민국국악관현악축제", "SEOUL", TODAY.plusDays(400).toString(),
                TODAY.plusDays(401).toString(), null);
        em.flush();
        em.clear();
    }

    private void insert(String title, String source, String start, String end, Long venueId) {
        em.createNativeQuery("insert into performances (title, source, start_date, end_date, state, venue_id) "
                        + "values (:t, :src, cast(:s as date), cast(:e as date), 'UPCOMING', :v)")
                .setParameter("t", title).setParameter("src", source)
                .setParameter("s", start).setParameter("e", end).setParameter("v", venueId)
                .executeUpdate();
    }

    private List<String> visible() {
        return repo.search(null, TODAY, null, null, "TEST_", null, PageRequest.of(0, 50))
                .stream().map(Performance::getTitle).toList();
    }

    private Performance byPrefix(String fragment) {
        return repo.findDedupeCandidates(TODAY).stream()
                .filter(p -> p.getTitle() != null && p.getTitle().startsWith("TEST_") && p.getTitle().contains(fragment))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("출처가 다른 같은 행사는 한 줄만 보인다")
    void 중복_숨김() {
        deduper.sweep();
        em.flush();
        em.clear();

        List<String> titles = visible();
        assertThat(titles).anyMatch(t -> t.contains("제4회 대한민국 국악 관현악 축제"));
        assertThat(titles).noneMatch(t -> t.contains("세종문화회관"));
    }

    @Test
    @DisplayName("예매처·장소가 붙은 KOPIS 쪽을 남긴다")
    void 남길_쪽() {
        deduper.sweep();
        em.flush();
        em.clear();

        assertThat(byPrefix("세종문화회관").getDuplicateOf())
                .isEqualTo(byPrefix("제4회").getId());
        assertThat(byPrefix("제4회").getDuplicateOf()).isNull();
    }

    @Test
    @DisplayName("장소가 이어진 줄이 있으면 출처보다 그쪽을 남긴다 — 지도·루트에 쓸 수 있다")
    void 장소_우선() {
        em.createNativeQuery("update performances set venue_id = "
                        + "(select id from venues order by id asc limit 1) where title like 'TEST_[세종%'")
                .executeUpdate();
        em.flush();
        em.clear();

        deduper.sweep();
        em.flush();
        em.clear();

        assertThat(byPrefix("세종문화회관").getDuplicateOf()).isNull();
        assertThat(byPrefix("제4회").getDuplicateOf()).isEqualTo(byPrefix("세종문화회관").getId());
    }

    @Test
    @DisplayName("제목이 비슷하기만 한 다른 행사는 그대로 보인다")
    void 다른_행사는_남긴다() {
        deduper.sweep();
        em.flush();
        em.clear();

        assertThat(visible()).anyMatch(t -> t.contains("청사국악관현악단"));
    }

    @Test
    @DisplayName("기간이 다르면 제목이 같아도 건드리지 않는다 — 내년 같은 축제가 사라지면 안 된다")
    void 기간이_다르면_비교하지_않는다() {
        deduper.sweep();
        em.flush();
        em.clear();

        assertThat(byPrefix("TEST_대한민국국악관현악축제").getDuplicateOf()).isNull();
    }

    @Test
    @DisplayName("제목이 갈라지면 숨김이 저절로 풀린다 — 판정이 틀렸을 때 되돌아오는 길이다")
    void 숨김_해제() {
        deduper.sweep();
        em.flush();
        em.clear();
        assertThat(byPrefix("세종문화회관").getDuplicateOf()).isNotNull();

        em.createNativeQuery("update performances set title = 'TEST_[세종문화회관] 전혀 다른 공연' "
                + "where title like 'TEST_[세종%'").executeUpdate();
        em.flush();
        em.clear();

        PerformanceDeduper.Summary summary = deduper.sweep();
        em.flush();
        em.clear();

        assertThat(summary.released()).isEqualTo(1);
        assertThat(byPrefix("세종문화회관").getDuplicateOf()).isNull();
        assertThat(visible()).anyMatch(t -> t.contains("세종문화회관"));
    }

    @Test
    @DisplayName("두 번 쓸어도 결과가 같다 — 매일 도는 일이라 같은 답이 나와야 한다")
    void 반복해도_같다() {
        PerformanceDeduper.Summary first = deduper.sweep();
        em.flush();
        em.clear();
        PerformanceDeduper.Summary second = deduper.sweep();

        assertThat(second.hidden()).isEqualTo(first.hidden());
        assertThat(second.added()).isZero();
        assertThat(second.released()).isZero();
    }
}
