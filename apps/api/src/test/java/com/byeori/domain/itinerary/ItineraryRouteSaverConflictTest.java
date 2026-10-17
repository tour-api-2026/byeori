package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 같은 루트를 동시에 열었을 때 저장이 부딪히면 어떻게 되는가 — 진짜 DB 로 본다.
 *
 * 목으로는 "예외를 삼킨다"까지만 확인할 수 있다. 정작 봐야 할 것은 그 예외가
 * DataIntegrityViolationException 으로 올라오는지, 그리고 깨진 트랜잭션이 경로를
 * 돌려주려는 쪽을 끌고 들어가지 않는지다. 그건 실제 기본키 충돌로만 알 수 있다.
 *
 * 경쟁을 시간에 맡기지 않는다. 테스트 쪽이 커밋하지 않은 INSERT 로 키를 잡고 있으면
 * 저장 스레드는 그 자리에서 **반드시** 멈춘다. pg_stat_activity 로 멈춘 것을 확인한
 * 뒤에 커밋해서, 저장 쪽이 중복 키를 보게 만든다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
class ItineraryRouteSaverConflictTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ItineraryRouteSaver saver;
    @Autowired ItineraryRouteRepository repo;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    private static final String PATH = "[[37.5663,126.9779],[37.5700,126.9800]]";
    private static final String LEGS = "[{\"distance\":520,\"duration\":380,\"mode\":\"WALK\"}]";

    private Long itineraryId;

    @BeforeEach
    void seed() {
        Long userId = jdbc.queryForObject(
                "insert into users (name, email) values ('저장경쟁', 'saver-conflict@example.com')"
                        + " returning id", Long.class);
        itineraryId = jdbc.queryForObject(
                "insert into itineraries (user_id, title, start_date, end_date) values"
                        + " (?, '저장 경쟁 테스트', date '2026-10-20', date '2026-10-21') returning id",
                Long.class, userId);
    }

    @AfterEach
    void cleanup() {
        // 롤백에 맡길 수 없다 — 이 테스트는 실제로 커밋해야 경쟁이 성립한다.
        jdbc.update("delete from itinerary_routes where itinerary_id = ?", itineraryId);
        jdbc.update("delete from itineraries where id = ?", itineraryId);
        jdbc.update("delete from users where email = 'saver-conflict@example.com'");
    }

    @Test
    @DisplayName("기본키가 부딪히면 DataIntegrityViolationException 으로 올라오고, 이긴 쪽 값이 남는다")
    void 동시_저장() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> loser = tx.execute(status -> {
                // 이긴 쪽. 아직 커밋하지 않아 저장 스레드는 이 줄을 볼 수 없고, 넣으려 하면 멈춘다.
                jdbc.update("insert into itinerary_routes"
                                + " (itinerary_id, mode, stops_hash, distance_m, duration_s, path, legs)"
                                + " values (?, 'AUTO', 'winner', 1111, 2222, ?::jsonb, ?::jsonb)",
                        itineraryId, PATH, LEGS);

                Future<Throwable> f = pool.submit(() -> {
                    try {
                        saver.store(itineraryId, "AUTO", "loser", 3333, 4444, PATH, LEGS);
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                });
                assertThat(blockedOnRoutes()).as("저장 스레드가 중복 키에서 멈춰야 한다. 활동: %s", dump()).isTrue();
                return f;   // 반환하면 커밋되고, 그때 저 스레드가 중복 키를 본다
            });

            // 지는 쪽은 예외를 받는다 — 서비스는 이것만 보고 삼킬 수 있어야 한다.
            Throwable thrown = loser.get(10, TimeUnit.SECONDS);
            assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);

            // 이긴 쪽 값이 그대로 남고, 줄은 하나다.
            ItineraryRoute r = repo.findByItineraryIdAndMode(itineraryId, "AUTO").orElseThrow();
            assertThat(r.getStopsHash()).isEqualTo("winner");
            assertThat(r.getDistanceM()).isEqualTo(1111);
            assertThat(jdbc.queryForObject("select count(*) from itinerary_routes where itinerary_id = ?",
                    Integer.class, itineraryId)).isEqualTo(1);

            // 저장이 터진 뒤에도 이 연결로 계속 일할 수 있다 — 바깥 트랜잭션이 오염되지 않았다.
            saver.store(itineraryId, "WALK", "after-conflict", 10, 20, PATH, LEGS);
            assertThat(repo.findByItineraryIdAndMode(itineraryId, "WALK")).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("이미 커밋된 줄이 있으면 충돌이 아니라 갱신이다")
    void 이미_있으면_갱신() {
        saver.store(itineraryId, "AUTO", "first", 100, 200, PATH, LEGS);
        saver.store(itineraryId, "AUTO", "second", 300, 400, PATH, LEGS);

        ItineraryRoute r = repo.findByItineraryIdAndMode(itineraryId, "AUTO").orElseThrow();
        assertThat(r.getStopsHash()).isEqualTo("second");
        assertThat(r.getDistanceM()).isEqualTo(300);
    }

    /** 진단용 — 지금 DB 가 보는 모든 백엔드 상태. */
    private String dump() {
        return jdbc.queryForList("select pid, state, wait_event_type, wait_event,"
                + " left(query, 90) as q from pg_stat_activity where datname is not null").toString();
    }

    /**
     * 저장 스레드가 잠금 대기에 들어갔는가. 시간이 아니라 상태로 본다.
     *
     * 문장 본문(query)으로 찾지 않는다. 중복 키를 기다리는 백엔드는 state=idle 로 보이고
     * query 가 비어 있어서 테이블 이름으로는 걸리지 않는다. 다른 백엔드의 잠금을 기다리는
     * 중인지(pg_blocking_pids)로 본다 — 지금 잠금을 쥔 것은 이 테스트뿐이다.
     */
    private boolean blockedOnRoutes() {
        for (int i = 0; i < 200; i++) {   // 최대 10초
            Integer waiting = jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock'"
                            + " and cardinality(pg_blocking_pids(pid)) > 0", Integer.class);
            if (waiting != null && waiting > 0) return true;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
