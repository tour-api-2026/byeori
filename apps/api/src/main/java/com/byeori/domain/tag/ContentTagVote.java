package com.byeori.domain.tag;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "content_tag_votes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTagVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long commentTagId;
    private Long userId;
    private Long performanceId;
    private Long venueId;
    private LocalDateTime createdAt;

    public ContentTagVote(Long commentTagId, Long userId, Long performanceId, Long venueId) {
        this.commentTagId = commentTagId;
        this.userId = userId;
        this.performanceId = performanceId;
        this.venueId = venueId;
        this.createdAt = LocalDateTime.now();
    }
}
