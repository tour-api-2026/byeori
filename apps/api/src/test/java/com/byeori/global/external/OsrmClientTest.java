package com.byeori.global.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.global.external.OsrmClient.WalkResult;
import com.byeori.global.external.dto.OsrmRoute;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OSRM 응답 해석.
 *
 * 좌표 순서가 함정이다 — OSRM 은 geojson 규약대로 [경도, 위도] 로 주는데
 * 우리 지도는 [위도, 경도] 로 쓴다. 뒤집지 않으면 경로가 바다에 그려진다.
 *
 * 그리고 실패의 두 종류를 가려야 한다. "걸어서 갈 수 없는 구간"과 "OSRM 이 응답하지
 * 않는다"를 섞으면, 제주 구간 하나 때문에 남은 구간이 전부 차로 간다.
 */
class OsrmClientTest {

    /** 실제 응답을 줄인 것(경복궁 근처 두 점). */
    private static final String OK = """
            {"code":"Ok","routes":[{"distance":2749.1,"duration":1979.4,
              "geometry":{"type":"LineString","coordinates":[[126.9770,37.5796],[126.9800,37.5780],[126.9947,37.5745]]}}]}""";

    @Test
    @DisplayName("거리·시간을 정수로 읽는다")
    void 요약() {
        OsrmRoute r = OsrmClient.parse(OK).route();
        assertThat(r).isNotNull();
        assertThat(r.distance()).isEqualTo(2749);
        assertThat(r.duration()).isEqualTo(1979);
    }

    @Test
    @DisplayName("좌표를 [위도, 경도] 로 뒤집어 준다")
    void 좌표_순서() {
        OsrmRoute r = OsrmClient.parse(OK).route();
        assertThat(r.path()).hasSize(3);
        assertThat(r.path().get(0)[0]).isEqualTo(37.5796);   // 위도가 앞
        assertThat(r.path().get(0)[1]).isEqualTo(126.9770);  // 경도가 뒤
    }

    @Test
    @DisplayName("걸어서 갈 수 없는 구간은 장애가 아니다 — 그 구간만 차로 넘긴다(제주↔육지)")
    void 경로_없음() {
        WalkResult r = OsrmClient.parse("{\"code\":\"NoRoute\",\"message\":\"...\"}");
        assertThat(r.route()).isNull();
        // 서버는 멀쩡히 답했다. 차단기를 걸면 남은 구간이 전부 차로 간다.
        assertThat(r.serverFailed()).isFalse();
    }

    @Test
    @DisplayName("Ok 인데 쓸 좌표가 없는 응답도 장애로 세지 않는다")
    void 빈_경로() {
        assertThat(OsrmClient.parse("{\"code\":\"Ok\",\"routes\":[]}").serverFailed()).isFalse();
    }

    @Test
    @DisplayName("응답을 읽을 수 없으면 서버 장애로 본다 — 차단기를 걸 이유가 된다")
    void 이상한_응답() {
        assertThat(OsrmClient.parse("").serverFailed()).isTrue();
        assertThat(OsrmClient.parse("not json").serverFailed()).isTrue();
        assertThat(OsrmClient.parse(null).serverFailed()).isTrue();
    }

    @Test
    @DisplayName("walk: OSRM 에 닿지 못하면 장애로 준다 (연결 거부)")
    void walk_연결_실패() {
        WalkResult r = new OsrmClient("http://127.0.0.1:1").walk(
                List.of(new double[]{126.9770, 37.5796}, new double[]{126.9947, 37.5745}));
        assertThat(r.route()).isNull();
        assertThat(r.serverFailed()).isTrue();
    }

    @Test
    @DisplayName("walk: 좌표 원소가 null 이어도 던지지 않는다 (네트워크 호출 전에 실패)")
    void walk_null_좌표() {
        List<double[]> points = Arrays.asList(new double[]{126.9770, 37.5796}, null);
        WalkResult r = new OsrmClient("http://127.0.0.1:1").walk(points);
        assertThat(r.route()).isNull();
        assertThat(r.serverFailed()).isTrue();   // URL 조립에서 터진다 — 물어보지도 못했다
    }

    @Test
    @DisplayName("walk: 원소 길이가 2 미만이어도 던지지 않는다 (네트워크 호출 전에 실패)")
    void walk_짧은_좌표() {
        List<double[]> points = List.of(new double[]{126.9770, 37.5796}, new double[]{1.0});
        assertThat(new OsrmClient("http://127.0.0.1:1").walk(points).route()).isNull();
    }

    @Test
    @DisplayName("walk: 좌표가 모자라면 OSRM 탓이 아니다 — 차단기를 걸지 않는다")
    void walk_좌표_부족() {
        WalkResult r = new OsrmClient("http://127.0.0.1:1")
                .walk(List.of(new double[]{126.9770, 37.5796}));
        assertThat(r.route()).isNull();
        assertThat(r.serverFailed()).isFalse();
    }
}
