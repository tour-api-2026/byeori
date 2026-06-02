package com.byeori.domain.tag;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "comment_tags")
@Getter
public class CommentTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String status;
}
