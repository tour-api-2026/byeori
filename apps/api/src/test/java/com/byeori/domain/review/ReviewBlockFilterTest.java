package com.byeori.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.review.dto.ReviewResponse;
import com.byeori.domain.user.UserBlockRepository;
import com.byeori.domain.venue.VenueRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 차단한 사용자의 리뷰는 목록에서 빠진다 — 차단이 실제로 콘텐츠를 가려야 의미가 있다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewBlockFilterTest {

    private static final Long VIEWER = 1L;
    private static final Long BLOCKED = 2L;
    private static final Long NORMAL = 3L;
    private static final Long VENUE_ID = 10L;

    @Mock ReviewRepository repo;
    @Mock VenueRepository venueRepo;
    @Mock PerformanceRepository performanceRepo;
    @Mock ReviewReportRepository reportRepo;
    @Mock UserBlockRepository blockRepo;

    ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(repo, venueRepo, performanceRepo, reportRepo, blockRepo);
        when(repo.findByVenueIdOrderByCreatedAtDesc(VENUE_ID)).thenReturn(List.of(
                new Review(BLOCKED, null, VENUE_ID, 1, "차단한 사용자의 리뷰"),
                new Review(NORMAL, null, VENUE_ID, 5, "정상 리뷰")));
    }

    @Test
    void 차단한_사용자의_리뷰는_제외된다() {
        when(blockRepo.findBlockedUserIds(VIEWER)).thenReturn(List.of(BLOCKED));

        List<ReviewResponse> result = service.listByTarget("VENUE", VENUE_ID, VIEWER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("정상 리뷰");
    }

    @Test
    void 차단이_없으면_모두_보인다() {
        when(blockRepo.findBlockedUserIds(VIEWER)).thenReturn(List.of());

        assertThat(service.listByTarget("VENUE", VENUE_ID, VIEWER)).hasSize(2);
    }

    @Test
    void 비로그인_조회는_차단_조회를_하지_않는다() {
        List<ReviewResponse> result = service.listByTarget("VENUE", VENUE_ID, null);

        assertThat(result).hasSize(2);
        verify(blockRepo, never()).findBlockedUserIds(anyLong());
    }
}
