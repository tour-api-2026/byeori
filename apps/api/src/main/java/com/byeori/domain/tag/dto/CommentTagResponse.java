package com.byeori.domain.tag.dto;

import com.byeori.domain.tag.CommentTag;

public record CommentTagResponse(Long id, String name) {
    public static CommentTagResponse from(CommentTag t) {
        return new CommentTagResponse(t.getId(), t.getName());
    }
}
