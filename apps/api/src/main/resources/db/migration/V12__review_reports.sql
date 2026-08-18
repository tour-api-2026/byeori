-- 리뷰 신고 (구글 Play UGC 정책: 사용자 생성 콘텐츠에는 신고 수단이 필요)
-- venue_reports와 동일한 구조. 리뷰가 삭제되면 신고도 함께 정리한다.
CREATE TABLE review_reports (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    review_id  bigint NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    user_id    bigint NOT NULL REFERENCES users(id),
    reason     varchar(50) NOT NULL,
    detail     text,
    status     varchar(20) NOT NULL DEFAULT 'PENDING',         -- PENDING | REVIEWED | DISMISSED
    created_at timestamp NOT NULL DEFAULT now()
);

-- 같은 사용자가 같은 리뷰를 반복 신고하지 못하게(서비스 검증의 DB측 방어선)
CREATE UNIQUE INDEX ux_review_reports_review_user ON review_reports (review_id, user_id);
