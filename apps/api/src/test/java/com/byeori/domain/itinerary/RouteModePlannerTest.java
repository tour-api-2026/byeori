package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 구간마다 걸을지 탈지 정한다.
 *
 * 기준을 1km 로 잡은 근거: 실제 루트 53개 구간의 중앙값이 706m 다. 500m 로 끊으면
 * "경주 첨성대 → 대릉원"(약 800m)처럼 관광객이 당연히 걷는 길이 차로 넘어간다.
 */
class RouteModePlannerTest {

    @Test
    @DisplayName("기준보다 가까우면 걷고, 멀면 탄다")
    void 자동_판정() {
        assertThat(RouteModePlanner.legMode("AUTO", 400, 1000)).isEqualTo("WALK");
        assertThat(RouteModePlanner.legMode("AUTO", 1500, 1000)).isEqualTo("DRIVE");
    }

    @Test
    @DisplayName("기준값 그 자체는 걷는 쪽에 넣는다 — 경계에서 차를 권할 이유가 없다")
    void 경계() {
        assertThat(RouteModePlanner.legMode("AUTO", 1000, 1000)).isEqualTo("WALK");
        assertThat(RouteModePlanner.legMode("AUTO", 1001, 1000)).isEqualTo("DRIVE");
    }

    @Test
    @DisplayName("사용자가 고르면 거리와 상관없이 그대로 따른다")
    void 사용자_선택() {
        assertThat(RouteModePlanner.legMode("WALK", 9000, 1000)).isEqualTo("WALK");
        assertThat(RouteModePlanner.legMode("DRIVE", 100, 1000)).isEqualTo("DRIVE");
    }

    @Test
    @DisplayName("모르는 값은 AUTO 로 본다 — 오타로 화면이 깨지면 안 된다")
    void 모르는_값() {
        assertThat(RouteModePlanner.legMode("hello", 400, 1000)).isEqualTo("WALK");
        assertThat(RouteModePlanner.legMode(null, 1500, 1000)).isEqualTo("DRIVE");
    }

    @Test
    @DisplayName("직선거리는 실제 거리와 맞아야 한다")
    void 거리_계산() {
        // 경복궁 → 종묘. 직선 약 1.6km (도보로는 2.7km).
        double m = RouteModePlanner.distanceMeters(37.5796, 126.9770, 37.5745, 126.9947);
        assertThat(m).isBetween(1500.0, 1750.0);
        // 같은 점이면 0
        assertThat(RouteModePlanner.distanceMeters(37.5, 127.0, 37.5, 127.0)).isZero();
    }
}
