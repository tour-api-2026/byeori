package com.byeori.domain.sync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TraditionalTaggerTest {

    @Test
    void 국악_장르는_전통() {
        assertTrue(TraditionalTagger.isTraditional("가곡의 밤", "국악"));
    }

    @Test
    void 제목_키워드로_전통_판정() {
        assertTrue(TraditionalTagger.isTraditional("경복궁 별빛야행", "축제"));
        assertTrue(TraditionalTagger.isTraditional("전주 한옥마을 축제", "축제"));
        assertTrue(TraditionalTagger.isTraditional("안동 탈춤 페스티벌", "축제"));
        assertTrue(TraditionalTagger.isTraditional("판소리 완창 무대", "연극"));
        assertTrue(TraditionalTagger.isTraditional("무형유산 공개행사", null));
    }

    @Test
    void 비전통_행사는_거짓() {
        assertFalse(TraditionalTagger.isTraditional("서울 재즈 페스티벌", "콘서트"));
        assertFalse(TraditionalTagger.isTraditional("뮤지컬 캣츠", "뮤지컬"));
        assertFalse(TraditionalTagger.isTraditional(null, null));
    }
}
