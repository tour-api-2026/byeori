package com.byeori.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 출처가 다른 같은 행사를 제목으로 가려낸다.
 *
 * 아래 제목은 모두 2026-10-11 운영 DB 에서 그대로 가져온 것이다. 진짜 중복(18쌍)과
 * 제목만 비슷한 다른 행사가 섞여 있어, 기준을 6자로 끊은 근거가 여기 남는다.
 */
class PerformanceDuplicateMatcherTest {

    @Test
    @DisplayName("회차·연도·대괄호 표기가 달라도 같은 행사로 본다")
    void 같은_행사() {
        // KOPIS 6384 / SEOUL 10448 — 사용자가 화면에서 나란히 본 그 두 줄이다.
        assertThat(PerformanceDuplicateMatcher.sameEvent(
                "제4회 대한민국 국악 관현악 축제, 강원특별자치도립 국악 관현악단",
                "[세종문화회관] 2026 대한민국국악관현악축제 [강원특별자치도립국악관현악단]"))
                .isTrue();
    }

    @Test
    @DisplayName("제목이 비슷하기만 한 다른 행사는 하나로 보지 않는다 — 유사도 비율로는 갈리지 않았다")
    void 다른_행사() {
        // 비율로 재면 0.71 로, 위의 진짜 중복(0.61)보다 오히려 높게 나오던 쌍이다.
        assertThat(PerformanceDuplicateMatcher.sameEvent("서울 바비큐 페스티벌", "서울뮤직페스티벌"))
                .isFalse();
        // 진짜 중복(위)과 같은 날짜에 들어온 다른 행사. 공통 토막이 "국악관현악" 5자뿐이다.
        assertThat(PerformanceDuplicateMatcher.sameEvent(
                "[세종문화회관] 2026 대한민국국악관현악축제 [성남시립국악단]",
                "청사국악관현악단과 함께 하는 협연의 밤"))
                .isFalse();
    }

    @Test
    @DisplayName("기준은 6자 — 5자는 일부러 놓친다")
    void 경계() {
        assertThat(PerformanceDuplicateMatcher.commonLength("가나다라마바사", "가나다라마바")).isEqualTo(6);
        assertThat(PerformanceDuplicateMatcher.sameEvent("가나다라마바사", "가나다라마바")).isTrue();
        assertThat(PerformanceDuplicateMatcher.sameEvent("가나다라마사", "가나다라마바")).isFalse();
    }

    @Test
    @DisplayName("5자에서 끊기는 진짜 중복은 일부러 놓친다 — 섞이는 구간을 피한 값이다")
    void 놓치는_것() {
        // 공통 토막이 "헤이스트링" 5자뿐이라 둘 다 보인다. 같은 5자 구간에 다른 행사가
        // 3쌍 섞여 있어(위 테스트), 이 한 쌍을 얻으려 기준을 내리지 않았다.
        assertThat(PerformanceDuplicateMatcher.sameEvent(
                "헤이스트링, 서큘레이터 Circulator",
                "[서울남산국악당] 헤이스트링 [서큘레이터]"))
                .isFalse();
    }

    @Test
    @DisplayName("비교할 제목이 없으면 하나로 보지 않는다")
    void 빈_제목() {
        assertThat(PerformanceDuplicateMatcher.sameEvent(null, "대한민국국악관현악축제")).isFalse();
        assertThat(PerformanceDuplicateMatcher.sameEvent("[세종문화회관]", "[세종문화회관]")).isFalse();
    }

    @Test
    @DisplayName("연도만 다른 같은 행사를 같다고 보지 않게, 연도는 떼되 기간으로 가른다")
    void 연도_제거는_기간_조건과_함께() {
        // 연도를 떼므로 2025년과 2026년 같은 축제는 제목만으로는 같아진다.
        // 그래서 PerformanceDeduper 가 시작일·종료일이 같을 때만 이 판정을 묻는다.
        assertThat(PerformanceDuplicateMatcher.normalize("2025 대한민국국악관현악축제"))
                .isEqualTo(PerformanceDuplicateMatcher.normalize("2026 대한민국국악관현악축제"));
    }
}
