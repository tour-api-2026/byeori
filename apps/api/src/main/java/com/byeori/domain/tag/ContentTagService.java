package com.byeori.domain.tag;

import com.byeori.domain.tag.dto.ContentTagCountResponse;
import com.byeori.domain.tag.dto.VoteRequest;
import com.byeori.global.content.ContentTarget;
import com.byeori.global.content.ContentType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContentTagService {

    private final CommentTagRepository tagRepo;
    private final ContentTagVoteRepository voteRepo;

    public ContentTagService(CommentTagRepository tagRepo, ContentTagVoteRepository voteRepo) {
        this.tagRepo = tagRepo;
        this.voteRepo = voteRepo;
    }

    public List<ContentTagCountResponse> aggregate(Long userId, String targetType, Long targetId) {
        ContentTarget t = new ContentTarget(ContentType.from(targetType), targetId);
        boolean venue = t.targetType() == ContentType.VENUE;

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : (venue ? voteRepo.aggByVenue(targetId) : voteRepo.aggByPerformance(targetId))) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        Set<Long> voted = Set.copyOf(venue ? voteRepo.votedVenue(userId, targetId) : voteRepo.votedPerformance(userId, targetId));

        return tagRepo.findByStatusOrderByIdAsc("ACTIVE").stream()
                .map(tag -> new ContentTagCountResponse(
                        tag.getId(), tag.getName(),
                        counts.getOrDefault(tag.getId(), 0L),
                        voted.contains(tag.getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public void vote(Long userId, VoteRequest req) {
        ContentTarget t = new ContentTarget(ContentType.from(req.targetType()), req.targetId());
        boolean exists = t.targetType() == ContentType.VENUE
                ? voteRepo.findByCommentTagIdAndUserIdAndVenueId(req.commentTagId(), userId, req.targetId()).isPresent()
                : voteRepo.findByCommentTagIdAndUserIdAndPerformanceId(req.commentTagId(), userId, req.targetId()).isPresent();
        if (!exists) {
            voteRepo.save(new ContentTagVote(req.commentTagId(), userId, t.performanceId(), t.venueId()));
        }
    }

    @Transactional
    public void unvote(Long userId, VoteRequest req) {
        ContentTarget t = new ContentTarget(ContentType.from(req.targetType()), req.targetId());
        var existing = t.targetType() == ContentType.VENUE
                ? voteRepo.findByCommentTagIdAndUserIdAndVenueId(req.commentTagId(), userId, req.targetId())
                : voteRepo.findByCommentTagIdAndUserIdAndPerformanceId(req.commentTagId(), userId, req.targetId());
        existing.ifPresent(voteRepo::delete);
    }
}
