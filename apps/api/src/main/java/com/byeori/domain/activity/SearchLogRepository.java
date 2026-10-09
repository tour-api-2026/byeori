package com.byeori.domain.activity;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    @Modifying
    @Query("delete from SearchLog s where s.searchedAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
