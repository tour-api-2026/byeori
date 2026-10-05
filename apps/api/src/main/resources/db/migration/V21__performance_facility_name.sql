-- KOPIS 공연시설명(fcltynm). 공연목록 응답에 이미 들어오는 값인데 그동안 버리고 있었다.
-- 이 이름으로 venues 와 이어 붙인다 — 좌표로는 같은 건물의 다른 시설이 0~10m 거리에 있어
-- 갈리지 않았고, 맞는지 확인할 기준도 없었다.
alter table performances add column facility_name varchar(200);
