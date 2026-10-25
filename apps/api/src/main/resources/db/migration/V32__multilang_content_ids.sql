-- 공사 다국어판(EngService2·JpnService2·ChsService2)에서 찾아낸 같은 장소의 contentId.
--
-- 다국어판은 국문판과 contentId 가 다르다 — 같은 경복궁이 국문 126508, 영문 264337 이다.
-- 그래서 매번 이름·좌표로 다시 맞출 수는 없고(장소 3만 5천 × 다국어 1만 5천), 한 번 맞춘
-- 결과를 여기 적어 둔다.
--
-- 이 값이 있으면 상세 설명을 공사 공식 번역으로 받아올 수 있다(detailCommon2 는 contentId
-- 하나당 한 번 부르는 오퍼레이션이라 id 를 알아야 한다). 이름은 목록에서 한꺼번에 받지만
-- 설명은 그렇지 않다.
ALTER TABLE venues
    ADD COLUMN tour_content_id_en VARCHAR(20),
    ADD COLUMN tour_content_id_ja VARCHAR(20),
    ADD COLUMN tour_content_id_zh VARCHAR(20);

-- 이름으로 맞출 때 쓰는 인덱스. 다국어 한 쪽을 돌릴 때마다 장소 이름으로 1만 5천 번 찾는다.
CREATE INDEX IF NOT EXISTS idx_venues_name ON venues (name);
