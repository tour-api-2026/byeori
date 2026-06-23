-- 행사(공연/축제) 지도·경로 네비게이션용 좌표.
-- TourAPI 축제는 mapx/mapy, KOPIS 공연은 공연시설상세(prfplc)의 la/lo에서 채운다.
ALTER TABLE performances ADD COLUMN lat NUMERIC(12, 8);
ALTER TABLE performances ADD COLUMN lng NUMERIC(12, 8);
