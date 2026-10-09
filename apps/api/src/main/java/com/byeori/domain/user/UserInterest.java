package com.byeori.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 고른 관심 주제·지역.
 *
 * 테이블은 처음부터 있었지만 넣는 코드가 없어 비어 있었다(탈퇴 정리 목록에만 등장했다).
 * 추천의 첫 재료라 이제 실제로 채운다.
 */
@Entity
@Table(name = "user_interests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** TOPIC | REGION */
    private String kind;

    /** 주제면 '관람'·'공예' 같은 온보딩 낱말, 지역이면 '서울'·'전주'. */
    private String category;

    public UserInterest(Long userId, String kind, String category) {
        this.userId = userId;
        this.kind = kind;
        this.category = category;
    }
}
