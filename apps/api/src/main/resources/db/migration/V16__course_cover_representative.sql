-- V15는 코스 커버를 "첫 번째 스톱의 사진"으로 채웠는데, '경복궁 별빛야행 풀코스'의 첫 스톱이
-- 한복 대여점이라 커버에 환전소 간판이 걸렸다. 코스를 대표하는 사진이 아니다.
--
-- 규칙을 바꾼다: 코스에 포함된 장소(공연 스톱의 공연장 포함) 중에서
--   ① 코스 제목에 이름이 들어간 장소를 우선("경복궁 별빛야행 풀코스" → 경복궁)
--   ② 없으면 방문 순서가 앞선 장소
-- 후보를 스톱으로 한정하므로 이름만 같은 엉뚱한 장소(예: '경복궁'이라는 상호의 음식점)는 걸리지 않는다.
UPDATE curated_courses c SET cover_image_url = (
    SELECT v.image_url
    FROM curated_course_items i
    LEFT JOIN performances p ON p.id = i.performance_id
    JOIN venues v ON v.id = COALESCE(i.venue_id, p.venue_id)
    WHERE i.course_id = c.id
      AND COALESCE(v.image_url, '') <> ''
    ORDER BY (CASE WHEN c.title LIKE '%' || v.name || '%' THEN 0 ELSE 1 END), i.sort_order
    LIMIT 1)
WHERE EXISTS (
    SELECT 1
    FROM curated_course_items i
    LEFT JOIN performances p ON p.id = i.performance_id
    JOIN venues v ON v.id = COALESCE(i.venue_id, p.venue_id)
    WHERE i.course_id = c.id AND COALESCE(v.image_url, '') <> '');
