package com.byeori.domain.venue;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.domain.performance.Performance;
import com.byeori.domain.performance.dto.PerformanceResponse;
import com.byeori.domain.venue.dto.VenueDetailResponse;
import com.byeori.domain.venue.dto.VenueResponse;
import com.byeori.global.i18n.Lang;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 응답이 요청 언어의 이름을 고르고, 번역이 없으면 한국어 원본으로 떨어진다. */
class VenueLanguageTest {

    private Venue 경복궁() {
        Venue v = Venue.fromTourApi("LANG_1", "경복궁", "서울", new BigDecimal("37.5796"),
                new BigDecimal("126.9770"), "문화", null, null);
        v.applyTranslation(Lang.EN, "Gyeongbokgung Palace");
        return v;
    }

    @Test
    @DisplayName("번역이 있으면 그 언어로 준다")
    void 번역_있음() {
        assertThat(VenueResponse.from(경복궁(), Lang.EN).name()).isEqualTo("Gyeongbokgung Palace");
    }

    @Test
    @DisplayName("번역이 없는 언어는 한국어로 떨어진다 — 빈 이름이 나가면 안 된다")
    void 번역_없음() {
        assertThat(VenueResponse.from(경복궁(), Lang.JA).name()).isEqualTo("경복궁");
        assertThat(VenueResponse.from(경복궁(), Lang.ZH).name()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("한국어 요청은 늘 원본")
    void 한국어() {
        assertThat(VenueResponse.from(경복궁(), Lang.KO).name()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("번역이 빈 문자열이어도 한국어로 떨어진다")
    void 빈_번역() {
        Venue v = 경복궁();
        v.applyTranslation(Lang.EN, "");
        assertThat(VenueResponse.from(v, Lang.EN).name()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("상세 응답도 같은 규칙을 따른다")
    void 상세() {
        assertThat(VenueDetailResponse.from(경복궁(), Lang.EN).name()).isEqualTo("Gyeongbokgung Palace");
        assertThat(VenueDetailResponse.from(경복궁(), Lang.ZH).name()).isEqualTo("경복궁");
    }

    @Test
    @DisplayName("행사 제목도 같은 규칙을 따른다")
    void 행사() {
        Performance p = Performance.fromTour("P_1", "수원화성문화제", "축제", null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), "ONGOING", null, null);
        p.applyTranslation(Lang.EN, "Suwon Hwaseong Cultural Festival");
        assertThat(PerformanceResponse.from(p, Lang.EN).title()).isEqualTo("Suwon Hwaseong Cultural Festival");
        assertThat(PerformanceResponse.from(p, Lang.JA).title()).isEqualTo("수원화성문화제");
        assertThat(PerformanceResponse.from(p, Lang.KO).title()).isEqualTo("수원화성문화제");
    }
}
