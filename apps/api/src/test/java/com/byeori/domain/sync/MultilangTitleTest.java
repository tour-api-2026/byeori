package com.byeori.domain.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 공사 다국어판을 국문판 장소에 잇는 규칙.
 *
 * 아래 제목은 전부 실제 응답에서 가져온 것이다. contentId 가 언어마다 달라 장소를 직접
 * 맞춰야 하는데, 틀리면 엉뚱한 장소에 엉뚱한 이름과 설명이 붙는다 — 조용히 틀리는 종류라
 * 규칙을 여기 고정한다.
 */
class MultilangTitleTest {

    @Test
    @DisplayName("괄호 안 한국어 원문을 뽑는다")
    void 원문_추출() {
        assertThat(MultilangTitle.koreanPart("Gyeongbokgung Palace (경복궁)")).isEqualTo("경복궁");
        assertThat(MultilangTitle.koreanPart("10 Corso Como Cheongdam Branch (10꼬르소꼬모 청담점)"))
                .isEqualTo("10꼬르소꼬모 청담점");
        assertThat(MultilangTitle.koreanPart("100 Years Night Night Market (백년나이트 야시장)"))
                .isEqualTo("백년나이트 야시장");
    }

    /**
     * 괄호가 여럿이면 뒤의 것이 원문이다. 'Tax Refund Shop' 표시가 앞에 붙어 온다.
     *
     * 중첩된 괄호는 **일부러 포기한다.** '(사공일구이(40192))' 는 안쪽 '(40192)' 가 잡혀
     * 한글이 없으므로 빈 값이 된다. 바깥까지 억지로 뜯어 맞추면 '사공일구이(40192' 같은
     * 반쪽 이름이 나와 엉뚱한 장소에 붙을 수 있다. 빈 값이면 좌표로만 맞추는 길로 떨어지고,
     * 그쪽은 후보가 딱 하나일 때만 잇는다 — 못 맞추는 편이 잘못 맞추는 것보다 낫다.
     */
    @Test
    @DisplayName("괄호가 여러 개면 가장 뒤의 한국어를 쓰고, 중첩이면 포기한다")
    void 괄호가_여럿() {
        assertThat(MultilangTitle.koreanPart("Mukyo-dong Bukchangdong Area (무교동·북창동 일대)"))
                .isEqualTo("무교동·북창동 일대");
        assertThat(MultilangTitle.koreanPart("40192 [Tax Refund Shop](사공일구이(40192))")).isEmpty();
        assertThat(MultilangTitle.koreanPart("APLSHOP [Tax Refund Shop](APLSHOP)")).isEmpty();
    }

    @Test
    @DisplayName("한국어가 없으면 빈 값 — 그 행은 좌표로만 맞춘다")
    void 원문이_없음() {
        assertThat(MultilangTitle.koreanPart("Quay Cafe")).isEmpty();
        assertThat(MultilangTitle.koreanPart("")).isEmpty();
        assertThat(MultilangTitle.koreanPart(null)).isEmpty();
        assertThat(MultilangTitle.koreanPart("Jeju Olle Trail")).isEmpty();
    }

    /**
     * 이 테스트가 규칙의 핵심이다. 이름만으로 이으면 아래 같은 동명이소 147건이 잘못 붙는다.
     * 좌표는 전부 운영 데이터에서 가져온 실제 값이다.
     */
    @Test
    @DisplayName("이름이 같아도 멀면 다른 장소다")
    void 동명이소를_가른다() {
        // 송정해수욕장 — 부산(35.1787,129.1997) vs 강원(37.7769,128.9379)
        assertThat(MultilangTitle.nearby(35.1787, 129.1997, 37.7769, 128.9379)).isFalse();
        // 삼각산 — (37.4706,127.7093) vs (37.6582,126.9909)
        assertThat(MultilangTitle.nearby(37.4706, 127.7093, 37.6582, 126.9909)).isFalse();
        // 삼학도 — (37.4854,127.0170) vs 목포(34.7821,126.3914)
        assertThat(MultilangTitle.nearby(37.4854, 127.0170, 34.7821, 126.3914)).isFalse();
    }

    /**
     * 국문판과 다국어판은 따로 관리돼 같은 장소도 좌표가 조금씩 다르다. 완전일치만 요구하면
     * 맞는 짝까지 버린다(영문 3,803 → 1,826).
     */
    @Test
    @DisplayName("같은 장소는 좌표가 조금 달라도 잇는다")
    void 같은_장소는_잇는다() {
        // 경복궁 — 국문과 영문이 소수점까지 같은 경우
        assertThat(MultilangTitle.nearby(37.57603, 126.97672, 37.57603, 126.97672)).isTrue();
        // 수백 미터 차이는 같은 장소로 본다
        assertThat(MultilangTitle.nearby(37.5760, 126.9767, 37.5800, 126.9800)).isTrue();
        // 경계 바로 안쪽
        assertThat(MultilangTitle.nearby(37.0000, 127.0000, 37.0089, 127.0109)).isTrue();
        // 경계 바로 바깥
        assertThat(MultilangTitle.nearby(37.0000, 127.0000, 37.0091, 127.0000)).isFalse();
        assertThat(MultilangTitle.nearby(37.0000, 127.0000, 37.0000, 127.0111)).isFalse();
    }
}
