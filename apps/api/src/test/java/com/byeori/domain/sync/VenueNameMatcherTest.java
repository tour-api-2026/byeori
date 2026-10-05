package com.byeori.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * KOPIS 공연시설명 → 우리 장소 매칭.
 *
 * 좌표로 잇지 않는 이유: 같은 건물의 다른 시설이 0~10m 거리에 있어 거리로는 갈리지 않고,
 * 행사 쪽에 이름이 없어 맞는지 확인할 방법도 없었다. 시설명은 공연목록 응답에 이미 들어
 * 있으므로(fcltynm) 추가 호출 없이 이름을 비교할 수 있다.
 *
 * 퍼지 매칭은 쓰지 않는다. 정규화한 이름이 정확히 같을 때만 잇는다 — 커버리지를 조금 잃는
 * 대신 틀린 연결을 만들지 않는다.
 */
class VenueNameMatcherTest {

    @Test
    @DisplayName("괄호 안 옛 이름은 떼어낸다")
    void 괄호_주석_제거() {
        assertThat(VenueNameMatcher.normalize("강릉아트센터 (구. 강릉문화예술관)"))
                .isEqualTo(VenueNameMatcher.normalize("강릉아트센터"));
        assertThat(VenueNameMatcher.normalize("경기아트센터(구. 경기도문화의전당)"))
                .isEqualTo(VenueNameMatcher.normalize("경기아트센터"));
    }

    @Test
    @DisplayName("대괄호 지역 표기도 떼어낸다")
    void 대괄호_제거() {
        assertThat(VenueNameMatcher.normalize("상상마당 [춘천]"))
                .isEqualTo(VenueNameMatcher.normalize("상상마당"));
    }

    @Test
    @DisplayName("가운뎃점·공백·마침표 차이를 흡수한다 — 같은 곳을 다르게 적는다")
    void 구분자_무시() {
        assertThat(VenueNameMatcher.normalize("3.15아트센터"))
                .isEqualTo(VenueNameMatcher.normalize("3·15 아트센터"));
        assertThat(VenueNameMatcher.normalize("CJ아지트 광흥창"))
                .isEqualTo(VenueNameMatcher.normalize("CJ아지트광흥창"));
    }

    @Test
    @DisplayName("XML 엔티티를 되돌린다 — KOPIS 응답에 &amp; 가 그대로 온다")
    void 엔티티_복원() {
        assertThat(VenueNameMatcher.normalize("KT&amp;G 상상마당"))
                .isEqualTo(VenueNameMatcher.normalize("KT&G 상상마당"));
    }

    @Test
    @DisplayName("대소문자를 무시한다")
    void 대소문자() {
        assertThat(VenueNameMatcher.normalize("Bibistudio"))
                .isEqualTo(VenueNameMatcher.normalize("BIBISTUDIO"));
    }

    @Test
    @DisplayName("빈 값과 괄호만 남는 이름은 매칭 대상이 아니다")
    void 빈_값() {
        assertThat(VenueNameMatcher.normalize(null)).isNull();
        assertThat(VenueNameMatcher.normalize("   ")).isNull();
        assertThat(VenueNameMatcher.normalize("(구. 어딘가)")).isNull();
    }

    @Test
    @DisplayName("색인에서 정확히 같은 이름만 찾는다 — 부분 일치는 잇지 않는다")
    void 완전일치만() {
        Map<String, Long> index = VenueNameMatcher.buildIndex(Map.of(
                1L, "강동아트센터",
                2L, "서울돈화문국악당",
                3L, "아트센터"));
        assertThat(VenueNameMatcher.find(index, "강동아트센터 (구. 강동구민회관)")).isEqualTo(1L);
        assertThat(VenueNameMatcher.find(index, "서울돈화문국악당")).isEqualTo(2L);
        // '대학로예술극장'은 색인에 없다 — 비슷한 '아트센터'로 끌어다 붙이지 않는다
        assertThat(VenueNameMatcher.find(index, "대학로예술극장")).isNull();
        assertThat(VenueNameMatcher.find(index, null)).isNull();
    }

    @Test
    @DisplayName("이름이 겹치는 장소가 둘 이상이면 잇지 않는다 — 어느 쪽인지 알 수 없다")
    void 중복_이름은_포기() {
        Map<String, Long> index = VenueNameMatcher.buildIndex(Map.of(
                10L, "문화예술회관",
                11L, "문화예술회관 "));
        assertThat(VenueNameMatcher.find(index, "문화예술회관")).isNull();
    }
}
