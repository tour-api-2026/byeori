package com.byeori.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.byeori.domain.performance.PerformanceRepository;
import com.byeori.domain.review.dto.ReviewReportRequest;
import com.byeori.domain.venue.VenueRepository;
import com.byeori.global.exception.BadRequestException;
import com.byeori.global.exception.NotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** 리뷰 신고 — 구글 UGC 정책이 요구하는 신고 수단. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewReportTest {

    private static final Long REPORTER = 1L;
    private static final Long AUTHOR = 2L;
    private static final Long REVIEW_ID = 100L;

    @Mock ReviewRepository repo;
    @Mock VenueRepository venueRepo;
    @Mock PerformanceRepository performanceRepo;
    @Mock ReviewReportRepository reportRepo;

    ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(repo, venueRepo, performanceRepo, reportRepo);
        // 신고 대상: AUTHOR가 쓴 리뷰
        Review review = new Review(AUTHOR, null, 10L, 5, "내용");
        when(repo.findById(REVIEW_ID)).thenReturn(Optional.of(review));
        when(reportRepo.existsByReviewIdAndUserId(anyLong(), anyLong())).thenReturn(false);
    }

    @Test
    void 신고가_PENDING_상태로_저장된다() {
        service.report(REPORTER, REVIEW_ID, new ReviewReportRequest("SPAM", "광고성 글입니다"));

        ArgumentCaptor<ReviewReport> captor = ArgumentCaptor.forClass(ReviewReport.class);
        verify(reportRepo).save(captor.capture());
        ReviewReport saved = captor.getValue();
        assertThat(saved.getReviewId()).isEqualTo(REVIEW_ID);
        assertThat(saved.getUserId()).isEqualTo(REPORTER);
        assertThat(saved.getReason()).isEqualTo("SPAM");
        assertThat(saved.getDetail()).isEqualTo("광고성 글입니다");
        assertThat(saved.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void 사유가_없으면_거부() {
        assertThatThrownBy(() -> service.report(REPORTER, REVIEW_ID, new ReviewReportRequest(null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.report(REPORTER, REVIEW_ID, new ReviewReportRequest("  ", null)))
                .isInstanceOf(BadRequestException.class);
        verify(reportRepo, never()).save(any());
    }

    @Test
    void 사유가_50자를_넘으면_거부() {
        // reason 컬럼이 varchar(50) — 넘기면 DB에서 500이 터지므로 앞단에서 막는다
        String tooLong = "가".repeat(51);
        assertThatThrownBy(() -> service.report(REPORTER, REVIEW_ID, new ReviewReportRequest(tooLong, null)))
                .isInstanceOf(BadRequestException.class);
        verify(reportRepo, never()).save(any());
    }

    @Test
    void 없는_리뷰는_404() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.report(REPORTER, 999L, new ReviewReportRequest("SPAM", null)))
                .isInstanceOf(NotFoundException.class);
        verify(reportRepo, never()).save(any());
    }

    @Test
    void 본인_리뷰는_신고할_수_없다() {
        assertThatThrownBy(() -> service.report(AUTHOR, REVIEW_ID, new ReviewReportRequest("SPAM", null)))
                .isInstanceOf(BadRequestException.class);
        verify(reportRepo, never()).save(any());
    }

    @Test
    void 같은_리뷰를_두_번_신고할_수_없다() {
        when(reportRepo.existsByReviewIdAndUserId(REVIEW_ID, REPORTER)).thenReturn(true);

        assertThatThrownBy(() -> service.report(REPORTER, REVIEW_ID, new ReviewReportRequest("SPAM", null)))
                .isInstanceOf(BadRequestException.class);
        verify(reportRepo, never()).save(any());
    }
}
