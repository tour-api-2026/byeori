package com.byeori.domain.course;

import com.byeori.global.i18n.Lang;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Entity
@Table(name = "curated_courses")
@Getter
public class CuratedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    // 큐레이션 코스는 우리가 쓴 글이라 사람이 번역한다(V29, scripts/import-translations.py 로 채운다).
    @Column(name = "title_en") private String titleEn;
    @Column(name = "title_ja") private String titleJa;
    @Column(name = "title_zh") private String titleZh;
    @Column(name = "desc_en", columnDefinition = "text") private String descEn;
    @Column(name = "desc_ja", columnDefinition = "text") private String descJa;
    @Column(name = "desc_zh", columnDefinition = "text") private String descZh;

    private String theme;
    private String coverImageUrl;
    private BigDecimal durationHours;
    private String status;
    private LocalDateTime createdAt;

    /** 선택한 언어의 제목. 번역이 없으면 한국어 원본. */
    public String titleIn(Lang lang) {
        String t = switch (lang) { case EN -> titleEn; case JA -> titleJa; case ZH -> titleZh; case KO -> null; };
        return (t == null || t.isBlank()) ? title : t;
    }

    /** 선택한 언어의 설명. 번역이 없으면 한국어 원본. */
    public String descIn(Lang lang) {
        String d = switch (lang) { case EN -> descEn; case JA -> descJa; case ZH -> descZh; case KO -> null; };
        return (d == null || d.isBlank()) ? description : d;
    }
}
