package com.byeori.domain.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.domain.performance.dto.PerformanceResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 행사 목록의 순서.
 *
 * '예정' 탭이 가장 먼 미래부터 나왔다. 쿼리에 `order by p.startDate desc` 가 박혀 있었고,
 * 컨트롤러는 오름차순을 넘기고 있었는데 JPQL 의 order by 가 이겼다. 정렬 기준이 두 곳에
 * 있으면 이렇게 조용히 어긋나므로, 이제 부르는 쪽에만 둔다.
 *
 * 이 테스트는 그 순서를 고정한다. 날짜 정렬이 다시 뒤집히면 여기서 걸린다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class PerformanceListOrderTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired PerformanceRepository repo;
    @Autowired PerformanceService service;

    private static final LocalDate TODAY = LocalDate.now();

    /** 컨트롤러가 쓰는 정렬과 같은 것. 컨트롤러의 private 메서드를 여기서 되풀이한다. */
    private static Sort sortFor(String state) {
        if ("ENDED".equals(state)) return Sort.by(Sort.Order.desc("endDate"), Sort.Order.asc("id"));
        if ("ONGOING".equals(state)) return Sort.by(Sort.Order.asc("endDate"), Sort.Order.asc("id"));
        return Sort.by(Sort.Order.asc("startDate"), Sort.Order.asc("id"));
    }

    /**
     * state 컬럼은 not-null 이라 값을 준다. 그런데 목록의 상태 판정은 이 컬럼을 보지 않고
     * 날짜로 따진다 — 저장된 state 는 동기화 시점 값이라 시간이 지나면 틀어지기 때문이다
     * (Performance.stateOn 주석 참고). 그래서 일부러 **전부 "UPCOMING"** 으로 넣는다.
     * 쿼리가 몰래 이 컬럼을 보기 시작하면 '진행중'·'종료' 테스트가 빈 목록을 받아 실패한다.
     */
    private void given(String title, LocalDate start, LocalDate end) {
        // kopis_id 는 varchar(20) 이라 제목을 붙이면 넘친다. 짧고 겹치지 않는 값을 쓴다.
        Performance p = Performance.fromKopis(
                "zzot-" + (seq++), MARK + " " + title,
                "국악", "https://example.test/poster.jpg",
                start, end, "UPCOMING", null);
        p.applyTraditional(true);
        repo.saveAndFlush(p);
    }

    /**
     * 이 테스트가 넣은 행만 골라 본다.
     *
     * 시드 데이터에도 전통 행사가 있고, 그것들을 지울 수도 없다 — curated_course_items 가
     * 참조하고 있어 deleteAll 이 외래키 제약에 걸린다. 그래서 지우는 대신 제목에 표식을
     * 달고 키워드로 거른다. 남의 데이터를 건드리지 않는 쪽이 테스트끼리도 안전하다.
     */
    private static final String MARK = "ZZORDERTEST";

    /** kopis_id 를 겹치지 않게 만드는 일련번호. 테스트마다 새로 시작한다. */
    private int seq = 0;

    private List<String> titles(String state) {
        return service.list(state, null, null, MARK, true, PageRequest.of(0, 20, sortFor(state)))
                .getContent().stream()
                .map(p -> p.title().replace(MARK + " ", ""))
                .toList();
    }

    @BeforeEach
    void seed() {
        // 예정 — 일부러 먼 것을 먼저 넣는다. 저장 순서가 결과를 좌우하면 안 된다.
        given("두 달 뒤", TODAY.plusDays(60), TODAY.plusDays(61));
        given("내일", TODAY.plusDays(1), TODAY.plusDays(2));
        given("일주일 뒤", TODAY.plusDays(7), TODAY.plusDays(8));
        // 진행중 — 끝나는 날이 다르다
        given("오래 하는 중", TODAY.minusDays(3), TODAY.plusDays(30));
        given("곧 끝나는 중", TODAY.minusDays(3), TODAY.plusDays(1));
        // 종료
        given("작년에 끝남", TODAY.minusDays(400), TODAY.minusDays(399));
        given("지난주에 끝남", TODAY.minusDays(7), TODAY.minusDays(6));
    }

    @Test
    @DisplayName("예정은 오늘과 가까운 것부터 나온다")
    void 예정은_가까운_것부터() {
        assertThat(titles("UPCOMING")).containsExactly("내일", "일주일 뒤", "두 달 뒤");
    }

    @Test
    @DisplayName("진행중은 곧 끝나는 것부터 나온다")
    void 진행중은_곧_끝나는_것부터() {
        assertThat(titles("ONGOING")).containsExactly("곧 끝나는 중", "오래 하는 중");
    }

    @Test
    @DisplayName("종료는 최근에 끝난 것부터 나온다")
    void 종료는_최근부터() {
        assertThat(titles("ENDED")).containsExactly("지난주에 끝남", "작년에 끝남");
    }

    @Test
    @DisplayName("예정 목록에 진행중·종료가 섞이지 않는다")
    void 예정에는_예정만() {
        assertThat(titles("UPCOMING"))
                .doesNotContain("곧 끝나는 중", "오래 하는 중", "지난주에 끝남", "작년에 끝남");
    }

    /**
     * 같은 날짜가 여러 건일 때 순서가 요청마다 흔들리면, 페이지를 넘길 때 어떤 행사는 두 번
     * 보이고 어떤 행사는 영영 안 보인다. id 를 마지막 기준으로 두는 이유다.
     */
    @Test
    @DisplayName("같은 날짜끼리도 순서가 매번 같다")
    void 동점도_순서가_같다() {
        for (int i = 1; i <= 5; i++) given("같은날 " + i, TODAY.plusDays(10), TODAY.plusDays(11));

        List<String> first = titles("UPCOMING");
        assertThat(titles("UPCOMING")).isEqualTo(first);
        assertThat(titles("UPCOMING")).isEqualTo(first);
        assertThat(first).contains("같은날 1", "같은날 2", "같은날 3", "같은날 4", "같은날 5");
    }
}
