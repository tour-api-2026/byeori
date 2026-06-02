package com.byeori.domain.venue;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VenueRepository extends JpaRepository<Venue, Long> {

    @Query("""
            select v from Venue v
            where v.status = 'ACTIVE' and v.visibility = 'PUBLIC'
              and (:category is null or v.category = :category)
              and (:hanbokDiscount is null or v.hanbokDiscount = :hanbokDiscount)
              and (:keyword is null or v.name like %:keyword%)
            """)
    Page<Venue> search(@Param("category") String category,
                       @Param("hanbokDiscount") Boolean hanbokDiscount,
                       @Param("keyword") String keyword,
                       Pageable pageable);
}
