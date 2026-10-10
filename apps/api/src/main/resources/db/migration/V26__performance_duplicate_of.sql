-- 출처가 다른 같은 행사를 가린다.
--
-- KOPIS 와 서울 열린데이터가 같은 공연을 각자 올리는데, 우리 중복 방지는 출처별 외부
-- id 로만 돌아서(kopis_id / seoul_id) 같은 행사가 두 줄로 남는다. 2026-10-11 기준
-- 진행 중·예정 3,325건에서 18쌍이 그랬다.
--
-- 지우지 않는다. 남길 줄의 id 만 적고 목록 쿼리에서 가린다 — 판정이 틀렸을 때
-- update performances set duplicate_of = null where id = ... 한 줄로 되돌아온다.
-- 찜·루트에 이미 담긴 줄이 사라지지 않는 것도 지우지 않는 이유다.
alter table performances
    add column duplicate_of bigint references performances(id) on delete set null;

-- 목록 쿼리가 전부 duplicate_of is null 을 달고 나간다. 거의 모든 행이 null 이므로
-- 부분 인덱스로 둔다(가려진 줄만 찾는 관리용 조회에 쓴다).
create index idx_performances_duplicate_of on performances (duplicate_of)
    where duplicate_of is not null;
