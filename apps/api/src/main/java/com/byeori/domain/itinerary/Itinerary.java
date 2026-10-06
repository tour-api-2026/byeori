package com.byeori.domain.itinerary;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "itineraries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Itinerary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String title;
    private LocalDate startDate;
    private LocalDate endDate;
    private String sourceType;
    private Long sourceCourseId;

    /** 공유 링크 토큰. null 이면 아직 공유한 적이 없다. */
    private String shareToken;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Itinerary(Long userId, String title, LocalDate startDate, LocalDate endDate,
                     String sourceType, Long sourceCourseId) {
        this.userId = userId;
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.sourceType = sourceType;
        this.sourceCourseId = sourceCourseId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * 공유 토큰을 처음 한 번만 심는다.
     *
     * 이미 있으면 그대로 둔다 — 공유 버튼을 다시 눌렀다고 토큰이 바뀌면, 먼저 보낸 링크가
     * 소리 없이 죽어 받은 쪽은 왜 안 열리는지 알 수 없다.
     */
    public void shareWith(String token) {
        if (this.shareToken != null) return;
        this.shareToken = token;
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String title, LocalDate startDate, LocalDate endDate) {
        if (title != null) this.title = title;
        if (startDate != null) this.startDate = startDate;
        if (endDate != null) this.endDate = endDate;
        this.updatedAt = LocalDateTime.now();
    }
}
