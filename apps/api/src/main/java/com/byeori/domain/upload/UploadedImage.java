package com.byeori.domain.upload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 업로드 이미지. 바이트는 Postgres BYTEA 컬럼에 그대로 저장한다. */
@Entity
@Table(name = "uploaded_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedImage {

    @Id
    private String id;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private int sizeBytes;

    // @Lob 를 쓰면 Postgres에서 OID(large object)로 매핑돼 BYTEA와 어긋난다 → columnDefinition로 고정.
    @Column(name = "data", columnDefinition = "bytea")
    private byte[] data;

    @Column(name = "uploader_id")
    private Long uploaderId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public static UploadedImage of(String id, String contentType, byte[] data, Long uploaderId) {
        UploadedImage img = new UploadedImage();
        img.id = id;
        img.contentType = contentType;
        img.data = data;
        img.sizeBytes = data.length;
        img.uploaderId = uploaderId;
        img.createdAt = LocalDateTime.now();
        return img;
    }
}
