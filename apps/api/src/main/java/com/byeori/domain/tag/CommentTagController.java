package com.byeori.domain.tag;

import com.byeori.domain.tag.dto.CommentTagResponse;
import com.byeori.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/comment-tags")
public class CommentTagController {

    private final CommentTagRepository repo;

    public CommentTagController(CommentTagRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ApiResponse<List<CommentTagResponse>> list() {
        List<CommentTagResponse> tags = repo.findByStatusOrderByIdAsc("ACTIVE").stream()
                .map(CommentTagResponse::from).toList();
        return ApiResponse.ok(tags);
    }
}
