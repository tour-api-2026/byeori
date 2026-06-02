package com.byeori.domain.tag;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentTagRepository extends JpaRepository<CommentTag, Long> {
    List<CommentTag> findByStatusOrderByIdAsc(String status);
}
