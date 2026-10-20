package com.byeori.domain.translation;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TranslationBatchTest {

    @Test
    @DisplayName("같은 이름은 같은 해시")
    void 같은_이름() {
        assertThat(TranslationBatch.hashOf("경복궁")).isEqualTo(TranslationBatch.hashOf("경복궁"));
    }

    @Test
    @DisplayName("이름이 한 글자만 달라도 해시가 다르다")
    void 다른_이름() {
        assertThat(TranslationBatch.hashOf("경복궁")).isNotEqualTo(TranslationBatch.hashOf("경복궁 "));
    }

    @Test
    @DisplayName("해시는 64자 16진수다 — 컬럼이 VARCHAR(64) 다")
    void 길이() {
        assertThat(TranslationBatch.hashOf("경복궁")).hasSize(64).matches("[0-9a-f]{64}");
    }
}
