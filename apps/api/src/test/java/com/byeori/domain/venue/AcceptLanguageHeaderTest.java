package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 헤더가 없거나 이상해도 요청은 성공하고, 번역 없는 행은 한국어 이름을 받는다.
 * 헬스체크·curl 은 헤더가 없고, 브라우저는 'ko-KR,ko;q=0.9' 같은 값을 보낸다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@AutoConfigureMockMvc
@Transactional
class AcceptLanguageHeaderTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired VenueRepository repo;

    private static final String URL = "/api/v1/venues/nearby?lat=37.5796&lng=126.9770&radius=1000";

    @BeforeEach
    void seed() {
        Venue tr = Venue.fromTourApi("HDR_1", "헤더경복궁", "서울", new BigDecimal("37.5796"),
                new BigDecimal("126.9770"), "문화", null, null);
        tr.applyTranslation(Lang.EN, "Header Palace");
        repo.saveAndFlush(tr);
        repo.saveAndFlush(Venue.fromTourApi("HDR_2", "헤더무명", "서울", new BigDecimal("37.5797"),
                new BigDecimal("126.9771"), "문화", null, null));
    }

    private String namesFor(String header) throws Exception {
        var req = get(URL);
        if (header != null) req = req.header("Accept-Language", header);
        return mvc.perform(req).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("en 이면 번역된 이름, 번역 없는 행은 한국어")
    void 영어() throws Exception {
        String body = namesFor("en");
        assertThat(body).contains("Header Palace").doesNotContain("헤더경복궁").contains("헤더무명");
    }

    @Test
    @DisplayName("헤더가 없으면 200 이고 한국어")
    void 헤더_없음() throws Exception {
        assertThat(namesFor(null)).contains("헤더경복궁").contains("헤더무명");
    }

    @Test
    @DisplayName("모르는 언어(de)·브라우저식 값·빈 값도 200 이고 한국어")
    void 이상한_헤더() throws Exception {
        for (String h : new String[]{"de", "ko-KR,ko;q=0.9,en-US;q=0.8", "", "*", "!!!"}) {
            assertThat(namesFor(h)).as(h).contains("헤더경복궁").contains("헤더무명");
        }
    }

    @Test
    @DisplayName("번역 없는 행은 어느 언어로 불러도 이름이 비지 않는다")
    void 번역_없는_행() throws Exception {
        for (String h : new String[]{"en", "ja", "zh", "ko"}) {
            mvc.perform(get(URL).header("Accept-Language", h))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[?(@.name == '헤더무명')]").isNotEmpty());
        }
    }
}
