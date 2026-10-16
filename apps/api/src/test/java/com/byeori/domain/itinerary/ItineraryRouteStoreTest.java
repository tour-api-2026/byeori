package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
        assertThat(ItineraryRoute.hashOf(a, 1000)).isEqualTo(ItineraryRoute.hashOf(b, 1000));
    }

    @Test
    @DisplayName("순서가 바뀌면 다른 해시 — 끌어서 순서를 바꾸면 경로도 달라진다")
    void 순서가_바뀌면() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        List<double[]> b = List.of(new double[]{37.6, 127.1}, new double[]{37.5, 127.0});
        assertThat(ItineraryRoute.hashOf(a, 1000)).isNotEqualTo(ItineraryRoute.hashOf(b, 1000));
    }

    @Test
    @DisplayName("방문지가 늘면 다른 해시")
    void 방문지가_늘면() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        List<double[]> b = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1},
                                   new double[]{37.7, 127.2});
        assertThat(ItineraryRoute.hashOf(a, 1000)).isNotEqualTo(ItineraryRoute.hashOf(b, 1000));
    }

    @Test
    @DisplayName("빈 목록도 터지지 않는다")
    void 빈_목록() {
        assertThat(ItineraryRoute.hashOf(List.of(), 1000)).isNotBlank();
    }

    @Test
    @DisplayName("기본 로케일이 소수점에 쉼표를 쓰는 곳이어도 같은 해시 — 캐시가 조용히 죽지 않는다")
    void 로케일과_무관() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        Locale original = Locale.getDefault();
        String expected = ItineraryRoute.hashOf(a, 1000);
        try {
            // 독일어권은 37,500000 처럼 쉼표를 쓴다. 해시가 달라지면 저장된 경로가 매번
            // 빗나가는데 오류도 로그도 없어, 이 테스트가 아니면 알아챌 길이 없다.
            Locale.setDefault(Locale.GERMANY);
            assertThat(ItineraryRoute.hashOf(a, 1000)).isEqualTo(expected);
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("기준값이 바뀌면 다른 해시 — 설정만 바꿔도 캐시가 비켜 준다")
    void 기준값이_바뀌면() {
        List<double[]> a = List.of(new double[]{37.5, 127.0}, new double[]{37.6, 127.1});
        // 도보 기준값을 바꾸면 같은 방문지라도 구간별 수단이 달라진다. 좌표만 보면
        // 저장본이 그대로 맞아떨어져서, 이미 캐시가 있는 사용자에게는 설정을 바꾼 효과가
        // 영원히 나타나지 않는다.
        assertThat(ItineraryRoute.hashOf(a, 1000)).isNotEqualTo(ItineraryRoute.hashOf(a, 500));
    }

    // ── 나이 ─────────────────────────────

    private static ItineraryRoute row(String hash) {
        return new ItineraryRoute(1L, "AUTO", hash, 960, 720, "[]", "[]");
    }

    @Test
    @DisplayName("방금 계산한 줄은 해시가 맞으면 그대로 쓴다")
    void 신선한_줄() {
        assertThat(row("h").matches("h", Duration.ofDays(7))).isTrue();
    }

    @Test
    @DisplayName("보관 기간을 넘긴 줄은 쓰지 않는다 — 모양은 그대로여도 차 시간은 그때의 교통량이다")
    void 오래된_줄() {
        ItineraryRoute r = row("h");
        ReflectionTestUtils.setField(r, "computedAt", LocalDateTime.now().minusDays(8));
        assertThat(r.matches("h", Duration.ofDays(7))).isFalse();
        // 기간 안이면 같은 줄이 멀쩡히 쓰인다 — 나이 때문에 전부 버리는 게 아니다.
        assertThat(r.matches("h", Duration.ofDays(30))).isTrue();
    }

    @Test
    @DisplayName("해시가 틀리면 아무리 새것이어도 쓰지 않는다")
    void 해시가_먼저() {
        assertThat(row("h").matches("다른해시", Duration.ofDays(7))).isFalse();
    }

    @Test
    @DisplayName("계산 시각이 비어 있으면 오래된 것으로 본다 — 모르는 값을 믿지 않는다")
    void 시각이_없으면() {
        ItineraryRoute r = row("h");
        ReflectionTestUtils.setField(r, "computedAt", null);
        assertThat(r.matches("h", Duration.ofDays(7))).isFalse();
        // 나이를 묻지 않겠다고 하면(null) 해시만 본다.
        assertThat(r.matches("h", null)).isTrue();
    }
}
