package com.byeori.domain.itinerary;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {
    List<Itinerary> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 공유 링크로 들어온 조회. 소유자를 묻지 않으므로 토큰이 곧 열쇠다. */
    Optional<Itinerary> findByShareToken(String shareToken);
}
