-- 소셜 로그인: 외부 제공자 사용자 식별자·프로필 이미지 컬럼 추가.
-- (auth_provider, provider_user_id) 조합으로 동일 소셜 계정을 업서트한다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_user_id  varchar(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_image_url varchar(500);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_provider_user
    ON users (auth_provider, provider_user_id)
    WHERE provider_user_id IS NOT NULL;
