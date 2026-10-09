-- 개인화 추천의 재료가 되는 행동 기록.
--
-- 로그인한 사용자만 남긴다. 주인 없는 기록은 개인화에 쓸 수 없는데 개인정보 범위만 넓힌다.
-- 대가로 인기 점수가 로그인 트래픽만 보게 되지만, 추천에 쓰는 값이라 그쪽이 맞다.
--
-- 90일이 지나면 지운다(ActivityRetentionScheduler). 취향은 변하고 행사는 어차피 끝난다.

create table view_logs (
    id          bigserial primary key,
    user_id     bigint      not null references users (id),
    -- VENUE | PERFORMANCE. 두 종류뿐이라 별도 테이블로 가르지 않는다.
    target_type varchar(20) not null,
    target_id   bigint      not null,
    -- 어느 화면에서 들어왔는지. 같은 장소라도 검색으로 찾아 들어간 쪽이 의도가 뚜렷하다.
    source      varchar(20),
    viewed_at   timestamp   not null default now()
);

-- "이 사람이 최근에 본 것" 과 "이 항목이 최근에 얼마나 보였나" 두 방향으로만 읽는다.
create index ix_view_logs_user on view_logs (user_id, viewed_at desc);
create index ix_view_logs_target on view_logs (target_type, target_id, viewed_at desc);

create table search_logs (
    id           bigserial primary key,
    user_id      bigint      not null references users (id),
    keyword      varchar(100) not null,
    category     varchar(40),
    -- 결과가 0이던 검색은 취향 신호가 아니라 '우리에게 없는 것' 신호다. 나중에 가른다.
    result_count integer     not null default 0,
    searched_at  timestamp   not null default now()
);

create index ix_search_logs_user on search_logs (user_id, searched_at desc);
