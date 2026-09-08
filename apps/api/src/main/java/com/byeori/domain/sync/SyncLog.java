package com.byeori.domain.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 동기화 실행 기록.
 *
 * 증분 동기화의 커서 역할을 겸한다. 다음 회차는 "마지막 성공 실행일"부터 받으므로,
 * 실패한 회차가 있으면 커서가 전진하지 않아 빠진 구간을 다음 회차가 함께 가져간다.
 * 공사에서 호출 이력을 확인할 때의 근거 자료이기도 하다.
 */
@Entity
@Table(name = "sync_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncLog {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "processed_cnt", nullable = false)
    private int processedCnt;

    @Column(nullable = false)
    private String status;

    private String message;

    public static SyncLog started(String provider, String targetType) {
        SyncLog s = new SyncLog();
        s.provider = provider;
        s.targetType = targetType;
        s.startedAt = LocalDateTime.now();
        s.status = "RUNNING";
        return s;
    }

    public void succeeded(int processed, String message) {
        this.status = SUCCESS;
        this.processedCnt = processed;
        this.message = message;
        this.finishedAt = LocalDateTime.now();
    }

    public void failed(String message) {
        this.status = FAILED;
        // 실패해도 그때까지 반영된 건수는 남긴다(어디까지 갔는지 알아야 한다).
        this.message = message == null ? null : message.substring(0, Math.min(message.length(), 500));
        this.finishedAt = LocalDateTime.now();
    }

    public void progressed(int processed) {
        this.processedCnt = processed;
    }
}
