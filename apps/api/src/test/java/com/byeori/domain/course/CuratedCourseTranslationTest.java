package com.byeori.domain.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.domain.course.dto.CuratedCourseResponse;
import com.byeori.global.i18n.Lang;
import jakarta.persistence.EntityManager;
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
 * V29 의 컬럼이 실제 DB 에 생기고, 번역이 있으면 그 언어로 없으면 한국어로 나온다.
 * 번역값은 마이그레이션이 아니라 import-translations.py 가 넣으므로, 여기서는 그 스크립트가
 * 만드는 것과 같은 꼴의 UPDATE 로 채운다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
@Transactional
class CuratedCourseTranslationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired CuratedCourseService service;
    @Autowired CuratedCourseRepository repo;
    @Autowired EntityManager em;

    private Long firstId() {
        return repo.findActive(null).get(0).getId();
    }

    private void fill(Long id) {
        em.createNativeQuery("update curated_courses set title_ja = '日本語タイトル', desc_ja = '日本語の説明' where id = :id")
                .setParameter("id", id).executeUpdate();
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("번역이 있으면 그 언어로, 목록과 상세 모두")
    void 번역이_있으면() {
        Long id = firstId();
        fill(id);

        CuratedCourseResponse row = service.list(Lang.JA, null).stream()
                .filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        assertThat(row.title()).isEqualTo("日本語タイトル");
        assertThat(row.description()).isEqualTo("日本語の説明");
        assertThat(service.detail(Lang.JA, id).title()).isEqualTo("日本語タイトル");
    }

    @Test
    @DisplayName("번역이 없는 언어와 한국어는 한국어 원본")
    void 없으면_한국어() {
        Long id = firstId();
        fill(id);
        String ko = repo.findById(id).orElseThrow().getTitle();

        assertThat(service.detail(Lang.EN, id).title()).isEqualTo(ko);
        assertThat(service.detail(Lang.KO, id).title()).isEqualTo(ko);
        assertThat(service.detail(Lang.ZH, id).description())
                .isEqualTo(repo.findById(id).orElseThrow().getDescription());
    }
}
