package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 번역된 이름으로도 검색된다. 기존 한국어 검색은 그대로여야 한다 — 조건을 바꾸지 않고 더했다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class VenueSearchTranslationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired VenueRepository repo;
    @Autowired PerformanceRepository perfRepo;

    private static final BigDecimal LAT = new BigDecimal("37.5796");
    private static final BigDecimal LNG = new BigDecimal("126.9770");

    @BeforeEach
    void seed() {
        Venue v = Venue.fromTourApi("TRS_1", "번역검색궁", "서울 종로구", LAT, LNG, "문화", null, null);
        v.applyTranslation(Lang.EN, "Transearch Palace");
        v.applyTranslation(Lang.JA, "翻訳宮");
        v.applyTranslation(Lang.ZH, "翻译宫");
        repo.saveAndFlush(v);
    }

    private int count(String keyword) {
        return repo.search(null, null, keyword, PageRequest.of(0, 10)).getContent().size();
    }

    @Test
    @DisplayName("로마자로 찾을 수 있다")
    void 로마자() {
        assertThat(repo.search(null, null, "Transearch", PageRequest.of(0, 10)).getContent())
                .extracting(Venue::getName).containsExactly("번역검색궁");
    }

    @Test
    @DisplayName("대소문자를 가리지 않는다 — 외국인은 소문자로 친다")
    void 대소문자() {
        assertThat(count("transearch palace")).isEqualTo(1);
        assertThat(count("TRANSEARCH")).isEqualTo(1);
    }

    @Test
    @DisplayName("일본어·중국어로도 찾을 수 있다")
    void 일본어_중국어() {
        assertThat(count("翻訳宮")).isEqualTo(1);
        assertThat(count("翻译宫")).isEqualTo(1);
    }

    @Test
    @DisplayName("한국어 검색은 그대로 된다 — 회귀 방지")
    void 한국어_회귀() {
        assertThat(count("번역검색궁")).isEqualTo(1);
        assertThat(count("검색")).isEqualTo(1);
    }

    @Test
    @DisplayName("번역이 없는 장소도 한국어로는 찾힌다")
    void 번역_없는_장소() {
        repo.saveAndFlush(Venue.fromTourApi("TRS_2", "무명장소검색", "서울", LAT, LNG, "문화", null, null));
        assertThat(count("무명장소검색")).isEqualTo(1);
        // 번역 컬럼이 null 이어도 OR 조건이 터지지 않고, 엉뚱한 로마자가 걸리지도 않는다.
        assertThat(count("Transearch")).isEqualTo(1);
    }

    @Test
    @DisplayName("키워드가 없으면 거르지 않는다")
    void 키워드_없음() {
        assertThat(repo.search(null, null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Venue::getName).contains("번역검색궁");
    }

    @Test
    @DisplayName("가까운 순 검색(searchNear)도 같은 규칙이다")
    void 가까운순() {
        assertThat(repo.searchNear(null, null, "Transearch", LAT, LNG, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
        assertThat(repo.searchNear(null, null, "번역검색궁", LAT, LNG, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
        assertThat(repo.searchNear(null, null, "翻訳宮", LAT, LNG, PageRequest.of(0, 10)).getContent())
                .hasSize(1);
    }

    @Test
    @DisplayName("행사 검색도 번역된 제목을 찾고, 한국어 제목은 그대로 찾는다")
    void 행사() {
        Performance p = Performance.fromTour("TRS_P1", "번역축제검색", "축제", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), "ONGOING", null, null);
        p.applyTranslation(Lang.EN, "Transfest Festival");
        perfRepo.saveAndFlush(p);
        var page = PageRequest.of(0, 10);
        assertThat(perfRepo.search(null, LocalDate.now(), null, null, "transfest", null, page).getContent()).hasSize(1);
        assertThat(perfRepo.search(null, LocalDate.now(), null, null, "번역축제검색", null, page).getContent()).hasSize(1);
        assertThat(perfRepo.search(null, LocalDate.now(), null, null, null, null, page).getContent()).isNotEmpty();
    }
}
