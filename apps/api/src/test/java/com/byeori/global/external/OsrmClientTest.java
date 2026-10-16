package com.byeori.global.external;

import static org.assertj.core.api.Assertions.assertThat;

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
 */
class OsrmClientTest {

    /** 실제 응답을 줄인 것(경복궁 근처 두 점). */
    private static final String OK = """
            {"code":"Ok","routes":[{"distance":2749.1,"duration":1979.4,
              "geometry":{"type":"LineString","coordinates":[[126.9770,37.5796],[126.9800,37.5780],[126.9947,37.5745]]}}]}""";

    @Test
    @DisplayName("거리·시간을 정수로 읽는다")
    void 요약() {
        OsrmRoute r = OsrmClient.parse(OK);
        assertThat(r).isNotNull();
        assertThat(r.distance()).isEqualTo(2749);
        assertThat(r.duration()).isEqualTo(1979);
    }

    @Test
    @DisplayName("좌표를 [위도, 경도] 로 뒤집어 준다")
    void 좌표_순서() {
        OsrmRoute r = OsrmClient.parse(OK);
        assertThat(r.path()).hasSize(3);
        assertThat(r.path().get(0)[0]).isEqualTo(37.5796);   // 위도가 앞
        assertThat(r.path().get(0)[1]).isEqualTo(126.9770);  // 경도가 뒤
    }

    @Test
    @DisplayName("경로를 못 찾으면 null — 걸어서 갈 수 없는 구간이 있다(제주↔육지)")
    void 경로_없음() {
        assertThat(OsrmClient.parse("{\"code\":\"NoRoute\",\"message\":\"...\"}")).isNull();
    }

    @Test
    @DisplayName("망가진 응답에도 터지지 않는다")
    void 이상한_응답() {
        assertThat(OsrmClient.parse("")).isNull();
        assertThat(OsrmClient.parse("not json")).isNull();
        assertThat(OsrmClient.parse("{\"code\":\"Ok\",\"routes\":[]}")).isNull();
    }

    @Test
    @DisplayName("walk: 좌표 원소가 null 이어도 던지지 않고 null 을 준다 (네트워크 호출 전에 실패)")
    void walk_null_좌표() {
        List<double[]> points = Arrays.asList(new double[]{126.9770, 37.5796}, null);
        assertThat(new OsrmClient("http://127.0.0.1:1").walk(points)).isNull();
    }

    @Test
    @DisplayName("walk: 원소 길이가 2 미만이어도 던지지 않고 null 을 준다 (네트워크 호출 전에 실패)")
    void walk_짧은_좌표() {
        List<double[]> points = List.of(new double[]{126.9770, 37.5796}, new double[]{1.0});
        assertThat(new OsrmClient("http://127.0.0.1:1").walk(points)).isNull();
    }
}
