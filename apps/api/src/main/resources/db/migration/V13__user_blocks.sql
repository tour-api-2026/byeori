-- 사용자 차단 (구글 Play UGC 정책: 사용자/콘텐츠 차단 수단이 필요)
-- 단방향 — user_id가 blocked_user_id를 차단한다. 차단하면 상대의 리뷰가 목록에서 빠진다.
CREATE TABLE user_blocks (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         bigint NOT NULL REFERENCES users(id),
    blocked_user_id bigint NOT NULL REFERENCES users(id),
    created_at      timestamp NOT NULL DEFAULT now(),
    CONSTRAINT ck_user_blocks_not_self CHECK (user_id <> blocked_user_id)
);

CREATE UNIQUE INDEX ux_user_blocks ON user_blocks (user_id, blocked_user_id);
-- 리뷰 목록 조회 시 "내가 차단한 사용자" 를 매번 읽는다
CREATE INDEX ix_user_blocks_user ON user_blocks (user_id);
