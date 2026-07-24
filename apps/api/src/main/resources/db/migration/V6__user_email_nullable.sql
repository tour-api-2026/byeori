-- 소셜 로그인 사용자는 이메일을 제공하지 않을 수 있다(카카오 이메일은 "선택 동의" 항목).
-- email NOT NULL 제약 제거 → 이메일 없는 소셜 가입 허용.
-- UNIQUE 제약은 유지(Postgres에서 NULL은 서로 중복으로 보지 않아 다수 NULL 허용).
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
