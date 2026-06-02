package com.byeori.domain.venue.dto;

import java.math.BigDecimal;

public record VenueCreateRequest(
        String name, String address, BigDecimal lat, BigDecimal lng, String category,
        String phone, String homepageUrl, String operatingHours, String imageUrl, String description) {
}
