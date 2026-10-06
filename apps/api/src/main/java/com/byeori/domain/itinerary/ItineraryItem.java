package com.byeori.domain.itinerary;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "itinerary_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long itineraryId;
    private Long performanceId;
    private Long venueId;
    private LocalDate visitDate;
    private Integer sortOrder;
    private String plannedTime;

    @Column(columnDefinition = "text")
    private String memo;

    private LocalDateTime createdAt;

    public ItineraryItem(Long itineraryId, Long performanceId, Long venueId, LocalDate visitDate,
                         Integer sortOrder, String plannedTime, String memo) {
        this.itineraryId = itineraryId;
        this.performanceId = performanceId;
        this.venueId = venueId;
        this.visitDate = visitDate;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
        this.plannedTime = plannedTime;
        this.memo = memo;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 이 자리의 장소를 다른 곳으로 바꾼다.
     *
     * 메모를 지운다. AI 루트는 추천 이유를 메모로 적어 두는데, 장소가 바뀌면 그 글은
     * 이제 없는 장소를 설명하는 말이 된다. 남겨 두면 "왜 여길 추천했지" 하고 읽게 된다.
     */
    public void changeTarget(Long performanceId, Long venueId) {
        this.performanceId = performanceId;
        this.venueId = venueId;
        this.memo = null;
    }

    /**
     * 보낸 값만 바꾼다.
     *
     * 빈 문자열은 **지워 달라**는 뜻이다. JSON 의 null 하나로는 '안 건드림'과 '지움'을
     * 가릴 수 없어서, 그동안 '미정'을 눌러도 시간이 지워지지 않았다 — 넣을 수는 있어도
     * 되돌릴 수가 없었다.
     */
    public void update(LocalDate visitDate, Integer sortOrder, String plannedTime, String memo) {
        if (visitDate != null) this.visitDate = visitDate;
        if (sortOrder != null) this.sortOrder = sortOrder;
        if (plannedTime != null) this.plannedTime = plannedTime.isBlank() ? null : plannedTime;
        if (memo != null) this.memo = memo.isBlank() ? null : memo;
    }
}
