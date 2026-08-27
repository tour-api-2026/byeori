-- V14가 picsum 더미 이미지를 걷어내면서 일부 화면이 빈 회색 카드로 남았다.
-- 시드 장소(id ≤ 9)는 대부분 TourAPI가 이미 갖고 있는 실제 장소와 이름이 겹치는 중복 행이다.
-- 같은 장소의 한국관광공사 사진을 연결해 준다(출처 표기가 이미 "ⓒ한국관광공사"라 정당하다).
--
-- 중복 행을 합치거나 지우지는 않는다. 사용자의 여행일정·즐겨찾기가 시드 id를 참조하고 있어
-- 병합은 별도 작업이 필요하다.

-- 1) 시드 장소에 같은 이름의 TourAPI 행 사진을 연결한다.
--    같은 이름이 여럿이면 카테고리가 일치하는 쪽을, 그다음 좌표가 가까운 쪽을 고른다
--    (예: '경복궁'은 문화 1375를 고르고 맛집 12835를 피한다).
UPDATE venues s SET image_url = (
    SELECT t.image_url FROM venues t
    WHERE t.name = s.name AND t.id <> s.id
      AND COALESCE(t.image_url, '') <> '' AND t.image_url NOT LIKE '%picsum%'
    ORDER BY (CASE WHEN t.category = s.category THEN 0 ELSE 1 END),
             (ABS(t.lat - s.lat) + ABS(t.lng - s.lng))
    LIMIT 1)
WHERE s.id <= 9
  AND COALESCE(s.image_url, '') = ''
  AND EXISTS (
    SELECT 1 FROM venues t
    WHERE t.name = s.name AND t.id <> s.id
      AND COALESCE(t.image_url, '') <> '' AND t.image_url NOT LIKE '%picsum%');

-- 2) V14가 놓친 테이블. 코스 커버도 picsum 랜덤 이미지였다
--    ('경복궁 별빛야행 풀코스'에 필름카메라 사진이 뜨던 원인).
--    코스에 실제로 포함된 장소의 사진을 커버로 쓴다.
UPDATE curated_courses c SET cover_image_url = (
    SELECT v.image_url FROM curated_course_items i
    JOIN venues v ON v.id = i.venue_id
    WHERE i.course_id = c.id
      AND COALESCE(v.image_url, '') <> '' AND v.image_url NOT LIKE '%picsum%'
    ORDER BY i.sort_order
    LIMIT 1)
WHERE c.cover_image_url LIKE '%picsum%';

-- 3) 그래도 채우지 못한 커버는 NULL로. 무관한 사진보다 플레이스홀더가 낫다.
UPDATE curated_courses SET cover_image_url = NULL WHERE cover_image_url LIKE '%picsum%';
