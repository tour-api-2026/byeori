package com.byeori.domain.activity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 무엇을 찾았는지. 결과 수까지 남겨, 헛친 검색을 나중에 가를 수 있게 한다. */
@Entity
@Table(name = "search_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String keyword;
    private String category;
    private int resultCount;
    private LocalDateTime searchedAt;

    public SearchLog(Long userId, String keyword, String category, int resultCount) {
        this.userId = userId;
        this.keyword = keyword;
        this.category = category;
        this.resultCount = resultCount;
        this.searchedAt = LocalDateTime.now();
    }
}
