package com.byeori.domain.user;

import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 갈라진 두 계정을 하나로 합친다.
 *
 * 카카오로 가입한 사람이 구글로도 들어와 계정이 둘이 된 경우를 되돌리는 일이다. 남길 계정
 * (keep)으로 다른 계정(merge)의 것을 전부 옮기고 그 계정을 지운다.
 *
 * **되돌릴 수 없다.** 그래서 부르는 쪽이 두 가지를 먼저 끝내야 한다 — 사용자가 그 계정의
 * 제공자로 실제 인증했을 것(소유 증명), 그리고 무엇이 넘어오는지 보고 확인했을 것.
 *
 * 어려운 데는 옮기기가 아니라 **중복**이다. 양쪽에서 같은 장소를 찜했으면 유니크 제약에
 * 걸려 통째로 실패한다. 그래서 겹치는 줄은 옮기기 전에 지운다 — 어차피 남길 계정에 같은
 * 것이 이미 있으니 잃는 것이 없다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountMerger {

    private final EntityManager em;

    /**
     * 옮기기 전에 겹치는 줄을 지워야 하는 표들.
     *
     * 각 줄은 "표 이름, 무엇이 같으면 겹치는 것으로 볼지"다. 유니크 제약을 그대로 옮겨
     * 적은 것이라, 제약이 늘면 여기도 늘려야 한다(안 늘리면 합치기가 통째로 실패한다).
     */
    private static final List<String[]> DEDUPE = List.of(
            new String[]{"wishlists", "venue_id"},
            new String[]{"wishlists", "performance_id"},
            new String[]{"user_interests", "kind, category"},
            new String[]{"content_tag_votes", "comment_tag_id, venue_id"},
            new String[]{"content_tag_votes", "comment_tag_id, performance_id"},
            new String[]{"review_reports", "review_id"},
            new String[]{"user_terms", "terms_id"}
    );

    /** 사용자 id 를 그대로 바꿔 끼우면 되는 표들. 겹칠 유니크 제약이 없다. */
    private static final List<String> MOVE = List.of(
            "itineraries", "reviews", "venue_reports", "view_logs", "search_logs",
            "wishlists", "user_interests", "content_tag_votes", "review_reports", "user_terms",
            "social_auths"
    );

    @Transactional
    public void merge(Long keep, Long drop) {
        if (keep == null || drop == null || keep.equals(drop)) return;

        // ① 겹치는 줄 먼저 치운다. 남길 계정에 같은 것이 이미 있으므로 잃는 것이 없다.
        for (String[] rule : DEDUPE) {
            String table = rule[0], cols = rule[1];
            /*
             * `=` 를 쓴다. `is not distinct from` 은 NULL 끼리도 같다고 보는데, 유니크
             * 인덱스는 NULL 을 서로 다르게 센다. 찜은 장소용·행사용 두 칸 중 하나만
             * 채워지므로, NULL 을 같다고 보면 **장소 찜끼리 performance_id 가 둘 다
             * NULL 이라는 이유로 겹친 것이 되어 통째로 지워진다**(실제로 그랬다).
             */
            String on = java.util.Arrays.stream(cols.split(","))
                    .map(String::strip)
                    .map(c -> "d." + c + " = k." + c)
                    .reduce((a, b) -> a + " and " + b).orElseThrow();
            em.createNativeQuery("""
                    delete from %s d
                     where d.user_id = :drop
                       and exists (select 1 from %s k where k.user_id = :keep and %s)
                    """.formatted(table, table, on))
                    .setParameter("drop", drop).setParameter("keep", keep).executeUpdate();
        }

        /*
         * ② 차단은 양쪽 칸이 모두 사용자를 가리켜 손이 더 간다.
         *
         * 지우기가 **옮기기보다 먼저**여야 한다. user_blocks 에는 ck_user_blocks_not_self
         * 체크 제약이 있어, 서로 차단해 둔 두 계정을 합치면 옮기는 그 순간 자기 차단 줄이
         * 되어 거기서 터진다(실제로 터뜨려 보고 알았다). 옮긴 뒤 치우려 해도 늦는다.
         */
        // 둘이 서로(또는 한쪽이 다른 쪽을) 차단한 줄 — 옮기면 자기 차단이 된다
        em.createNativeQuery("""
                delete from user_blocks
                 where (user_id = :drop and blocked_user_id = :keep)
                    or (user_id = :keep and blocked_user_id = :drop)
                """).setParameter("drop", drop).setParameter("keep", keep).executeUpdate();
        // 옮기면 (user_id, blocked_user_id) 가 겹칠 줄 — 양방향 모두
        em.createNativeQuery("""
                delete from user_blocks d
                 where d.user_id = :drop
                   and exists (select 1 from user_blocks k
                                where k.user_id = :keep and k.blocked_user_id = d.blocked_user_id)
                """).setParameter("drop", drop).setParameter("keep", keep).executeUpdate();
        em.createNativeQuery("""
                delete from user_blocks d
                 where d.blocked_user_id = :drop
                   and exists (select 1 from user_blocks k
                                where k.blocked_user_id = :keep and k.user_id = d.user_id)
                """).setParameter("drop", drop).setParameter("keep", keep).executeUpdate();
        em.createNativeQuery("update user_blocks set user_id = :keep where user_id = :drop")
                .setParameter("keep", keep).setParameter("drop", drop).executeUpdate();
        em.createNativeQuery("update user_blocks set blocked_user_id = :keep where blocked_user_id = :drop")
                .setParameter("keep", keep).setParameter("drop", drop).executeUpdate();

        // ③ 나머지는 그대로 옮긴다.
        for (String table : MOVE) {
            em.createNativeQuery("update %s set user_id = :keep where user_id = :drop".formatted(table))
                    .setParameter("keep", keep).setParameter("drop", drop).executeUpdate();
        }

        // ④ 그 사람이 만든 장소는 지우지 않는다. 다른 사람의 리뷰·찜이 걸려 있는 공용
        //    콘텐츠이고, 그 사람의 비공개 장소라면 루트가 그걸 가리키고 있다.
        em.createNativeQuery("update venues set created_by_user_id = :keep where created_by_user_id = :drop")
                .setParameter("keep", keep).setParameter("drop", drop).executeUpdate();

        em.createNativeQuery("delete from users where id = :drop").setParameter("drop", drop).executeUpdate();
        em.flush();
        em.clear();
        log.info("계정 합침: {} ← {}", keep, drop);
    }

    /** 합치기 전에 "무엇이 넘어오는지" 보여줄 수치. 되돌릴 수 없는 일이라 미리 알려야 한다. */
    @Transactional(readOnly = true)
    public Summary summarize(Long userId) {
        return new Summary(
                count("itineraries", userId), count("wishlists", userId),
                count("reviews", userId), count("venues", userId, "created_by_user_id"));
    }

    private int count(String table, Long userId) { return count(table, userId, "user_id"); }

    private int count(String table, Long userId, String column) {
        Object n = em.createNativeQuery("select count(*) from %s where %s = :uid".formatted(table, column))
                .setParameter("uid", userId).getSingleResult();
        return ((Number) n).intValue();
    }

    public record Summary(int itineraries, int wishlists, int reviews, int venues) {
        public boolean isEmpty() { return itineraries + wishlists + reviews + venues == 0; }

        /** 사람에게 보일 한 줄. "루트 2개 · 찜 5개" 처럼 있는 것만 적는다. */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            if (itineraries > 0) sb.append("루트 ").append(itineraries).append("개");
            if (wishlists > 0) sb.append(sb.isEmpty() ? "" : " · ").append("찜 ").append(wishlists).append("개");
            if (reviews > 0) sb.append(sb.isEmpty() ? "" : " · ").append("리뷰 ").append(reviews).append("개");
            if (venues > 0) sb.append(sb.isEmpty() ? "" : " · ").append("등록한 장소 ").append(venues).append("곳");
            return sb.isEmpty() ? "담긴 내용 없음" : sb.toString();
        }
    }
}
