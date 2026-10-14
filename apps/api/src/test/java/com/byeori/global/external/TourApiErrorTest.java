package com.byeori.global.external;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 동기화 실패 사유를 꺼낸다.
 *
 * 장소 증분 동기화가 2026-09-21 부터 24일간 매일 실패했는데 아무도 몰랐다. 기록에 남은
 * 것이 "동기화 목록 조회 실패" 한 줄이어서다. 사유는 응답 본문에 또박또박 적혀 있었다.
 */
class TourApiErrorTest {

    /** 2026-10-14 에 실제로 받은 응답 그대로. */
    private static final String REAL_403 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <OpenAPI_ServiceResponse>
            <cmmMsgHeader>
              <errMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</errMsg>
              <returnAuthMsg>등록되지 않은 서비스키</returnAuthMsg>
              <returnReasonCode>30</returnReasonCode>
            </cmmMsgHeader>
            </OpenAPI_ServiceResponse>""";

    @Test
    @DisplayName("응답 본문에서 사유를 꺼낸다")
    void 본문에서_꺼낸다() {
        assertThat(TourApiError.reasonOf(REAL_403)).isEqualTo("등록되지 않은 서비스키");
    }

    @Test
    @DisplayName("감싸인 예외 사슬을 끝까지 따라간다 — 감싸는 과정에서 사유가 버려지고 있었다")
    void 예외_사슬() {
        // RestClient 는 4xx 본문을 예외 메시지에 실어 준다. 그것이 두 겹 감싸인 모양.
        Throwable http = new RuntimeException("403 Forbidden: \"" + REAL_403 + "\"");
        Throwable wrapped = new IllegalStateException("동기화 목록 조회 실패", http);

        assertThat(TourApiError.reasonOf(wrapped)).isEqualTo("등록되지 않은 서비스키");
    }

    @Test
    @DisplayName("사유 태그가 없으면 맨 아래 예외의 말이라도 남긴다")
    void 사유가_없으면() {
        Throwable root = new java.net.SocketTimeoutException("Read timed out");
        assertThat(TourApiError.reasonOf(new IllegalStateException("동기화 목록 조회 실패", root)))
                .isEqualTo("Read timed out");
    }

    @Test
    @DisplayName("사람이 손대야 하는 사유와 저절로 낫는 사유를 가른다")
    void 사람이_손댈_일인가() {
        assertThat(TourApiError.needsHuman("등록되지 않은 서비스키")).isTrue();
        assertThat(TourApiError.needsHuman("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")).isTrue();
        assertThat(TourApiError.needsHuman("서비스 접근 거부")).isTrue();
        // 타임아웃·일시 장애는 다음 회차에 저절로 낫는다. 오류로 올리면 늑대 소년이 된다.
        assertThat(TourApiError.needsHuman("Read timed out")).isFalse();
        assertThat(TourApiError.needsHuman("Connection reset")).isFalse();
        assertThat(TourApiError.needsHuman(null)).isFalse();
    }
}
