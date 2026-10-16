-- 계산한 경로를 저장해 다시 쓴다.
--
-- 지금은 루트를 열 때마다 카카오를 부르고 최대 1초를 기다린다. 경로는 방문지를 고치기
-- 전까지 변하지 않으므로 한 번 계산해 두면 그 뒤로는 외부 호출이 없다.
--
-- 키에 mode 가 들어가는 이유: 자동/걷기/차 는 답이 다르다. 한 번 계산한 모드는 다시
-- 눌러도 즉시 나온다.
--
-- stops_hash 가 자동 무효화 장치다. 요청이 오면 지금 방문지로 해시를 만들어 비교하고,
-- 다르면 다시 계산한다. 루트를 고치면 해시가 달라지므로 갱신을 잊을 수 없다.
create table itinerary_routes (
    itinerary_id bigint      not null references itineraries(id) on delete cascade,
    mode         varchar(10) not null,              -- AUTO | WALK | DRIVE
    stops_hash   varchar(64) not null,
    distance_m   int         not null,
    duration_s   int         not null,
    path         jsonb       not null,              -- [[위도, 경도], ...]
    legs         jsonb       not null,              -- [{distance, duration, mode, points}, ...]
    computed_at  timestamp   not null default now(),
    primary key (itinerary_id, mode)
);
