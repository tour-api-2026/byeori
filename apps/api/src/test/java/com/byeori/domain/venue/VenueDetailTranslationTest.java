package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.byeori.domain.translation.TranslationClient;
import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.TourDetail;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 트랜잭션 없이 돈다 — 서비스가 AI 를 트랜잭션 밖에서 부르는 구조를 검증하려면 테스트가 감싸면 안 된다.
 * 그래서 행을 실제로 커밋하고 끝나면 지운다.
 *
 * 상세 설명 번역은 **화면 뒤에서** 돈다. 지금 여는 사람은 한국어를 받고, 번역이 저장된 뒤
 * 여는 사람부터 번역본을 본다. 실패·재조회·한국어·빈 설명 네 길을 모두 고정한다.
 * AI 와 공사 API 는 가짜로 바꾸고, 뒤에서 도는 일은 같은 스레드에서 실행한다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Import(com.byeori.domain.translation.SameThreadExecutor.class)
class VenueDetailTranslationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean TranslationClient translationClient;
    @MockitoBean TourApiClient tourClient;

    @Autowired VenueService service;
    @Autowired VenueRepository repo;

    private static final String KO = "조선의 법궁입니다.";
    private String key;

    @BeforeEach
    void seed() {
        Venue v = Venue.fromTourApi("DESC_1", "경복궁", "서울", new BigDecimal("37.5796"),
                new BigDecimal("126.9770"), "문화", null, null);
        key = String.valueOf(repo.saveAndFlush(v).getId());
        when(tourClient.detail(any())).thenReturn(new TourDetail(KO, null, null, null, null, null));
    }

    @AfterEach
    void clean() {
        repo.deleteById(Long.valueOf(key));
    }

    private String overview(Lang lang) {
        return service.detailByKey(lang, key).liveInfo().overview();
    }

    /**
     * 이 테스트가 이 변경의 핵심이다. 전에는 첫 조회가 번역을 기다렸고, 긴 글은 그 안에 끝나지
     * 않아 매번 실패하며 아무것도 저장되지 않았다 — 그래서 아무도 번역본을 못 봤다.
     */
    @Test
    @DisplayName("첫 조회는 한국어를 주고, 번역은 뒤에서 저장된다. 두 번째부터 번역본")
    void 첫_조회는_한국어_두_번째는_번역본() {
        when(translationClient.translateText(Lang.EN, KO)).thenReturn("The main palace of Joseon.");

        // 기다리지 않는다 — 지금 여는 사람은 한국어를 받는다
        assertThat(overview(Lang.EN)).isEqualTo(KO);
        // 그 사이 뒤에서 번역이 끝나 커밋됐다(트랜잭션 밖에서 읽으므로 DB 값이다)
        assertThat(repo.findById(Long.valueOf(key)).orElseThrow().getDescEn()).isEqualTo("The main palace of Joseon.");
        // 다음 사람부터 번역본
        assertThat(overview(Lang.EN)).isEqualTo("The main palace of Joseon.");

        verify(translationClient, times(1)).translateText(eq(Lang.EN), any());
    }

    @Test
    @DisplayName("번역에 실패하면 한국어 설명을 그대로 주고 저장하지 않는다")
    void 실패하면_한국어() {
        when(translationClient.translateText(any(), any())).thenReturn(null);

        assertThat(overview(Lang.EN)).isEqualTo(KO);
        assertThat(repo.findById(Long.valueOf(key)).orElseThrow().getDescEn()).isNull();
    }

    @Test
    @DisplayName("한국어 요청은 AI 를 부르지 않는다")
    void 한국어() {
        assertThat(overview(Lang.KO)).isEqualTo(KO);
        verify(translationClient, never()).translateText(any(), any());
    }

    @Test
    @DisplayName("설명이 비어 있으면 AI 를 부르지 않는다")
    void 빈_설명() {
        when(tourClient.detail(any())).thenReturn(null);
        assertThat(service.detailByKey(Lang.JA, key).description()).isNull();
        verify(translationClient, never()).translateText(any(), any());
    }

    @Test
    @DisplayName("언어마다 따로 저장한다 — 영어 저장분을 일본어가 쓰지 않는다")
    void 언어별() {
        when(translationClient.translateText(Lang.EN, KO)).thenReturn("EN");
        when(translationClient.translateText(Lang.JA, KO)).thenReturn("JA");
        overview(Lang.EN);                                  // 한 번 열어 번역을 걸고
        overview(Lang.JA);
        assertThat(overview(Lang.EN)).isEqualTo("EN");       // 그다음부터 각자의 번역본
        assertThat(overview(Lang.JA)).isEqualTo("JA");
    }

    @Test
    @DisplayName("AI 를 부르는 동안 트랜잭션(DB 연결)을 쥐고 있지 않다")
    void 트랜잭션_밖에서_부른다() {
        boolean[] inTx = {true};
        when(translationClient.translateText(any(), any())).thenAnswer(inv -> {
            inTx[0] = TransactionSynchronizationManager.isActualTransactionActive();
            return "EN";
        });

        overview(Lang.EN);
        assertThat(inTx[0]).isFalse();
        assertThat(repo.findById(Long.valueOf(key)).orElseThrow().getDescEn()).isEqualTo("EN");
    }
}
