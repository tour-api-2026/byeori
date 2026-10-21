package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.byeori.domain.translation.TranslationClient;
import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.TourDetail;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
 * 상세는 로그인 없이 열려서 장소 id 를 훑으면 번역을 마구 일으킬 수 있다. 하루 총량(여기서는 2)을
 * 넘으면 AI 를 부르지 않고 한국어를 준다. 이미 저장된 번역을 읽는 것은 상한을 쓰지 않는다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789",
        "byeori.translation.detail-daily-cap=2"
})
class VenueDetailTranslationCapTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean TranslationClient translationClient;
    @MockitoBean TourApiClient tourClient;
    @Autowired VenueService service;
    @Autowired VenueRepository repo;

    private static final String KO = "조선의 법궁입니다.";
    private final List<Long> ids = new ArrayList<>();

    @AfterEach
    void clean() {
        repo.deleteAllById(ids);
    }

    private String seed(String code) {
        Venue v = Venue.fromTourApi(code, "장소" + code, "서울", new BigDecimal("37.5796"),
                new BigDecimal("126.9770"), "문화", null, null);
        Long id = repo.saveAndFlush(v).getId();
        ids.add(id);
        return String.valueOf(id);
    }

    @Test
    @DisplayName("상한을 넘으면 AI 를 부르지 않고 한국어를 준다 — 저장된 번역은 상한과 무관하게 읽힌다")
    void 상한() {
        when(tourClient.detail(any())).thenReturn(new TourDetail(KO, null, null, null, null, null));
        when(translationClient.translateText(any(), any())).thenReturn("EN");
        String a = seed("CAP_1"), b = seed("CAP_2"), c = seed("CAP_3");

        assertThat(service.detailByKey(Lang.EN, a).liveInfo().overview()).isEqualTo("EN");
        assertThat(service.detailByKey(Lang.EN, b).liveInfo().overview()).isEqualTo("EN");
        // 셋째는 상한 — 한국어, AI 호출 없음
        assertThat(service.detailByKey(Lang.EN, c).liveInfo().overview()).isEqualTo(KO);
        verify(translationClient, times(2)).translateText(any(), any());
        // 이미 번역된 곳은 상한에 닿은 뒤에도 번역본을 준다
        assertThat(service.detailByKey(Lang.EN, a).liveInfo().overview()).isEqualTo("EN");
        verify(translationClient, times(2)).translateText(any(), any());
    }
}
