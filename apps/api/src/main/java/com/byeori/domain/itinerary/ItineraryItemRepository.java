package com.byeori.domain.itinerary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, Long> {
    List<ItineraryItem> findByItineraryIdOrderByVisitDateAscSortOrderAsc(Long itineraryId);
    void deleteByItineraryId(Long itineraryId);
}
