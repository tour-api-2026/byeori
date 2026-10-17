package com.byeori.global.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.global.external.OsrmClient.WalkResult;
import com.byeori.global.external.dto.OsrmRoute;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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

    // ── 상태 코드 ─────────────────────────────
    //
    // 여기가 함정이었다. OSRM 은 경로를 못 찾은 것도 **400** 으로 알리는데, RestClient 는
    // 기본으로 4xx 에서 던진다. 그래서 응답 본문을 읽는 parse() 까지 가지도 못하고
    // 전부 "서버 장애"가 되어, 못 걷는 구간 하나가 남은 구간을 모두 차로 보냈다.
    // 아래 두 묶음은 상태 코드를 거치는 길을 본다 — JSON 만 넣어 보는 테스트로는 못 잡았다.

    @Test
    @DisplayName("400 NoRoute·NoSegment 는 그 구간을 못 걷는다는 사실이다 — 장애가 아니다")
    void 상태_400_경로_없음() {
        assertThat(OsrmClient.classify(400, "{\"code\":\"NoRoute\",\"message\":\"...\"}")
                .serverFailed()).isFalse();
        assertThat(OsrmClient.classify(400, "{\"code\":\"NoSegment\",\"message\":\"...\"}")
                .serverFailed()).isFalse();
    }

    @Test
    @DisplayName("그 밖의 400 은 우리가 잘못 물은 것이다 — 차단기와 경고를 남긴다")
    void 상태_400_우리_잘못() {
        // 실제로 받아 본 코드들이다. 좌표가 범위를 넘으면 InvalidValue, 좌표가 하나면
        // InvalidOptions 가 온다. 지형 탓이 아니라 우리 쪽 버그다.
        assertThat(OsrmClient.classify(400, "{\"code\":\"InvalidQuery\"}").serverFailed()).isTrue();
        assertThat(OsrmClient.classify(400, "{\"code\":\"InvalidValue\"}").serverFailed()).isTrue();
        assertThat(OsrmClient.classify(400, "{\"code\":\"InvalidOptions\"}").serverFailed()).isTrue();
        assertThat(OsrmClient.classify(400, "{\"code\":\"TooBig\"}").serverFailed()).isTrue();
    }

    @Test
    @DisplayName("5xx·읽을 수 없는 오류 본문은 장애다")
    void 상태_5xx() {
        assertThat(OsrmClient.classify(503, "").serverFailed()).isTrue();
        assertThat(OsrmClient.classify(500, "not json").serverFailed()).isTrue();
        assertThat(OsrmClient.classify(502, null).serverFailed()).isTrue();
    }

    @Test
    @DisplayName("200 은 본문을 읽는다")
    void 상태_200() {
        assertThat(OsrmClient.classify(200, OK).route()).isNotNull();
    }

    @Test
    @DisplayName("walk 가 400 을 예외로 흘리지 않는다 — 실제 상태 코드를 받아 본다")
    void walk_400_응답() throws Exception {
        // RestClient 는 기본으로 4xx 에서 던진다. 그 던짐을 막았는지는 상태 코드가 붙은
        // 실제 응답을 통과시켜야 알 수 있어, JDK 내장 서버를 띄운다(의존성 추가 없음).
        try (Stub stub = Stub.serving(400, "{\"code\":\"NoRoute\",\"message\":\"...\"}")) {
            WalkResult r = new OsrmClient(stub.baseUrl()).walk(
                    List.of(new double[]{126.9770, 37.5796}, new double[]{126.9947, 37.5745}));
            assertThat(r.route()).isNull();
            assertThat(r.serverFailed()).isFalse();   // 그 구간만 차로 넘기면 된다
        }
    }

    @Test
    @DisplayName("walk 는 500 을 장애로 받는다")
    void walk_500_응답() throws Exception {
        try (Stub stub = Stub.serving(500, "{\"code\":\"Error\"}")) {
            assertThat(new OsrmClient(stub.baseUrl()).walk(
                    List.of(new double[]{126.9770, 37.5796}, new double[]{126.9947, 37.5745}))
                    .serverFailed()).isTrue();
        }
    }

    @Test
    @DisplayName("walk 는 200 응답을 그대로 읽는다")
    void walk_200_응답() throws Exception {
        try (Stub stub = Stub.serving(200, OK)) {
            OsrmRoute route = new OsrmClient(stub.baseUrl()).walk(
                    List.of(new double[]{126.9770, 37.5796}, new double[]{126.9947, 37.5745})).route();
            assertThat(route).isNotNull();
            assertThat(route.distance()).isEqualTo(2749);
        }
    }

    /** 상태 코드가 붙은 응답 하나를 돌려주는 임시 서버. */
    private record Stub(HttpServer server) implements AutoCloseable {

        static Stub serving(int status, String body) throws Exception {
            HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            s.createContext("/", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
            });
            s.start();
            return new Stub(s);
        }

        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

        @Override public void close() { server.stop(0); }
    }
}
