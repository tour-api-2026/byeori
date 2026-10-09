-- 관심사에 종류를 붙인다.
--
-- user_interests 는 (user_id, category) 뿐이라 '주제'와 '지역'을 한 테이블에 넣으면
-- 둘이 섞인다. 테이블을 하나 더 만들지 않고 kind 로 가른다 — 읽는 쪽이 늘 함께 읽는다.
--
-- 기존 행은 없다(0건). 그래서 기본값을 주고 바로 NOT NULL 로 조인다.
alter table user_interests add column kind varchar(10) not null default 'TOPIC';

-- 같은 사람이 같은 관심사를 두 번 가질 이유가 없다. 저장은 '지우고 다시 넣기'라
-- 경쟁 상황에서 중복이 생길 수 있는데, 여기서 막는다.
create unique index ux_user_interests on user_interests (user_id, kind, category);
