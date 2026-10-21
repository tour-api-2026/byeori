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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 상세 설명은 처음 열릴 때만 AI 를 부른다. 사용자 요청 경로에 AI 가 들어오는 유일한 자리라
 * 실패·재조회·한국어·빈 설명 네 길을 모두 고정한다. AI 와 공사 API 는 가짜로 바꾼다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class VenueDetailTranslationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean TranslationClient translationClient;
    @MockitoBean TourApiClient tourClient;

    @Autowired VenueService service;
    @Autowired VenueRepository repo;
    @Autowired EntityManager em;

    private static final String KO = "조선의 법궁입니다.";
    private String key;

    @BeforeEach
    void seed() {
        Venue v = Venue.fromTourApi("DESC_1", "경복궁", "서울", new BigDecimal("37.5796"),
                new BigDecimal("126.9770"), "문화", null, null);
        key = String.valueOf(repo.saveAndFlush(v).getId());
        when(tourClient.detail(any())).thenReturn(new TourDetail(KO, null, null, null, null, null));
    }

    private String overview(Lang lang) {
        return service.detailByKey(lang, key).liveInfo().overview();
    }

    @Test
    @DisplayName("첫 조회는 번역해 저장하고, 두 번째는 AI 를 부르지 않는다")
    void 두_번째는_저장분() {
        when(translationClient.translateText(Lang.EN, KO)).thenReturn("The main palace of Joseon.");

        assertThat(overview(Lang.EN)).isEqualTo("The main palace of Joseon.");
        em.flush();
        em.clear();   // 메모리가 아니라 DB 에 저장됐는지 본다
        assertThat(repo.findById(Long.valueOf(key)).orElseThrow().getDescEn()).isEqualTo("The main palace of Joseon.");
        assertThat(overview(Lang.EN)).isEqualTo("The main palace of Joseon.");

        verify(translationClient, times(1)).translateText(eq(Lang.EN), any());
    }

    @Test
    @DisplayName("번역에 실패하면 한국어 설명을 그대로 주고 저장하지 않는다")
    void 실패하면_한국어() {
        when(translationClient.translateText(any(), any())).thenReturn(null);

        assertThat(overview(Lang.EN)).isEqualTo(KO);
        em.flush();
        em.clear();
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
        assertThat(overview(Lang.EN)).isEqualTo("EN");
        assertThat(overview(Lang.JA)).isEqualTo("JA");
    }
}
