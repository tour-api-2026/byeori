package com.byeori.domain.itinerary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {
    List<Itinerary> findByUserIdOrderByCreatedAtDesc(Long userId);
}
