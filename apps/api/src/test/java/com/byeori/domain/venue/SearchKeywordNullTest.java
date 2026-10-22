package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 검색어 없이 부르는 목록. **운영에서 터진 것을 고정하는 테스트다.**
 *
 * 번역 컬럼을 검색 조건에 더할 때 `lower(concat('%', :keyword, '%'))` 를 썼다. keyword 가
 * null 이면 Hibernate 가 그 자리의 타입을 정하지 못해 bytea 로 바인딩하고, Postgres 는
 * `lower(bytea)` 라는 함수가 없다며 쿼리를 거부한다. 같은 쿼리의 `p.title like ?` 는
 * like 가 타입을 text 로 고정해 주기 때문에 멀쩡했다 — 그래서 한쪽만 조용히 죽었다.
 *
 * 전통 테마 행사 탭과 '내 주변' 목록이 검색어 없이 부르는 경로라, 배포 다음 날 그 두
 * 화면이 통째로 500 이 됐다. 기존 검색 테스트는 **항상 keyword 를 넘겨서** 못 잡았다.
 *
 * 그래서 여기서 보는 것은 "검색어 없이 불러도 쿼리가 돌고, 필터가 걸리지 않는다" 다.
 * 세 쿼리 모두 같은 결함을 갖고 있었으므로 세 개를 다 건다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class SearchKeywordNullTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired VenueRepository venueRepo;
    @Autowired PerformanceRepository perfRepo;

    private static final BigDecimal LAT = new BigDecimal("37.5796");
    private static final BigDecimal LNG = new BigDecimal("126.9770");
    private static final LocalDate TODAY = LocalDate.now();

    /** 시드 데이터에도 장소·행사가 많다. 이 테스트가 넣은 것만 골라 보려고 표식을 쓴다. */
    private static final String MARK = "ZZKWNULL";

    /** 행사 목록 쿼리는 order by 를 갖지 않는다 — 부르는 쪽이 준다(PerformanceController.sortFor). */
    private static final Sort UPCOMING_SORT =
            Sort.by(Sort.Order.asc("startDate"), Sort.Order.asc("id"));

    @BeforeEach
    void seed() {
        venueRepo.saveAndFlush(
                Venue.fromTourApi("KWNULL_1", MARK + " 장소", "서울 종로구", LAT, LNG, "문화", null, null));

        // kopis_id 는 varchar(20) 이라 제목을 붙이면 넘친다. 짧게 쓴다.
        Performance p = Performance.fromKopis(
                "zzkw-1", MARK + " 행사", "국악", "https://example.test/poster.jpg",
                TODAY.plusDays(3), TODAY.plusDays(4), "UPCOMING", null);
        p.applyTraditional(true);
        perfRepo.saveAndFlush(p);
    }

    @Test
    @DisplayName("전통 테마 행사 탭: 검색어 없이 불러도 돈다")
    void 행사_목록은_검색어_없이_돈다() {
        var page = perfRepo.search("UPCOMING", TODAY, null, null, null, true,
                PageRequest.of(0, 200, UPCOMING_SORT));

        assertThat(page.getContent()).extracting(Performance::getTitle).contains(MARK + " 행사");
    }

    @Test
    @DisplayName("장소 목록: 검색어 없이 불러도 돈다")
    void 장소_목록은_검색어_없이_돈다() {
        var page = venueRepo.search(null, null, null, PageRequest.of(0, 200));

        assertThat(page.getTotalElements()).isPositive();
    }

    @Test
    @DisplayName("장소 목록(가까운 순): 검색어 없이 불러도 돈다 — '내 주변'이 쓰는 경로")
    void 장소_가까운순도_검색어_없이_돈다() {
        var page = venueRepo.searchNear(null, null, null, LAT, LNG, PageRequest.of(0, 200));

        assertThat(page.getContent()).extracting(Venue::getName).contains(MARK + " 장소");
    }

    /**
     * 검색어가 null 일 때 "아무것도 안 걸린다" 가 맞는지도 본다. 빈 문자열로 바꿔서 고치면
     * `like '%%'` 가 되어 테스트는 통과하지만, 번역이 없는(null) 행이 전부 빠진다 —
     * SQL 에서 null like '%%' 는 참이 아니라 null 이다. 그래서 건수로 확인한다.
     */
    @Test
    @DisplayName("검색어가 없으면 번역 없는 장소도 빠지지 않는다")
    void 검색어가_없으면_필터가_걸리지_않는다() {
        Venue noTranslation = venueRepo.saveAndFlush(
                Venue.fromTourApi("KWNULL_2", MARK + " 번역없음", "서울 중구", LAT, LNG, "문화", null, null));
        assertThat(noTranslation.getNameEn()).isNull();

        var page = venueRepo.search(null, null, null, PageRequest.of(0, 500));

        assertThat(page.getContent()).extracting(Venue::getName)
                .contains(MARK + " 번역없음", MARK + " 장소");
    }

    @Test
    @DisplayName("검색어를 주면 여전히 걸러진다")
    void 검색어를_주면_걸러진다() {
        var page = venueRepo.search(null, null, MARK + " 번역없음", PageRequest.of(0, 200));

        assertThat(page.getContent()).isEmpty();   // 아직 안 넣은 이름이다
    }
}
