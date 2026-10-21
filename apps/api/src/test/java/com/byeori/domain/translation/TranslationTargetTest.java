package com.byeori.domain.translation;

import static org.assertj.core.api.Assertions.assertThat;
import com.byeori.domain.venue.*;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 번역 대상 고르기. 이 조건이 뒤집히면 매일 전량을 세 언어로 다시 번역해 비용이 수백 배가 된다.
 * digest() 는 pgcrypto 라 H2 로는 볼 수 없어 실제 Postgres 로 확인한다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class TranslationTargetTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    // 개발 DB 시드와 같은 이름(경복궁 등)이 이미 있을 수 있어 이름이 아니라 id 로 비교한다.
    @Autowired VenueRepository repo;

    private static int seq = 0;

    private Venue saved(String name) {
        return repo.saveAndFlush(Venue.fromTourApi("TT_" + (seq++), name, "서울", new BigDecimal("37.5"), new BigDecimal("127.0"), "문화", null, null));
    }

    @Test
    @DisplayName("번역이 없는 행은 대상이다")
    void 번역_없음() {
        Venue v0 = saved("무명장소");
        assertThat(repo.findNeedingTranslation("EN", 1_000_000, TranslationBatch.MAX_ATTEMPTS))
                .extracting(Venue::getId).contains(v0.getId());
    }

    @Test
    @DisplayName("번역을 마치고 해시를 찍은 행은 **대상이 아니다** — 이게 뒤집히면 매일 전량을 다시 번역한다")
    void 끝난_행은_제외() {
        Venue v = saved("경복궁");
        v.applyTranslation(Lang.EN, "Gyeongbokgung Palace");
        v.markHash(TranslationBatch.hashOf("경복궁"));
        repo.saveAndFlush(v);

        assertThat(repo.findNeedingTranslation("EN", 1_000_000, TranslationBatch.MAX_ATTEMPTS))
                .extracting(Venue::getId).doesNotContain(v.getId());
    }

    @Test
    @DisplayName("이름이 바뀌면 다시 대상이 된다")
    void 이름이_바뀌면() {
        Venue v = saved("경복궁");
        v.applyTranslation(Lang.EN, "Gyeongbokgung Palace");
        v.markHash(TranslationBatch.hashOf("옛이름"));   // 이름이 바뀐 상황을 흉내 낸다
        repo.saveAndFlush(v);

        assertThat(repo.findNeedingTranslation("EN", 1_000_000, TranslationBatch.MAX_ATTEMPTS))
                .extracting(Venue::getId).contains(v.getId());
    }

    @Test
    @DisplayName("영어만 끝낸 행은 일본어 대상으로는 남는다")
    void 언어별로_따로() {
        Venue v = saved("북촌한옥마을");
        v.applyTranslation(Lang.EN, "Bukchon Hanok Village");
        v.markHash(TranslationBatch.hashOf("북촌한옥마을"));
        repo.saveAndFlush(v);

        assertThat(repo.findNeedingTranslation("EN", 1_000_000, TranslationBatch.MAX_ATTEMPTS))
                .extracting(Venue::getId).doesNotContain(v.getId());
        assertThat(repo.findNeedingTranslation("JA", 1_000_000, TranslationBatch.MAX_ATTEMPTS))
                .extracting(Venue::getId).contains(v.getId());
    }
}
