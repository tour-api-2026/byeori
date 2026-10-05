package com.byeori.domain.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 행사 상태는 조회 시점의 날짜로 계산해야 한다.
 *
 * 예전에는 동기화할 때 계산해 컬럼에 넣어 두었는데, 저장된 뒤로는 아무도 다시 계산하지 않아
 * '진행 중' 목록 200건 중 192건이 이미 끝난 행사였다(2026-10-05 확인, 최대 59일 경과).
 * 판정 규칙 자체는 그대로 두고 기준 날짜만 '오늘'로 바꾼다.
 */
class PerformanceStateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);

    private String state(String start, String end) {
        return Performance.stateOn(
                start == null ? null : LocalDate.parse(start),
                end == null ? null : LocalDate.parse(end),
                TODAY);
    }

    @Test
    @DisplayName("끝난 행사는 ENDED — 이번 버그의 핵심")
    void 끝난_행사() {
        assertThat(state("2026-08-07", "2026-08-07")).isEqualTo("ENDED");
        assertThat(state("2026-09-01", "2026-10-04")).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("오늘이 기간 안이면 ONGOING — 경계 포함")
    void 진행중() {
        assertThat(state("2026-10-01", "2026-10-31")).isEqualTo("ONGOING");
        assertThat(state("2026-10-05", "2026-10-05")).isEqualTo("ONGOING");
        assertThat(state("2026-10-05", "2026-12-01")).isEqualTo("ONGOING");
        assertThat(state("2026-01-01", "2026-10-05")).isEqualTo("ONGOING");
    }

    @Test
    @DisplayName("아직 시작 전이면 UPCOMING")
    void 예정() {
        assertThat(state("2026-10-06", "2026-10-10")).isEqualTo("UPCOMING");
        assertThat(state("2026-12-26", "2026-12-26")).isEqualTo("UPCOMING");
    }

    @Test
    @DisplayName("기간 정보가 비면 UPCOMING — 기존 규칙을 그대로 둔다")
    void 기간_없음() {
        assertThat(state(null, null)).isEqualTo("UPCOMING");
        assertThat(state("2026-01-01", null)).isEqualTo("UPCOMING");
        assertThat(state(null, "2026-12-31")).isEqualTo("UPCOMING");
    }

    @Test
    @DisplayName("종료일만 있고 지났으면 ENDED")
    void 종료일만_지남() {
        assertThat(state(null, "2026-10-04")).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("날짜가 바뀌면 같은 행사의 상태도 바뀐다 — 저장값이면 생기지 않는 성질")
    void 날짜에_따라_변한다() {
        LocalDate s = LocalDate.of(2026, 10, 1), e = LocalDate.of(2026, 10, 31);
        assertThat(Performance.stateOn(s, e, LocalDate.of(2026, 9, 30))).isEqualTo("UPCOMING");
        assertThat(Performance.stateOn(s, e, LocalDate.of(2026, 10, 15))).isEqualTo("ONGOING");
        assertThat(Performance.stateOn(s, e, LocalDate.of(2026, 11, 1))).isEqualTo("ENDED");
    }
}
