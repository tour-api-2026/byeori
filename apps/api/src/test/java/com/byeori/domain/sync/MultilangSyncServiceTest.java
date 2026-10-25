package com.byeori.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.byeori.domain.venue.Venue;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.external.TourApiClient;
import com.byeori.global.external.dto.TourItem;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 공사 다국어판을 우리 장소에 붙이는 동기화.
 *
 * 틀려도 화면이 깨지지 않고 **엉뚱한 이름이 조용히 붙는** 종류라, 붙여야 하는 경우만큼
 * 붙이면 안 되는 경우를 함께 건다. 제목과 좌표는 운영 응답에서 가져온 실제 값이다.
 */
@Testcontainers
@SpringBootTest(properties = {
        "byeori.auth.jwt-secret=test-only-secret-not-for-production-0123456789"
})
class MultilangSyncServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean TourApiClient tourClient;

    @Autowired MultilangSyncService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired VenueRepository repo;

    private final List<Long> ids = new ArrayList<>();

    @AfterEach
    void clean() {
        repo.deleteAllById(ids);
    }

    /**
     * 시드 데이터에 경복궁·창경궁 같은 이름이 이미 들어 있다. 테스트가 같은 이름을 또 넣으면
     * 후보가 둘이 되어 규칙이 (의도대로) 건너뛴다. 겹치지 않게 표식을 붙인다.
     */
    private Venue seed(String code, String name, String lat, String lng) {
        Venue v = Venue.fromTourApi(code, name, "서울", new BigDecimal(lat), new BigDecimal(lng),
                "문화", null, null);
        Venue saved = repo.saveAndFlush(v);
        ids.add(saved.getId());
        return saved;
    }

    /** 공사 다국어 목록 한 쪽만 돌려준다(두 번째 쪽은 비어 끝난다). */
    private void given(Lang lang, TourItem... items) {
        when(tourClient.multilangList(eq(lang), eq(1), anyInt())).thenReturn(List.of(items));
        when(tourClient.multilangList(eq(lang), eq(2), anyInt())).thenReturn(List.of());
    }

    private static TourItem item(String cid, String title, String lat, String lng) {
        // contentId, title, addr1, mapy(lat), mapx(lng), firstImage, contentTypeId, lcls2, lcls3, tel
        return new TourItem(cid, title, "서울", lat, lng, null, "12", null, null, null);
    }

    @Test
    @DisplayName("이름과 좌표가 맞으면 공식 이름을 붙이고 한국어 원문은 뗀다")
    void 붙인다() {
        Venue v = seed("ML_1", "ZZML경복궁", "37.57603", "126.97672");
        given(Lang.EN, item("264337", "Gyeongbokgung Palace (ZZML경복궁)", "37.57603", "126.97672"));

        assertThat(service.sync(Lang.EN)).isEqualTo(1);

        Venue after = repo.findById(v.getId()).orElseThrow();
        assertThat(after.getNameEn()).isEqualTo("Gyeongbokgung Palace");   // 괄호 속 한글은 뗀다
        assertThat(after.tourContentIdIn(Lang.EN)).isEqualTo("264337");
    }

    /**
     * 이 테스트가 규칙의 핵심이다. '송정해수욕장'은 부산과 강원에 하나씩 있고, 이름만 보면
     * 둘 다 맞는다. 좌표를 함께 보지 않으면 운영 데이터에서 147건이 잘못 붙었다.
     */
    @Test
    @DisplayName("이름이 같아도 좌표가 멀면 붙이지 않는다")
    void 동명이소는_안_붙인다() {
        Venue busan = seed("ML_2", "ZZML송정해수욕장", "35.1787", "129.1997");
        given(Lang.EN, item("999", "Songjeong Beach (ZZML송정해수욕장)", "37.7769", "128.9379"));  // 강원

        assertThat(service.sync(Lang.EN)).isZero();
        assertThat(repo.findById(busan.getId()).orElseThrow().getNameEn()).isNull();
    }

    @Test
    @DisplayName("이름도 좌표도 맞는 곳이 둘이면 건너뛴다 — 잘못 붙이느니 안 붙인다")
    void 모호하면_건너뛴다() {
        Venue a = seed("ML_3", "ZZML같은이름카페", "37.5000", "127.0000");
        Venue b = seed("ML_4", "ZZML같은이름카페", "37.5001", "127.0001");
        given(Lang.EN, item("888", "Same Name Cafe (ZZML같은이름카페)", "37.5000", "127.0000"));

        assertThat(service.sync(Lang.EN)).isZero();
        assertThat(repo.findById(a.getId()).orElseThrow().getNameEn()).isNull();
        assertThat(repo.findById(b.getId()).orElseThrow().getNameEn()).isNull();
    }

    /**
     * 사람이 손본 번역은 기계가 덮지 않는다. 사용자가 3,261행을 직접 번역해 넣었고
     * 그게 가장 정확하다.
     */
    @Test
    @DisplayName("사람이 번역한 행은 이름을 덮지 않는다 — contentId 는 적어 둔다")
    void 사람_번역은_지킨다() {
        Venue v = seed("ML_5", "ZZML창경궁", "37.5785", "126.9950");
        v.applyTranslation(Lang.EN, "Changgyeonggung Palace");
        repo.saveAndFlush(v);
        // 사람 번역 표시는 가져오기 스크립트가 SQL 로 찍는다(scripts/import-translations.py).
        jdbc.update("update venues set tr_source = 'HUMAN' where id = ?", v.getId());

        given(Lang.EN, item("777", "Changgyeonggung Palace Official (ZZML창경궁)", "37.5785", "126.9950"));
        service.sync(Lang.EN);

        Venue after = repo.findById(v.getId()).orElseThrow();
        assertThat(after.getNameEn()).isEqualTo("Changgyeonggung Palace");   // 그대로
        // 이름은 안 덮어도 설명을 받아오려면 id 는 있어야 한다
        assertThat(after.tourContentIdIn(Lang.EN)).isEqualTo("777");
    }

    @Test
    @DisplayName("AI 가 쓴 이름은 공식 번역으로 덮는다")
    void AI_는_덮는다() {
        Venue v = seed("ML_6", "ZZML창경궁B", "37.5785", "126.9950");
        v.applyTranslation(Lang.EN, "Changdeokgung Palace");   // AI 가 실제로 낸 오답(다른 궁이다)
        repo.saveAndFlush(v);

        given(Lang.EN, item("776", "Changgyeonggung Palace (ZZML창경궁B)", "37.5785", "126.9950"));
        service.sync(Lang.EN);

        assertThat(repo.findById(v.getId()).orElseThrow().getNameEn()).isEqualTo("Changgyeonggung Palace");
    }

    /**
     * 해시를 안 찍으면 야간 AI 배치가 이 행을 '번역이 필요한 행'으로 다시 집어
     * 공식 번역을 AI 번역으로 덮는다.
     */
    @Test
    @DisplayName("공식 번역을 넣은 행은 AI 배치 대상에서 빠진다")
    void AI_배치가_다시_집지_않는다() {
        Venue v = seed("ML_7", "ZZML덕수궁", "37.5658", "126.9751");
        given(Lang.EN, item("775", "Deoksugung Palace (ZZML덕수궁)", "37.5658", "126.9751"));
        service.sync(Lang.EN);

        List<Venue> todo = repo.findNeedingTranslation("EN", 1000, 3);
        assertThat(todo).extracting(Venue::getId).doesNotContain(v.getId());
    }

    @Test
    @DisplayName("원문이 달려 있지 않은 제목은 건너뛴다")
    void 원문이_없으면_건너뛴다() {
        Venue v = seed("ML_8", "ZZML키카페", "37.5000", "127.0000");
        given(Lang.EN, item("774", "Quay Cafe", "37.5000", "127.0000"));

        assertThat(service.sync(Lang.EN)).isZero();
        assertThat(repo.findById(v.getId()).orElseThrow().getNameEn()).isNull();
    }
}
