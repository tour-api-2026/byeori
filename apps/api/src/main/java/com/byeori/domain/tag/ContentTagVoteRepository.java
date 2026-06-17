package com.byeori.domain.tag;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentTagVoteRepository extends JpaRepository<ContentTagVote, Long> {

    @Query("select v.commentTagId, count(v) from ContentTagVote v where v.venueId = :id group by v.commentTagId")
    List<Object[]> aggByVenue(@Param("id") Long id);

    @Query("select v.commentTagId, count(v) from ContentTagVote v where v.performanceId = :id group by v.commentTagId")
    List<Object[]> aggByPerformance(@Param("id") Long id);

    @Query("select v.commentTagId from ContentTagVote v where v.userId = :userId and v.venueId = :id")
    List<Long> votedVenue(@Param("userId") Long userId, @Param("id") Long id);

    @Query("select v.commentTagId from ContentTagVote v where v.userId = :userId and v.performanceId = :id")
    List<Long> votedPerformance(@Param("userId") Long userId, @Param("id") Long id);

    Optional<ContentTagVote> findByCommentTagIdAndUserIdAndVenueId(Long commentTagId, Long userId, Long venueId);
    Optional<ContentTagVote> findByCommentTagIdAndUserIdAndPerformanceId(Long commentTagId, Long userId, Long performanceId);
}
