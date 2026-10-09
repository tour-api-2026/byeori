-- 한 사람이 여러 로그인 수단을 가질 수 있게 한다.
--
-- 지금은 users 한 줄에 사람과 로그인 수단이 같이 묶여 있다(auth_provider, provider_user_id).
-- 그래서 카카오로 가입한 사람이 구글로 들어오면 **별개의 계정**이 생기고 찜·루트·관심사가
-- 둘로 갈린다. social_auths 는 처음부터 있었지만 쓰는 코드가 없어 0건이었다.
--
-- users 의 두 칸은 지우지 않는다. '처음 가입한 수단'으로 남겨 두면 되돌리기 쉽고,
-- 기존 조회 코드가 그대로 돈다.

-- 같은 소셜 계정이 두 사람에게 붙으면 누구로 로그인할지 알 수 없다. DB에서 막는다.
create unique index ux_social_auths_provider_user
    on social_auths (provider, provider_user_id);

create index ix_social_auths_user on social_auths (user_id);

-- 기존 사용자를 옮긴다. 소셜만 — ADMIN·TEST·REVIEW·LOCAL 은 외부 제공자가 아니라
-- 아이디/비밀번호로 들어오므로 연결할 것이 없다.
insert into social_auths (user_id, provider, provider_user_id, created_at)
select id, auth_provider, provider_user_id, coalesce(created_at, now())
from users
where auth_provider in ('KAKAO', 'GOOGLE')
  and provider_user_id is not null
on conflict do nothing;
