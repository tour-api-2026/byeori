package com.byeori.domain.wishlist;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wishlists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long performanceId;
    private Long venueId;
    private LocalDateTime createdAt;

    public Wishlist(Long userId, Long performanceId, Long venueId) {
        this.userId = userId;
        this.performanceId = performanceId;
        this.venueId = venueId;
        this.createdAt = LocalDateTime.now();
    }
}
