package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 저장된 경로를 언제 다시 쓰고 언제 버리는가.
 *
 * 해시로 가른다. 방문지와 순서가 그대로면 경로도 그대로이므로 저장된 것을 쓰고,
 * 하나라도 바뀌면 다시 계산한다. 갱신을 잊을 수 없는 구조가 낫다.
 */
class ItineraryRouteStoreTest {

    @Test
    @DisplayName("같은 방문지·순서면 같은 해시")
    void 같은_입력() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        List<double[]> b = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        assertThat(ItineraryRoute.hashOf(a)).isEqualTo(ItineraryRoute.hashOf(b));
    }

    @Test
    @DisplayName("순서가 바뀌면 다른 해시 — 끌어서 순서를 바꾸면 경로도 달라진다")
    void 순서가_바뀌면() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        List<double[]> b = List.of(new double[]{37.6, 127.1}, new double[]{37.5, 127.0});
        assertThat(ItineraryRoute.hashOf(a)).isNotEqualTo(ItineraryRoute.hashOf(b));
    }

    @Test
    @DisplayName("방문지가 늘면 다른 해시")
    void 방문지가_늘면() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        List<double[]> b = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1},
                                   new double[]{37.7, 127.2});
        assertThat(ItineraryRoute.hashOf(a)).isNotEqualTo(ItineraryRoute.hashOf(b));
    }

    @Test
    @DisplayName("빈 목록도 터지지 않는다")
    void 빈_목록() {
        assertThat(ItineraryRoute.hashOf(List.of())).isNotBlank();
    }
}
