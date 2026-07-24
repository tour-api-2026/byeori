-- 사용자 업로드 이미지(장소 등록 사진 등)를 DB(BYTEA)에 보관.
-- 별도 스토리지/볼륨 없이 postgres 볼륨에 영구 저장된다.
CREATE TABLE uploaded_image (
    id           VARCHAR(40)  PRIMARY KEY,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   INTEGER      NOT NULL,
    data         BYTEA        NOT NULL,
    uploader_id  BIGINT,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);
