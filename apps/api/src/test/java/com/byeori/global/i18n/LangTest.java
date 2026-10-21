package com.byeori.global.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LangTest {

    @Test
    @DisplayName("아는 네 값을 받는다")
    void 아는_값() {
        assertThat(Lang.of("ko")).isEqualTo(Lang.KO);
        assertThat(Lang.of("en")).isEqualTo(Lang.EN);
        assertThat(Lang.of("ja")).isEqualTo(Lang.JA);
        assertThat(Lang.of("zh")).isEqualTo(Lang.ZH);
    }

    @Test
    @DisplayName("대소문자를 가리지 않는다")
    void 대소문자() {
        assertThat(Lang.of("EN")).isEqualTo(Lang.EN);
    }

    @Test
    @DisplayName("모르는 값·빈 값·null 은 한국어로 본다")
    void 모르는_값() {
        assertThat(Lang.of("de")).isEqualTo(Lang.KO);
        assertThat(Lang.of("zh-Hans-CN")).isEqualTo(Lang.KO);
        assertThat(Lang.of("")).isEqualTo(Lang.KO);
        assertThat(Lang.of(null)).isEqualTo(Lang.KO);
    }

    @Test
    @DisplayName("브라우저가 보내는 긴 헤더도 터지지 않고 한국어로 떨어진다")
    void 긴_헤더() {
        assertThat(Lang.of("ko-KR,ko;q=0.9,en-US;q=0.8")).isEqualTo(Lang.KO);
    }
}
