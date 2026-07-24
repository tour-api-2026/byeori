-- 외부 데이터(KOPIS 공연명, TourAPI 장소명)가 기존 길이를 초과하는 경우 대비
ALTER TABLE performances ALTER COLUMN title TYPE varchar(300);
ALTER TABLE venues ALTER COLUMN name TYPE varchar(150);
