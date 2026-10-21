package com.byeori.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.byeori.global.i18n.Lang;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 루트는 번역이 아니라 처음부터 그 언어로 만든다. 단 장소 이름은 한국어로 남는다. */
class AiRoutePromptLangTest {

    @Test
    @DisplayName("프롬프트가 요청한 언어를 지시한다")
    void 언어_지시() {
        assertThat(AiRouteService.systemPromptNew(Lang.JA)).contains("Japanese");
        assertThat(AiRouteService.systemPromptNew(Lang.EN)).contains("English");
        assertThat(AiRouteService.systemPromptNew(Lang.ZH)).contains("Simplified Chinese");
        assertThat(AiRouteService.systemPromptNew(Lang.KO)).contains("한국어");
    }

    @Test
    @DisplayName("장소 이름은 한국어 원본을 유지하라고 지시한다")
    void 장소명_유지() {
        // 현장에서 간판을 맞추려면 장소 이름은 한국어가 남아야 한다
        for (Lang l : new Lang[] {Lang.EN, Lang.JA, Lang.ZH}) {
            assertThat(AiRouteService.systemPromptNew(l)).contains("keep the Korean place names");
        }
    }

    @Test
    @DisplayName("기존 지시는 그대로 두고 끝에만 붙는다 — 3일 요청이 1일차만 나오던 버그의 문구")
    void 기존_지시_유지() {
        String ko = AiRouteService.systemPromptNew(Lang.KO);
        String ja = AiRouteService.systemPromptNew(Lang.JA);
        assertThat(ja).contains("[며칠짜리인지]").contains("틀에 있는 모든 칸을 빠짐없이 채운다");
        String base = ko.substring(0, ko.indexOf("\n\n[언어]"));
        assertThat(ja).startsWith(base);
    }
}
