package com.byeori.domain.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 경로 엔티티의 JPA 매핑이 실제 DB 에서 되는가.
 *
 * hashOf 는 순수 함수라 매핑을 건드리지 않는다. @IdClass 복합키와 jsonb 두 칼럼이
 * 틀리면 나중에 서비스를 붙일 때야 터져서 원인을 찾기 어렵다. 그래서 여기서 먼저
 * 실제 Postgres 로 저장 → 비우기 → 다시 읽기를 해 본다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class ItineraryRoutePersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired ItineraryRouteRepository repo;
    @Autowired EntityManager em;

    private static final String PATH = "[[37.5663,126.9779],[37.5700,126.9800]]";
    private static final String LEGS =
            "[{\"distance\":520,\"duration\":380,\"mode\":\"WALK\"}]";

    private Long itineraryId;

    @BeforeEach
    void seed() {
        // 외래키(itineraries.user_id → users.id)를 만족시키려면 사용자부터 있어야 한다.
        // 테스트 트랜잭션은 롤백되므로 뒤에 남지 않는다.
        Number userId = (Number) em.createNativeQuery(
                "insert into users (name, email) values ('경로테스트', 'route-test@example.com') "
                        + "returning id").getSingleResult();
        itineraryId = ((Number) em.createNativeQuery(
                "insert into itineraries (user_id, title, start_date, end_date) "
                        + "values (:u, '경로 저장 테스트', date '2026-10-20', date '2026-10-21') "
                        + "returning id")
                .setParameter("u", userId.longValue()).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("저장하고 영속성 컨텍스트를 비운 뒤 읽으면 모든 필드가 그대로 돌아온다")
    void 왕복() {
        repo.save(new ItineraryRoute(itineraryId, "WALK", "abc123", 520, 380, PATH, LEGS));
        em.flush();
        em.clear();

        ItineraryRoute r = repo.findByItineraryIdAndMode(itineraryId, "WALK").orElseThrow();

        assertThat(r.getItineraryId()).isEqualTo(itineraryId);
        assertThat(r.getMode()).isEqualTo("WALK");
        assertThat(r.getStopsHash()).isEqualTo("abc123");
        assertThat(r.getDistanceM()).isEqualTo(520);
        assertThat(r.getDurationS()).isEqualTo(380);
        assertThat(r.getComputedAt()).isNotNull();
        // jsonb 는 공백·키 순서를 정규화해 돌려줄 수 있어 문자열 동치가 아니라 JSON 동치로 본다.
        assertThat(json(r.getPath())).isEqualTo(json(PATH));
        assertThat(json(r.getLegs())).isEqualTo(json(LEGS));
    }

    @Test
    @DisplayName("같은 루트의 다른 모드는 서로 덮어쓰지 않고 공존한다 — 복합키의 존재 이유")
    void 모드별_공존() {
        repo.save(new ItineraryRoute(itineraryId, "WALK", "h-walk", 520, 380, PATH, LEGS));
        repo.save(new ItineraryRoute(itineraryId, "DRIVE", "h-drive", 900, 120, PATH, LEGS));
        em.flush();
        em.clear();

        ItineraryRoute walk = repo.findByItineraryIdAndMode(itineraryId, "WALK").orElseThrow();
        ItineraryRoute drive = repo.findByItineraryIdAndMode(itineraryId, "DRIVE").orElseThrow();

        assertThat(walk.getStopsHash()).isEqualTo("h-walk");
        assertThat(walk.getDistanceM()).isEqualTo(520);
        assertThat(drive.getStopsHash()).isEqualTo("h-drive");
        assertThat(drive.getDistanceM()).isEqualTo(900);
        assertThat(repo.findAll().stream()
                .filter(x -> x.getItineraryId().equals(itineraryId)).count()).isEqualTo(2);
    }

    @Test
    @DisplayName("update 는 같은 행을 갱신하고 computedAt 을 옮긴다")
    void 갱신() {
        repo.save(new ItineraryRoute(itineraryId, "WALK", "old", 520, 380, PATH, LEGS));
        em.flush();
        // 같은 트랜잭션이라 now() 기본값이 아닌 자바 시각이 들어가지만, 비교가 확실하도록
        // 저장된 시각을 과거로 밀어 둔다.
        em.createNativeQuery("update itinerary_routes set computed_at = :t where itinerary_id = :i")
                .setParameter("t", LocalDateTime.of(2026, 1, 1, 0, 0))
                .setParameter("i", itineraryId).executeUpdate();
        em.clear();

        ItineraryRoute loaded = repo.findByItineraryIdAndMode(itineraryId, "WALK").orElseThrow();
        LocalDateTime before = loaded.getComputedAt();
        assertThat(before).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));

        String newPath = "[[37.1,127.1],[37.2,127.2],[37.3,127.3]]";
        String newLegs = "[{\"distance\":700,\"duration\":500,\"mode\":\"WALK\"},"
                + "{\"distance\":300,\"duration\":200,\"mode\":\"WALK\"}]";
        loaded.update("new", 1000, 700, newPath, newLegs);
        em.flush();
        em.clear();

        ItineraryRoute r = repo.findByItineraryIdAndMode(itineraryId, "WALK").orElseThrow();
        assertThat(r.getStopsHash()).isEqualTo("new");
        assertThat(r.getDistanceM()).isEqualTo(1000);
        assertThat(r.getDurationS()).isEqualTo(700);
        assertThat(json(r.getPath())).isEqualTo(json(newPath));
        assertThat(json(r.getLegs())).isEqualTo(json(newLegs));
        assertThat(r.getComputedAt()).isAfter(before);
    }

    private static Object json(String s) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 이 아니다: " + s, e);
        }
    }
}
