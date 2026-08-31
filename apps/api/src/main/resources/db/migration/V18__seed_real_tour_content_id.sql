-- 시드 장소(id ≤ 9)의 tour_content_id가 실제 값이 아니라 'TA-126508' 처럼 임의로 만든 값이거나
-- 아예 비어 있다. 그래서 장소 상세의 실시간 조회(detailCommon2/detailIntro2)가 아무것도
-- 받아오지 못해, 하필 한복 혜택이 붙은 대표 장소들만 소개글·이용시간이 비어 보인다.
--
-- tour_content_id 를 실제 값으로 덮어쓸 수는 없다. 이 컬럼에는 유니크 제약
-- (ux_venues_tour_content_id)이 걸려 있고, 같은 장소의 TourAPI 행이 이미 그 값을 갖고 있어
-- 중복이 된다. 동기화 upsert가 이 제약에 의존하므로 제약을 푸는 것도 안 된다.
--
-- 그래서 표시 전용 컬럼을 따로 둔다. 동기화 키가 아니라 상세 화면에서 공사 API를 부를 때만
-- 쓰는 참조값이므로 유니크 제약을 걸지 않는다.
ALTER TABLE venues ADD COLUMN IF NOT EXISTS detail_content_id varchar(50);

COMMENT ON COLUMN venues.detail_content_id IS
    '상세 화면 실시간 조회(detailCommon2/detailIntro2)에 쓸 공사 콘텐츠 ID. 동기화 키(tour_content_id)와 달리 중복 허용.';

-- 1) 이미 정상적인 콘텐츠 ID를 가진 장소는 그대로 쓴다.
UPDATE venues SET detail_content_id = tour_content_id
WHERE tour_content_id ~ '^[0-9]+$' AND detail_content_id IS NULL;

-- 2) 시드 장소는 같은 이름의 TourAPI 행이 가진 실제 콘텐츠 ID를 빌려온다.
--    같은 이름이 여럿이면 카테고리가 일치하는 쪽 → 좌표가 가까운 쪽 순으로 고른다(V15와 동일 규칙).
UPDATE venues s SET detail_content_id = (
    SELECT t.tour_content_id FROM venues t
    WHERE t.name = s.name AND t.id <> s.id
      AND t.tour_content_id ~ '^[0-9]+$'
    ORDER BY (CASE WHEN t.category = s.category THEN 0 ELSE 1 END),
             (ABS(t.lat - s.lat) + ABS(t.lng - s.lng))
    LIMIT 1)
WHERE s.id <= 9 AND s.detail_content_id IS NULL;
