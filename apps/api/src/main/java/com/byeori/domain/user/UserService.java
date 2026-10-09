package com.byeori.domain.user;

import com.byeori.global.exception.BadRequestException;
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
    private final UserBlockRepository blockRepo;
    private final EntityManager em;
    private final UserInterestRepository interestRepo;

    /** users.id를 참조하는 개인 데이터 테이블 — FK(NO ACTION) 때문에 users보다 먼저 지운다. */
    private static final List<String> PERSONAL_TABLES = List.of(
            "content_tag_votes", "venue_reports", "review_reports", "wishlists", "reviews",
            "social_auths", "user_interests", "user_terms",
            // 행동 기록도 개인 데이터다. 빼면 지운 사람의 조회·검색이 남는다.
            "view_logs", "search_logs");

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

        // 차단은 양방향으로 내 id를 참조한다(내가 차단한 것 + 나를 차단한 것)
        em.createNativeQuery("delete from user_blocks where user_id = :uid or blocked_user_id = :uid")
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

    /** 사용자 차단. 이미 차단했으면 아무 것도 하지 않는다(버튼 연타 대비). */
    @Transactional
    public void block(Long userId, Long targetUserId) {
        if (targetUserId == null || targetUserId.equals(userId)) {
            throw new BadRequestException("BLOCK_SELF", "자기 자신은 차단할 수 없습니다.");
        }
        if (!userRepo.existsById(targetUserId)) {
            throw new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없습니다.");
        }
        if (blockRepo.existsByUserIdAndBlockedUserId(userId, targetUserId)) return;
        blockRepo.save(new UserBlock(userId, targetUserId));
    }

    @Transactional
    public void unblock(Long userId, Long targetUserId) {
        blockRepo.deleteByUserIdAndBlockedUserId(userId, targetUserId);
    }

    @Transactional(readOnly = true)
    public List<Long> listBlocked(Long userId) {
        return blockRepo.findBlockedUserIds(userId);
    }

    // ── 관심사 ─────────────────────────────

    @Transactional(readOnly = true)
    public Interests getInterests(Long userId) {
        List<UserInterest> rows = interestRepo.findByUserId(userId);
        return new Interests(pick(rows, InterestCatalog.TOPIC), pick(rows, InterestCatalog.REGION));
    }

    /**
     * 관심사를 통째로 바꾼다. 더하기가 아니라 교체다 — 화면이 늘 전체 목록을 들고 있고,
     * 뺀 것을 따로 알려 주지 않기 때문이다.
     *
     * 모르는 낱말은 받지 않는다. 오타 하나가 조용히 쌓이면 영영 아무것도 안 걸리는
     * 관심사가 되는데, 사용자는 골라 뒀다고 믿는다.
     */
    @Transactional
    public Interests replaceInterests(Long userId, List<String> topics, List<String> regions) {
        // 조사까지 함께 넘긴다 — "주제는" / "지역은" 은 받침에 따라 달라서 붙여 만들 수 없다.
        List<String> t = clean(topics, InterestCatalog::isTopic, "주제", "주제는");
        List<String> r = clean(regions, InterestCatalog::isRegion, "지역", "지역은");
        interestRepo.deleteByUserId(userId);
        interestRepo.flush();   // 지우기가 넣기보다 먼저 가야 유니크 인덱스에 걸리지 않는다
        t.forEach(v -> interestRepo.save(new UserInterest(userId, InterestCatalog.TOPIC, v)));
        r.forEach(v -> interestRepo.save(new UserInterest(userId, InterestCatalog.REGION, v)));
        return new Interests(t, r);
    }

    private static List<String> pick(List<UserInterest> rows, String kind) {
        return rows.stream().filter(i -> kind.equals(i.getKind())).map(UserInterest::getCategory).sorted().toList();
    }

    private static List<String> clean(List<String> given, java.util.function.Predicate<String> known,
                                      String what, String whatWithParticle) {
        if (given == null) return List.of();
        List<String> out = given.stream().filter(java.util.Objects::nonNull).map(String::strip)
                .filter(v -> !v.isEmpty()).distinct().toList();
        if (out.size() > InterestCatalog.MAX_PER_KIND) {
            throw new BadRequestException("INTEREST_TOO_MANY",
                    whatWithParticle + " " + InterestCatalog.MAX_PER_KIND + "개까지 고를 수 있어요.");
        }
        out.stream().filter(v -> !known.test(v)).findFirst().ifPresent(bad -> {
            throw new BadRequestException("INTEREST_UNKNOWN", "알 수 없는 " + what + "예요: " + bad);
        });
        return out;
    }

    /** 고른 주제와 지역. 화면이 보낸 낱말 그대로 돌려준다. */
    public record Interests(List<String> topics, List<String> regions) {}
}
