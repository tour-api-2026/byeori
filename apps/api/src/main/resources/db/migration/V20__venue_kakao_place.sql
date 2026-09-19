-- 루트에 벼리 DB에 없는 식당·카페를 직접 넣을 수 있게 한다(카카오 로컬 검색).
-- 고른 곳은 그 사용자만 보는 장소(visibility = 'PRIVATE', source = 'KAKAO')로 저장한다.
-- 같은 사용자가 같은 곳을 다시 고르면 새로 만들지 않고 재사용하려고 카카오 장소 ID를 둔다.
ALTER TABLE venues ADD COLUMN kakao_place_id VARCHAR(30);
CREATE INDEX idx_venues_owner_kakao ON venues (created_by_user_id, kakao_place_id)
    WHERE kakao_place_id IS NOT NULL;
