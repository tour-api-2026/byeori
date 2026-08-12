package com.byeori.domain.user;

import com.byeori.global.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴(계정 삭제). 개인정보와 개인 활동 데이터는 파기하고,
 * 사용자가 등록한 장소(venues)는 다른 이용자의 리뷰·즐겨찾기가 연결된 공용 콘텐츠이므로
 * 삭제하지 않고 작성자만 익명화(created_by_user_id = null)한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepo;
    private final EntityManager em;

    /** users.id를 참조하는 개인 데이터 테이블 — FK(NO ACTION) 때문에 users보다 먼저 지운다. */
    private static final List<String> PERSONAL_TABLES = List.of(
            "content_tag_votes", "venue_reports", "wishlists", "reviews",
            "social_auths", "user_interests", "user_terms");

    @Transactional
    public void deleteAccount(Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        // 일정은 하위 항목(itinerary_items)이 있어 먼저 정리
        em.createNativeQuery("""
                delete from itinerary_items
                 where itinerary_id in (select id from itineraries where user_id = :uid)
                """).setParameter("uid", userId).executeUpdate();
        em.createNativeQuery("delete from itineraries where user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        for (String table : PERSONAL_TABLES) {
            em.createNativeQuery("delete from " + table + " where user_id = :uid")
                    .setParameter("uid", userId).executeUpdate();
        }

        // 공용 콘텐츠는 유지하되 작성자 익명화
        em.createNativeQuery("update venues set created_by_user_id = null where created_by_user_id = :uid")
                .setParameter("uid", userId).executeUpdate();

        userRepo.delete(user);
        em.flush();
        log.info("계정 삭제 완료 userId={}", userId);
    }
}
