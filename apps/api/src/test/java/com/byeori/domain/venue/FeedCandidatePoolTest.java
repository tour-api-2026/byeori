package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
 * 맞춤 추천 후보 뽑기.
 *
 * 전에는 search(null, ...) 로 200건을 받았다. 그 정렬이 'avg_rating desc, id asc' 인데
 * 운영 DB 에서 별점 있는 장소가 33,906곳 중 4곳이라 사실상 id 순이 되어, 후보 200건이
 * 문화 198 · 체험 1 · 한복 1 로 나왔다. 음식·공예를 고른 사람에게 맞는 후보가 0건이라
 * 취향 항(가중치 0.40)이 전부 0이 되고, 관심사항을 바꿔도 추천이 그대로였다.
 *
 * 여기서 확인하는 것은 세 가지다 — 고른 분류가 후보에 들어오는가, 고르지 않은 분류도
 * 남는가(발견), 그리고 지역이 모자랄 때 전국에서 채워지는가.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class FeedCandidatePoolTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired VenueRepository repo;
    @Autowired EntityManager em;

    @BeforeEach
    void seed() {
        // 기존 장소를 전부 비공개로 돌려 후보 풀을 이 테스트가 넣은 것만으로 만든다.
        // 지우지 않는 이유는 다른 표가 장소를 참조하기 때문이다. 트랜잭션이 되돌린다.
        em.createNativeQuery("update venues set visibility = 'PRIVATE'").executeUpdate();

        // 분류마다 수를 다르게 둔다 — 운영 데이터가 그렇다(맛집 10,388 vs 공예 52).
        // 한 쿼리로 합쳐 뽑으면 수가 많고 id 가 낮은 분류가 칸을 다 가져간다.
        insertMany("맛집", 30, "부산 해운대구");
        insertMany("카페", 30, "서울 마포구");
        insertMany("공예", 2, "서울 종로구");     // 관심 지역(부산)에는 없다
        insertMany("문화", 30, "서울 종로구");
        em.flush();
        em.clear();
    }

    private void insertMany(String category, int count, String addressPrefix) {
        for (int i = 0; i < count; i++) {
            em.createNativeQuery("insert into venues (name, address, lat, lng, image_url, category, source) "
                            + "values (:n, :a, 37.5, 127.0, 'https://x/i.jpg', :c, 'MANUAL')")
                    .setParameter("n", "TEST_" + category + "_" + i)
                    .setParameter("a", addressPrefix + " " + i + "길")
                    .setParameter("c", category)
                    .executeUpdate();
        }
    }

    private Map<String, Long> byCategory(List<Venue> venues) {
        return venues.stream().collect(Collectors.groupingBy(Venue::getCategory, Collectors.counting()));
    }

    @Test
    @DisplayName("고른 분류는 더 많이, 나머지도 조금은 들어온다")
    void 분류별_몫() {
        Map<String, Long> got = byCategory(
                repo.findFeedCandidates(Set.of("맛집", "카페"), null, null, null, 20, 5));

        assertThat(got).containsEntry("맛집", 20L).containsEntry("카페", 20L);
        // 고르지 않은 분류도 남는다 — 고른 것만 보여주면 새로운 것을 만날 길이 막힌다.
        assertThat(got).containsEntry("문화", 5L);
    }

    @Test
    @DisplayName("수가 적은 분류도 후보에 들어온다 — 전에는 0건이었다")
    void 적은_분류도_들어온다() {
        Map<String, Long> got = byCategory(
                repo.findFeedCandidates(Set.of("공예", "전통시장"), null, null, null, 20, 5));

        // 공예는 2건뿐이라 20칸을 다 못 채운다. 있는 만큼은 반드시 들어와야 한다.
        assertThat(got).containsEntry("공예", 2L);
        // 수가 많은 분류가 칸을 다 가져가지 않는다.
        assertThat(got.getOrDefault("맛집", 0L)).isEqualTo(5L);
    }

    @Test
    @DisplayName("관심 분류가 없으면 모든 분류가 골고루 들어온다 — 전에는 문화만 나왔다")
    void 관심이_없는_사람() {
        // FeedService 가 빈 목록 대신 넘기는 자리끼움 값. 어느 분류와도 같지 않다.
        Map<String, Long> got = byCategory(
                repo.findFeedCandidates(Set.of(""), null, null, null, 20, 5));

        assertThat(got).containsEntry("맛집", 5L).containsEntry("카페", 5L)
                .containsEntry("문화", 5L).containsEntry("공예", 2L);
    }

    @Test
    @DisplayName("관심 지역을 앞세우되, 그 지역에 모자라면 전국에서 채운다")
    void 지역은_걸러내지_않고_앞세운다() {
        List<Venue> got = repo.findFeedCandidates(Set.of("맛집", "공예"), "부산", null, null, 20, 5);

        // 맛집은 전부 부산에 있으니 20칸이 부산으로 찬다.
        assertThat(got.stream().filter(v -> v.getCategory().equals("맛집")))
                .hasSize(20)
                .allMatch(v -> v.getAddress().contains("부산"));
        // 공예는 부산에 하나도 없다. 걸러냈다면 0건이 됐을 칸이 서울로 채워진다.
        assertThat(got.stream().filter(v -> v.getCategory().equals("공예")))
                .hasSize(2)
                .allMatch(v -> v.getAddress().contains("서울"));
    }

    @Test
    @DisplayName("분류를 번갈아 내보낸다 — 동점일 때 id 큰 분류가 밀리지 않게")
    void 분류를_번갈아() {
        List<Venue> got = repo.findFeedCandidates(Set.of("맛집", "카페", "공예"), null, null, null, 20, 5);

        // 앞자리에 분류가 겹치지 않는다. 리뷰·별점이 없어 관심 분류끼리 점수가 동점이 되는데,
        // 번갈아 내보내지 않으면 최종 순서가 DB 의 행 반환 순서에 좌우된다.
        List<String> firstThree = got.stream().limit(3).map(Venue::getCategory).toList();
        assertThat(firstThree).doesNotHaveDuplicates();
        // 수가 가장 적은 공예(2건)도 앞자리를 얻는다 — 전에는 한 건도 못 올라왔다.
        assertThat(got.stream().limit(8).map(Venue::getCategory)).contains("공예");
    }

    @Test
    @DisplayName("같은 관심사면 매번 같은 후보가 나온다")
    void 결과가_재현된다() {
        List<Long> first = repo.findFeedCandidates(Set.of("맛집", "카페"), "부산", null, null, 10, 3)
                .stream().map(Venue::getId).toList();
        List<Long> again = repo.findFeedCandidates(Set.of("맛집", "카페"), "부산", null, null, 10, 3)
                .stream().map(Venue::getId).toList();

        assertThat(again).containsExactlyElementsOf(first);
    }

    @Test
    @DisplayName("좌표를 주면 가까운 곳을 먼저 뽑는다")
    void 좌표가_있으면_거리순() {
        em.createNativeQuery("insert into venues (name, address, lat, lng, image_url, category, source) "
                        + "values ('TEST_먼맛집', '부산 기장군 99길', 35.2, 129.2, 'https://x/i.jpg', '맛집', 'MANUAL')")
                .executeUpdate();
        em.flush();
        em.clear();

        // 37.5/127.0 에 30건이 몰려 있고 방금 넣은 한 건만 멀다. 칸을 30으로 끊으면
        // 가까운 30건이 들어오고 먼 것은 밀려난다.
        List<Venue> got = repo.findFeedCandidates(Set.of("맛집"), null,
                new BigDecimal("37.5"), new BigDecimal("127.0"), 30, 1);

        assertThat(got).extracting(Venue::getName).doesNotContain("TEST_먼맛집");
    }
}
