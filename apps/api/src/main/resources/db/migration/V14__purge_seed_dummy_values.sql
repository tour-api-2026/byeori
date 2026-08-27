-- V2__seed.sql 이 넣은 개발용 더미 값이 운영까지 따라와 사용자에게 노출되고 있었다.
--   · avg_rating/review_count: 근거 없는 숫자(경복궁 4.8/1320 등). 실제 reviews 테이블엔 2건뿐이라
--     사용자가 리뷰를 하나 쓰는 순간 ReviewService.recalc()가 실제값으로 덮어써 1320 → 1 로 급락한다.
--   · image_url/poster_image_url: picsum.photos 랜덤 스톡 이미지. 창경궁 자리에 콘크리트 건물 사진이
--     뜨는 식으로 실제 장소와 무관하다.
--
-- 시드 행 자체는 지우지 않는다. 사용자의 여행일정·즐겨찾기·리뷰가 이미 참조하고 있어
-- 삭제하면 그 데이터가 함께 날아간다. 값만 사실에 맞게 되돌린다.

-- 1) 무관한 스톡 이미지 제거. NULL이면 앱이 플레이스홀더를 보여준다 — 틀린 사진보다 낫다.
UPDATE venues       SET image_url        = NULL WHERE image_url        LIKE '%picsum.photos%';
UPDATE performances SET poster_image_url = NULL WHERE poster_image_url LIKE '%picsum.photos%';

-- 2) 평점·리뷰 수를 reviews 테이블 실제 집계로 재계산한다.
--    ReviewService.recalc()와 같은 규칙(소수 1자리 반올림, 리뷰 없으면 0).
UPDATE venues v SET
    avg_rating   = COALESCE((SELECT ROUND(AVG(r.rating)::numeric, 1) FROM reviews r WHERE r.venue_id = v.id), 0),
    review_count = (SELECT COUNT(*) FROM reviews r WHERE r.venue_id = v.id)
WHERE v.review_count <> (SELECT COUNT(*) FROM reviews r WHERE r.venue_id = v.id)
   OR v.avg_rating   <> COALESCE((SELECT ROUND(AVG(r.rating)::numeric, 1) FROM reviews r WHERE r.venue_id = v.id), 0);

UPDATE performances p SET
    avg_rating   = COALESCE((SELECT ROUND(AVG(r.rating)::numeric, 1) FROM reviews r WHERE r.performance_id = p.id), 0),
    review_count = (SELECT COUNT(*) FROM reviews r WHERE r.performance_id = p.id)
WHERE p.review_count <> (SELECT COUNT(*) FROM reviews r WHERE r.performance_id = p.id)
   OR p.avg_rating   <> COALESCE((SELECT ROUND(AVG(r.rating)::numeric, 1) FROM reviews r WHERE r.performance_id = p.id), 0);
