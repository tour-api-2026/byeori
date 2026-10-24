package com.byeori.domain.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 상세 설명 번역이 화면을 붙잡지 않는지, 같은 글을 여러 번 사지 않는지.
 *
 * 운영에서 깨져 있던 두 가지를 고정한다. 요청 안에서 번역을 기다린 탓에 외국어 상세가
 * 8.5초 걸렸고, 그 끝에 시간 제한으로 끊겨 아무것도 저장되지 않아 다음 사람도 똑같이
 * 8.5초를 쓰고 또 실패했다.
 *
 * 트랜잭션 없이 돈다 — 번역이 트랜잭션 밖에서 저장되는 것을 보려면 테스트가 감싸면 안 된다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
class DetailTranslatorTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean TranslationClient client;

    @Autowired DetailTranslator translator;
    @Autowired VenueRepository repo;

    private static final String KO = "조선의 법궁입니다.";
    private final List<Long> ids = new ArrayList<>();

    @AfterEach
    void clean() {
        repo.deleteAllById(ids);
    }

    private long seed(String code) {
        Venue v = Venue.fromTourApi(code, "장소" + code, "서울", new BigDecimal("37.5796"),
                new BigDecimal("126.9770"), "문화", null, null);
        long id = repo.saveAndFlush(v).getId();
        ids.add(id);
        return id;
    }

    /**
     * 이것이 고치려는 것의 핵심이다. 번역이 아무리 오래 걸려도 request() 는 바로 돌아와야 한다 —
     * 그래야 상세 응답이 번역을 기다리지 않는다.
     *
     * 진짜 Executor(스프링이 만든 것)로 돈다. 같은 스레드 실행기를 끼우면 이 성질이 사라진다.
     */
    @Test
    @DisplayName("번역이 오래 걸려도 부른 쪽은 기다리지 않는다")
    void 기다리지_않는다() throws Exception {
        long id = seed("ASYNC_1");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.translateText(any(), any())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);   // 붙잡아 둔다
            return "EN";
        });

        long before = System.nanoTime();
        translator.request(id, Lang.EN, KO);
        long tookMs = (System.nanoTime() - before) / 1_000_000;

        assertThat(started.await(5, TimeUnit.SECONDS)).as("번역이 시작됐다").isTrue();
        assertThat(tookMs).as("번역이 붙잡혀 있는 동안에도 바로 돌아왔다").isLessThan(1_000);
        release.countDown();
    }

    /**
     * 인기 있는 장소는 여러 사람이 몇 초 안에 함께 연다. 그때마다 번역을 걸면 같은 글을
     * 여러 번 사고 하루 상한도 그만큼 빨리 닳는다.
     */
    @Test
    @DisplayName("같은 장소·언어를 동시에 여러 번 걸지 않는다")
    void 중복으로_걸지_않는다() throws Exception {
        long id = seed("ASYNC_2");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.translateText(any(), any())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "EN";
        });

        translator.request(id, Lang.EN, KO);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();   // 첫 번째가 돌고 있다
        translator.request(id, Lang.EN, KO);                       // 그 사이 같은 요청이 또 온다
        translator.request(id, Lang.EN, KO);
        release.countDown();

        // 첫 번째가 끝나기를 기다린 뒤 센다
        for (int i = 0; i < 100 && repo.findById(id).orElseThrow().getDescEn() == null; i++) {
            Thread.sleep(50);
        }
        assertThat(repo.findById(id).orElseThrow().getDescEn()).isEqualTo("EN");
        verify(client, times(1)).translateText(any(), any());
    }

    @Test
    @DisplayName("한국어와 빈 글은 번역을 걸지 않는다")
    void 걸지_않는_경우() {
        long id = seed("ASYNC_3");

        translator.request(id, Lang.KO, KO);
        translator.request(id, Lang.EN, null);
        translator.request(id, Lang.EN, "   ");

        verify(client, times(0)).translateText(any(), any());
    }
}
