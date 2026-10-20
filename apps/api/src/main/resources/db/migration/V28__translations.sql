-- 해시 비교를 SQL 로 하려면 digest() 가 필요하다. 번역 대상을 고르는 쿼리가 쓴다.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 장소·행사 이름의 번역본. 사용자 요청 경로에서 읽기만 하고, 쓰기는 동기화 뒤 배치뿐이다.
--
-- name_hash 는 원본 이름의 sha256 이다. 공사가 이름을 고쳤을 때만 다시 번역하려고 둔다 —
-- 이것이 없으면 매일 전량을 다시 번역해 비용이 수백 배가 된다.
--
-- tr_source 는 번역을 어디서 얻었는지다(AI | TOURAPI). 지금은 전부 AI 지만, 공사
-- 다국어 서비스를 쓸 수 있게 되면 매칭되는 것만 골라 덮어쓰기 위해 남긴다.
ALTER TABLE venues
    ADD COLUMN name_en   VARCHAR(255),
    ADD COLUMN name_ja   VARCHAR(255),
    ADD COLUMN name_zh   VARCHAR(255),
    ADD COLUMN desc_en   TEXT,
    ADD COLUMN desc_ja   TEXT,
    ADD COLUMN desc_zh   TEXT,
    ADD COLUMN name_hash CHAR(64),
    ADD COLUMN tr_source VARCHAR(10);

ALTER TABLE performances
    ADD COLUMN title_en  VARCHAR(255),
    ADD COLUMN title_ja  VARCHAR(255),
    ADD COLUMN title_zh  VARCHAR(255),
    ADD COLUMN name_hash CHAR(64),
    ADD COLUMN tr_source VARCHAR(10);

-- 번역 대상을 고르는 조건: name_hash IS NULL OR name_hash <> sha256(name).
-- 34,728 + 12,633 행을 매일 훑으므로 인덱스를 둔다.
CREATE INDEX idx_venues_name_hash       ON venues (name_hash);
CREATE INDEX idx_performances_name_hash ON performances (name_hash);
