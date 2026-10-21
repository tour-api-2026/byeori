package com.byeori.domain.translation;

import static org.assertj.core.api.Assertions.assertThat;
import com.byeori.domain.performance.*;
import com.byeori.domain.venue.*;
import com.byeori.global.external.OpenAiClient;
import com.byeori.global.i18n.Lang;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 배치 전체 흐름: 부분 실패가 데이터를 오염시키지 않고, 끝난 행은 AI 를 다시 부르지 않는다. */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class TranslationBatchFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired VenueRepository venueRepo;
    @Autowired PerformanceRepository perfRepo;
    @Autowired PlatformTransactionManager txm;
    @Autowired EntityManager em;

    /** AI 대신 정해진 답을 주고, 받은 요청을 기록한다. */
    static class FakeClient extends TranslationClient {
        final List<String> asked = new ArrayList<>();
        final Set<String> fail = new HashSet<>();   // 이 원문은 답에서 빠뜨린다
        FakeClient() { super(new OpenAiClient("", "none")); }
        @Override public Map<String, String> translate(Lang lang, List<String> names) {
            asked.addAll(names);
            Map<String, String> out = new HashMap<>();
            for (String n : names) if (!fail.contains(n)) out.put(n, n + "@" + lang);
            out.put("지어낸이름", "Ghost");   // 요청하지 않은 짝도 섞어 보낸다고 가정 — parse 가 거르지만 배치도 무시해야 한다
            return out;
        }
    }

    private static int seq = 0;

    private Venue venue(String name) {
        return venueRepo.saveAndFlush(Venue.fromTourApi("FL" + (seq++), name, "서울", new java.math.BigDecimal("37.5"), new java.math.BigDecimal("127.0"), "문화", null, null));
    }

    @Test
    @DisplayName("한 행이 빠져도 나머지는 저장되고, 빠진 행은 해시가 없어 다음에 다시 집힌다")
    void 부분_실패() {
        FakeClient c = new FakeClient();
        c.fail.add("실패장소");
        TranslationBatch b = new TranslationBatch(venueRepo, perfRepo, c, txm);
        Venue ok = venue("성공장소");
        Venue bad = venue("실패장소");

        b.translateVenues(Lang.EN, 100000);
        em.flush(); em.clear();

        Venue okRead = venueRepo.findById(ok.getId()).orElseThrow();
        Venue badRead = venueRepo.findById(bad.getId()).orElseThrow();
        assertThat(okRead.getNameEn()).isEqualTo("성공장소@EN");
        assertThat(okRead.getNameHash()).isEqualTo(TranslationBatch.hashOf("성공장소"));
        assertThat(badRead.getNameEn()).isNull();
        assertThat(badRead.getNameHash()).isNull();
        assertThat(venueRepo.findNeedingTranslation("EN", 100000))
                .extracting(Venue::getName).contains("실패장소").doesNotContain("성공장소");
    }

    @Test
    @DisplayName("두 번째 실행은 이미 끝낸 행을 AI 에 보내지 않는다")
    void 재실행은_조용하다() {
        FakeClient c = new FakeClient();
        TranslationBatch b = new TranslationBatch(venueRepo, perfRepo, c, txm);
        venue("재실행장소");
        for (Lang l : List.of(Lang.EN, Lang.JA, Lang.ZH)) b.translateVenues(l, 100000);
        em.flush(); em.clear();
        c.asked.clear();

        for (Lang l : List.of(Lang.EN, Lang.JA, Lang.ZH)) b.translateVenues(l, 100000);

        assertThat(c.asked).doesNotContain("재실행장소");
    }

    @Test
    @DisplayName("이름이 바뀌면 세 언어 모두 새로 번역한다 — 옛 번역이 남으면 안 된다")
    void 이름이_바뀌면_세_언어() {
        FakeClient c = new FakeClient();
        TranslationBatch b = new TranslationBatch(venueRepo, perfRepo, c, txm);
        Venue v = venue("옛이름장소");
        for (Lang l : List.of(Lang.EN, Lang.JA, Lang.ZH)) b.translateVenues(l, 100000);
        em.flush(); em.clear();

        Venue renamed = venueRepo.findById(v.getId()).orElseThrow();
        renamed.updateFromTourApi("새이름장소", null, null, null, null, null, null);
        venueRepo.saveAndFlush(renamed);
        for (Lang l : List.of(Lang.EN, Lang.JA, Lang.ZH)) b.translateVenues(l, 100000);
        em.flush(); em.clear();

        Venue read = venueRepo.findById(v.getId()).orElseThrow();
        assertThat(read.getNameEn()).isEqualTo("새이름장소@EN");
        assertThat(read.getNameJa()).isEqualTo("새이름장소@JA");
        assertThat(read.getNameZh()).isEqualTo("새이름장소@ZH");
    }

    @Test
    @DisplayName("행사도 같은 흐름이고, 가려진 중복 행은 번역하지 않는다")
    void 행사() {
        FakeClient c = new FakeClient();
        TranslationBatch b = new TranslationBatch(venueRepo, perfRepo, c, txm);
        Performance p = perfRepo.saveAndFlush(Performance.fromKopis("FLK" + (seq++), "행사제목", "연극",
                null, java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 12, 31), "공연중", null));

        b.translatePerformances(Lang.JA, 100000);
        em.flush(); em.clear();

        Performance read = perfRepo.findById(p.getId()).orElseThrow();
        assertThat(read.getTitleJa()).isEqualTo("행사제목@JA");
        assertThat(read.getNameHash()).isEqualTo(TranslationBatch.hashOf("행사제목"));
        assertThat(perfRepo.findNeedingTranslation("JA", 100000))
                .extracting(Performance::getTitle).doesNotContain("행사제목");
        assertThat(perfRepo.findNeedingTranslation("EN", 100000))
                .extracting(Performance::getTitle).contains("행사제목");
    }

    @Test
    @DisplayName("사람이 번역한 행에 AI 가 다른 언어를 채워도 출처는 HUMAN 으로 남는다")
    void 사람_표시_유지() {
        FakeClient c = new FakeClient();
        TranslationBatch b = new TranslationBatch(venueRepo, perfRepo, c, txm);
        Venue v = venue("사람번역장소");
        v.applyTranslation(Lang.EN, "Human Place");
        v.markHash(TranslationBatch.hashOf("사람번역장소"));
        em.createNativeQuery("update venues set tr_source='HUMAN' where id=:id").setParameter("id", v.getId()).executeUpdate();
        em.clear();

        b.translateVenues(Lang.JA, 100000);
        em.flush(); em.clear();

        Venue read = venueRepo.findById(v.getId()).orElseThrow();
        assertThat(read.getNameJa()).isEqualTo("사람번역장소@JA");
        assertThat(read.getNameEn()).isEqualTo("Human Place");
        assertThat(read.getTrSource()).isEqualTo("HUMAN");
    }
}
