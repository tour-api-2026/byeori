package com.byeori.domain.feed;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeedReasonTest {

    @Test
    @DisplayName("코드와 값을 나눠 담는다")
    void 코드와_값() {
        var r = FeedReason.favoriteTopic("문화");
        assertThat(r.code()).isEqualTo("FAVORITE_TOPIC");
        assertThat(r.param()).isEqualTo("문화");
    }

    @Test
    @DisplayName("값이 없는 이유는 param 이 null 이다")
    void 값_없음() {
        assertThat(FeedReason.ongoing().param()).isNull();
        assertThat(FeedReason.favoriteRegion().param()).isNull();
    }

    @Test
    @DisplayName("거리는 소수 한 자리 문자열로 넘긴다 — 서식은 언어마다 다르므로 앱이 만든다")
    void 거리() {
        assertThat(FeedReason.nearby(1.24).param()).isEqualTo("1.2");
    }

    @Test
    @DisplayName("며칠 뒤는 숫자만 넘긴다")
    void 며칠_뒤() {
        assertThat(FeedReason.startsInDays(3).param()).isEqualTo("3");
    }
}
