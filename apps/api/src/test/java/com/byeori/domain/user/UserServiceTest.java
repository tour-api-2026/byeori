package com.byeori.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 계정 삭제: 개인 데이터는 파기되고 공용 콘텐츠(등록 장소)는 익명화되어 남는지 검증. */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class UserServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired UserService userService;
    @Autowired EntityManager em;

    private long count(String sql, Object param) {
        return ((Number) em.createNativeQuery(sql).setParameter("uid", param).getSingleResult()).longValue();
    }

    @Test
    void 계정_삭제_시_개인데이터는_파기되고_등록장소는_익명화된다() {
        Long userId = ((Number) em.createNativeQuery(
                "insert into users (name, email, auth_provider) values ('탈퇴테스트', 'bye@test.local', 'KAKAO') returning id")
                .getSingleResult()).longValue();
        Long venueId = ((Number) em.createNativeQuery(
                "insert into venues (name, address, lat, lng, created_by_user_id) "
                + "values ('테스트 장소', '서울시 종로구', 37.5, 127.0, :uid) returning id")
                .setParameter("uid", userId).getSingleResult()).longValue();
        em.createNativeQuery("insert into wishlists (user_id, venue_id) values (:uid, " + venueId + ")")
                .setParameter("uid", userId).executeUpdate();
        Long reviewId = ((Number) em.createNativeQuery(
                "insert into reviews (user_id, venue_id, rating) values (:uid, " + venueId + ", 5) returning id")
                .setParameter("uid", userId).getSingleResult()).longValue();
        // 신고·차단도 개인 데이터 — users FK 때문에 탈퇴 시 먼저 정리되어야 한다
        Long otherId = ((Number) em.createNativeQuery(
                "insert into users (name, auth_provider) values ('상대방', 'KAKAO') returning id")
                .getSingleResult()).longValue();
        em.createNativeQuery("insert into review_reports (review_id, user_id, reason) "
                + "values (" + reviewId + ", :uid, 'SPAM')")
                .setParameter("uid", userId).executeUpdate();
        em.createNativeQuery("insert into user_blocks (user_id, blocked_user_id) values (:uid, " + otherId + ")")
                .setParameter("uid", userId).executeUpdate();
        // 남이 나를 차단한 기록도 내 user_id를 참조하므로 함께 지워져야 한다
        em.createNativeQuery("insert into user_blocks (user_id, blocked_user_id) values (" + otherId + ", :uid)")
                .setParameter("uid", userId).executeUpdate();
        Long itinId = ((Number) em.createNativeQuery(
                "insert into itineraries (user_id, title, start_date, end_date) "
                + "values (:uid, '테스트 일정', current_date, current_date) returning id")
                .setParameter("uid", userId).getSingleResult()).longValue();
        em.createNativeQuery("insert into itinerary_items (itinerary_id, venue_id, visit_date, sort_order) "
                + "values (" + itinId + ", " + venueId + ", current_date, 1)").executeUpdate();
        em.flush();

        userService.deleteAccount(userId);

        assertEquals(0, count("select count(*) from users where id = :uid", userId), "계정이 남아 있음");
        assertEquals(0, count("select count(*) from wishlists where user_id = :uid", userId), "즐겨찾기가 남아 있음");
        assertEquals(0, count("select count(*) from reviews where user_id = :uid", userId), "리뷰가 남아 있음");
        assertEquals(0, count("select count(*) from itineraries where user_id = :uid", userId), "일정이 남아 있음");
        assertEquals(0, count("select count(*) from review_reports where user_id = :uid", userId), "리뷰 신고가 남아 있음");
        assertEquals(0, count(
                "select count(*) from user_blocks where user_id = :uid or blocked_user_id = :uid", userId),
                "차단 기록이 남아 있음");

        // 등록 장소는 공용 콘텐츠 — 남아 있고 작성자만 비워진다
        long venueLeft = ((Number) em.createNativeQuery(
                "select count(*) from venues where id = " + venueId + " and created_by_user_id is null")
                .getSingleResult()).longValue();
        assertTrue(venueLeft == 1, "등록 장소가 익명화되어 남아 있어야 함");
    }
}
