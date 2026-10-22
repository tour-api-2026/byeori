package com.byeori.global.jpa;

import java.util.Locale;

/**
 * `like` 에 넣을 부분일치 패턴을 만든다.
 *
 * JPQL 안에서 `lower(concat('%', :keyword, '%'))` 로 만들지 않는 이유가 있다. keyword 가
 * null 이면 Hibernate 가 그 자리의 타입을 정하지 못해 **bytea** 로 바인딩하고, Postgres 에는
 * `lower(bytea)` 가 없어서 쿼리가 통째로 거부된다. 같은 쿼리의 `name like ?` 는 like 가
 * 타입을 text 로 고정해 주므로 멀쩡해서, 번역 컬럼 조건만 조용히 죽는다. 실제로 전통 테마
 * 행사 탭과 '내 주변' 목록이 그렇게 500 이 됐다(SearchKeywordNullTest 참고).
 *
 * 패턴을 미리 만들어 `like :pattern` 으로 넘기면 파라미터가 like 에 직접 붙어 타입이
 * 분명해진다. 행마다 문자열을 잇지 않아도 되니 조금 빠르기도 하다.
 *
 * 소문자화는 Java 쪽에서 한다. 컬럼 쪽은 SQL 의 `lower()` 가 그대로 하므로 양쪽이 맞는다.
 * Locale.ROOT 을 쓰는 이유 — 기본 로케일로 돌리면 터키어 환경에서 'I' 가 'ı' 가 되어
 * 서버의 로케일에 따라 검색 결과가 달라진다.
 */
public final class LikePattern {

    private LikePattern() {
    }

    /** null 이면 null 을 돌려준다 — 부르는 쿼리가 `:keyword is null` 로 이미 가르기 때문이다. */
    public static String contains(String keyword) {
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }
}
