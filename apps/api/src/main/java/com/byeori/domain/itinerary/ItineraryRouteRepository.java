package com.byeori.domain.itinerary;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRouteRepository
        extends JpaRepository<ItineraryRoute, ItineraryRoute.Key> {

    Optional<ItineraryRoute> findByItineraryIdAndMode(Long itineraryId, String mode);
}
