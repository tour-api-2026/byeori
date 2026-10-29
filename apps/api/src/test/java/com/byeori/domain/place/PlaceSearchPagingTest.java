package com.byeori.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.byeori.global.external.KakaoLocalClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 장소 검색의 쪽 넘기기.
 *
 * **응답 모양이 두 가지다.** page 를 주지 않으면 예전처럼 배열, 주면 {items, end} 다.
 * 이미 깔려 있는 앱(버전 11·12)이 unwrap&lt;KakaoPlace[]&gt; 로 배열을 읽으므로, 그 모양을
 * 깨면 OTA 를 안 받은 사용자의 장소 검색이 죽는다. 그 계약을 여기 고정한다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@AutoConfigureMockMvc(addFilters = false)   // 이 테스트가 보는 것은 응답 모양이지 인증이 아니다
class PlaceSearchPagingTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean KakaoLocalClient kakao;

    @Autowired MockMvc mvc;

    private static KakaoLocalClient.Place place(String id, String name) {
        return new KakaoLocalClient.Place(id, name, "AT4", "관광>명소", "서울", "02-0000-0000", 37.5, 127.0);
    }

    @Test
    @DisplayName("page 를 주지 않으면 예전처럼 배열을 준다 — 이미 깔린 앱이 이 모양을 읽는다")
    void 예전_모양() throws Exception {
        when(kakao.searchPage(eq("속초"), any(), any(), eq(1)))
                .thenReturn(new KakaoLocalClient.Page(List.of(place("1", "속초해수욕장")), false));

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/places/search").param("query", "속초")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                // data 가 배열이어야 한다. 객체가 되면 앱의 unwrap 이 깨진다.
                .andExpect(MockMvcResultMatchers.jsonPath("$.data").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].name").value("속초해수욕장"));
    }

    @Test
    @DisplayName("page 를 주면 끝인지 함께 준다")
    void 쪽_모양() throws Exception {
        when(kakao.searchPage(eq("속초"), any(), any(), eq(2)))
                .thenReturn(new KakaoLocalClient.Page(List.of(place("2", "속초중앙시장")), false));

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/places/search")
                        .param("query", "속초").param("page", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.items").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.items[0].name").value("속초중앙시장"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.end").value(false));
    }

    /**
     * 끝 판정은 카카오가 준 값을 그대로 전한다. 45 라는 상한을 우리 코드가 세면 카카오가
     * 바꿨을 때 따라가지 못한다.
     */
    @Test
    @DisplayName("마지막 쪽이면 end 가 true 다")
    void 마지막_쪽() throws Exception {
        when(kakao.searchPage(eq("속초"), any(), any(), eq(3)))
                .thenReturn(new KakaoLocalClient.Page(List.of(place("3", "영금정")), true));

        mvc.perform(MockMvcRequestBuilders.get("/api/v1/places/search")
                        .param("query", "속초").param("page", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.end").value(true));
    }
}
