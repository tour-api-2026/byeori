-- 서울 열린데이터 문화행사(culturalEventInfo) 동기화용 외부 ID (cultcode 또는 title 해시)
alter table performances add column seoul_id text;
create unique index uq_performances_seoul_id on performances (seoul_id) where seoul_id is not null;
