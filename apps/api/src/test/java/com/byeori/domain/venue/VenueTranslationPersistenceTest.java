package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.global.i18n.Lang;
import jakarta.persistence.EntityManager;
import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.PerformanceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 번역 컬럼이 실제 DB 를 오간다. Hibernate 이름 짓기가 컬럼과 어긋나면(distanceM 이
 * distancem 이 된 적이 있다) 컴파일은 되어도 런타임에 터지므로 저장하고 다시 읽어 본다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class VenueTranslationPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired VenueRepository repo;
    @Autowired PerformanceRepository perfRepo;
    @Autowired EntityManager em;

    @Test
    @DisplayName("번역 컬럼이 실제로 저장되고 다시 읽힌다")
    void 저장되고_읽힌다() {
        Venue v = Venue.fromTourApi("TR_TEST_1", "경복궁", "서울", new BigDecimal("37.5796"),
                new BigDecimal("126.9770"), "문화", null, null);
        v.applyTranslation(Lang.EN, "Gyeongbokgung Palace");
        v.applyTranslation(Lang.JA, "景福宮");
        v.applyTranslation(Lang.ZH, "景福宫");
        // 실제 sha256 hex 와 같은 길이(64)의 값으로 계약을 보여 준다.
        String hash = "a".repeat(64);
        v.markHash(hash);

        Long id = repo.saveAndFlush(v).getId();
        // 영속성 컨텍스트를 비워야 메모리 객체가 아니라 DB 에서 다시 읽는다.
        em.clear();

        Venue read = repo.findById(id).orElseThrow();
        assertThat(read).isNotSameAs(v);
        assertThat(read.getNameEn()).isEqualTo("Gyeongbokgung Palace");
        assertThat(read.getNameJa()).isEqualTo("景福宮");
        assertThat(read.getNameZh()).isEqualTo("景福宫");
        assertThat(read.getNameHash()).isEqualTo(hash);
        assertThat(read.getTrSource()).isEqualTo("AI");
        assertThat(read.nameIn(Lang.JA)).isEqualTo("景福宮");
        assertThat(read.nameIn(Lang.KO)).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("행사 번역 컬럼도 엔티티로 저장되고 다시 읽힌다")
    void 행사_컬럼() {
        Performance p = Performance.fromKopis("TR_PF_1", "공연", "연극", null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "UPCOMING", null);
        p.applyTranslation(Lang.EN, "Show");
        p.applyTranslation(Lang.JA, "ショー");
        p.applyTranslation(Lang.ZH, "演出");
        String hash = "b".repeat(64);
        p.markHash(hash);

        Long id = perfRepo.saveAndFlush(p).getId();
        em.clear();

        Performance read = perfRepo.findById(id).orElseThrow();
        assertThat(read).isNotSameAs(p);
        assertThat(read.getTitleEn()).isEqualTo("Show");
        assertThat(read.getTitleJa()).isEqualTo("ショー");
        assertThat(read.getTitleZh()).isEqualTo("演出");
        assertThat(read.getNameHash()).isEqualTo(hash);
        assertThat(read.getTrSource()).isEqualTo("AI");
        assertThat(read.titleIn(Lang.ZH)).isEqualTo("演出");
        assertThat(read.titleIn(Lang.KO)).isEqualTo("공연");
    }
}
