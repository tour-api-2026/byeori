-- 벼리(Byeori) 초기 스키마 (erd_final_1.txt v3 기준)
-- 명명: snake_case 복수형 테이블, PK id, FK <entity>_id, 시각 *_at, enum varchar
-- 다형성(performance_id XOR venue_id)은 CHECK + 부분 유니크 인덱스로 처리

-- =============== 회원 / 인증 ===============
CREATE TABLE users (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           varchar(50)  NOT NULL,
    email          varchar(100) NOT NULL UNIQUE,
    phone          varchar(20),
    password_hash  varchar(255),
    auth_provider  varchar(20)  NOT NULL DEFAULT 'LOCAL',
    role           varchar(20)  NOT NULL DEFAULT 'USER',      -- USER | ADMIN
    phone_verified boolean      NOT NULL DEFAULT false,
    email_verified boolean      NOT NULL DEFAULT false,
    language       varchar(10)  NOT NULL DEFAULT 'ko',
    status         varchar(20)  NOT NULL DEFAULT 'ACTIVE',    -- ACTIVE | WITHDRAWN
    withdrawn_at   timestamp,
    created_at     timestamp    NOT NULL DEFAULT now(),
    updated_at     timestamp    NOT NULL DEFAULT now()
);

CREATE TABLE social_auths (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint NOT NULL REFERENCES users(id),
    provider         varchar(20)  NOT NULL,
    provider_user_id varchar(100) NOT NULL,
    access_token     text,
    created_at       timestamp NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id)
);

CREATE TABLE user_interests (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id  bigint NOT NULL REFERENCES users(id),
    category varchar(30) NOT NULL
);

CREATE TABLE terms (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title        varchar(100) NOT NULL,
    content      text NOT NULL,
    type         varchar(30) NOT NULL,
    is_required  boolean NOT NULL DEFAULT true,
    effective_at timestamp NOT NULL
);

CREATE TABLE user_terms (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id   bigint NOT NULL REFERENCES users(id),
    terms_id  bigint NOT NULL REFERENCES terms(id),
    is_agreed boolean NOT NULL,
    agreed_at timestamp NOT NULL DEFAULT now(),
    UNIQUE (user_id, terms_id)
);

-- =============== 장소 ===============
CREATE TABLE venues (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            varchar(100) NOT NULL,
    address         varchar(255) NOT NULL,
    lat             decimal(10,7) NOT NULL,
    lng             decimal(10,7) NOT NULL,
    operating_hours varchar(255),
    phone           varchar(20),
    homepage_url    varchar(500),
    description     text,
    image_url       varchar(500),                              -- 카드/상세 썸네일 (TourAPI 이미지 URL 캐시)
    category        varchar(30),
    hanbok_discount      boolean NOT NULL DEFAULT false,
    hanbok_discount_desc varchar(255),
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE | INACTIVE
    avg_rating      decimal(2,1) NOT NULL DEFAULT 0,
    review_count    int NOT NULL DEFAULT 0,
    source          varchar(20) NOT NULL DEFAULT 'MANUAL',     -- KOPIS | TOURAPI | MANUAL | USER
    kopis_id        varchar(20),
    tour_content_id varchar(30),
    synced_at       timestamp,
    created_by_user_id bigint REFERENCES users(id),
    visibility      varchar(20) NOT NULL DEFAULT 'PUBLIC',     -- PUBLIC | PRIVATE
    created_at      timestamp NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_venues_kopis_id ON venues(kopis_id) WHERE kopis_id IS NOT NULL;
CREATE UNIQUE INDEX ux_venues_tour_content_id ON venues(tour_content_id) WHERE tour_content_id IS NOT NULL;
CREATE INDEX ix_venues_source ON venues(source);
CREATE INDEX ix_venues_created_by ON venues(created_by_user_id);

CREATE TABLE venue_reports (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id   bigint NOT NULL REFERENCES venues(id),
    user_id    bigint NOT NULL REFERENCES users(id),
    reason     varchar(50) NOT NULL,
    detail     text,
    status     varchar(20) NOT NULL DEFAULT 'PENDING',         -- PENDING | REVIEWED | DISMISSED
    created_at timestamp NOT NULL DEFAULT now()
);

-- =============== 공연 / 행사 ===============
CREATE TABLE performances (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    venue_id         bigint NOT NULL REFERENCES venues(id),
    title            varchar(150) NOT NULL,
    genre            varchar(50),
    poster_image_url varchar(500),
    start_date       date NOT NULL,
    end_date         date NOT NULL,
    state            varchar(20) NOT NULL DEFAULT 'UPCOMING',  -- UPCOMING | ONGOING | ENDED
    external_booking_url varchar(500),
    avg_rating       decimal(2,1) NOT NULL DEFAULT 0,
    review_count     int NOT NULL DEFAULT 0,
    created_at       timestamp NOT NULL DEFAULT now(),
    source           varchar(20) NOT NULL DEFAULT 'MANUAL',    -- KOPIS | TOURAPI | MANUAL
    kopis_id         varchar(20),
    tour_content_id  varchar(30),
    synced_at        timestamp
);
CREATE UNIQUE INDEX ux_performances_kopis_id ON performances(kopis_id) WHERE kopis_id IS NOT NULL;
CREATE UNIQUE INDEX ux_performances_tour_content_id ON performances(tour_content_id) WHERE tour_content_id IS NOT NULL;
CREATE INDEX ix_performances_start_date ON performances(start_date);
CREATE INDEX ix_performances_state ON performances(state);

CREATE TABLE performance_medias (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    performance_id bigint NOT NULL REFERENCES performances(id),
    media_type     varchar(20) NOT NULL,                       -- IMAGE | VIDEO
    url            varchar(500) NOT NULL,
    sort_order     int NOT NULL DEFAULT 0
);

CREATE TABLE performance_options (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    performance_id bigint NOT NULL REFERENCES performances(id),
    name           varchar(100) NOT NULL,
    description    text,
    extra_price    decimal(10,2) NOT NULL DEFAULT 0
);

CREATE TABLE performance_schedules (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    performance_id bigint NOT NULL REFERENCES performances(id),
    scheduled_at   timestamp NOT NULL,
    status         varchar(20) NOT NULL DEFAULT 'AVAILABLE'    -- AVAILABLE | CANCELLED
);

-- =============== 찜 / 리뷰 (다형성: performance_id XOR venue_id) ===============
CREATE TABLE wishlists (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        bigint NOT NULL REFERENCES users(id),
    performance_id bigint REFERENCES performances(id),
    venue_id       bigint REFERENCES venues(id),
    created_at     timestamp NOT NULL DEFAULT now(),
    CHECK ((performance_id IS NULL) <> (venue_id IS NULL))
);
CREATE UNIQUE INDEX ux_wishlists_user_perf  ON wishlists(user_id, performance_id) WHERE performance_id IS NOT NULL;
CREATE UNIQUE INDEX ux_wishlists_user_venue ON wishlists(user_id, venue_id)       WHERE venue_id IS NOT NULL;

CREATE TABLE reviews (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        bigint NOT NULL REFERENCES users(id),
    performance_id bigint REFERENCES performances(id),
    venue_id       bigint REFERENCES venues(id),
    rating         int NOT NULL,
    content        text,
    created_at     timestamp NOT NULL DEFAULT now(),
    updated_at     timestamp NOT NULL DEFAULT now(),
    CHECK ((performance_id IS NULL) <> (venue_id IS NULL)),
    CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX ix_reviews_perf  ON reviews(performance_id) WHERE performance_id IS NOT NULL;
CREATE INDEX ix_reviews_venue ON reviews(venue_id)       WHERE venue_id IS NOT NULL;

-- =============== 방문자 코멘트 태그 ===============
CREATE TABLE comment_tags (
    id     bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name   varchar(30) NOT NULL UNIQUE,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE'               -- ACTIVE | INACTIVE
);

CREATE TABLE content_tag_votes (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comment_tag_id bigint NOT NULL REFERENCES comment_tags(id),
    user_id        bigint NOT NULL REFERENCES users(id),
    performance_id bigint REFERENCES performances(id),
    venue_id       bigint REFERENCES venues(id),
    created_at     timestamp NOT NULL DEFAULT now(),
    CHECK ((performance_id IS NULL) <> (venue_id IS NULL))
);
CREATE UNIQUE INDEX ux_votes_tag_user_perf  ON content_tag_votes(comment_tag_id, user_id, performance_id) WHERE performance_id IS NOT NULL;
CREATE UNIQUE INDEX ux_votes_tag_user_venue ON content_tag_votes(comment_tag_id, user_id, venue_id)       WHERE venue_id IS NOT NULL;

-- =============== 추천 코스 ===============
CREATE TABLE curated_courses (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title           varchar(150) NOT NULL,
    description     text,
    theme           varchar(50),
    cover_image_url varchar(500),
    duration_hours  decimal(4,1),
    status          varchar(20) NOT NULL DEFAULT 'ACTIVE',     -- ACTIVE | INACTIVE
    created_at      timestamp NOT NULL DEFAULT now()
);

CREATE TABLE curated_course_items (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id        bigint NOT NULL REFERENCES curated_courses(id),
    performance_id   bigint REFERENCES performances(id),
    venue_id         bigint REFERENCES venues(id),
    sort_order       int NOT NULL DEFAULT 0,
    recommended_time varchar(20),
    note             text,
    CHECK ((performance_id IS NULL) <> (venue_id IS NULL))
);

-- =============== 여행 일지 ===============
CREATE TABLE itineraries (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          bigint NOT NULL REFERENCES users(id),
    title            varchar(150) NOT NULL,
    start_date       date NOT NULL,
    end_date         date NOT NULL,
    source_type      varchar(20) NOT NULL DEFAULT 'CUSTOM',    -- CUSTOM | CURATED
    source_course_id bigint REFERENCES curated_courses(id),
    created_at       timestamp NOT NULL DEFAULT now(),
    updated_at       timestamp NOT NULL DEFAULT now()
);

CREATE TABLE itinerary_items (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    itinerary_id   bigint NOT NULL REFERENCES itineraries(id),
    performance_id bigint REFERENCES performances(id),
    venue_id       bigint REFERENCES venues(id),
    visit_date     date NOT NULL,
    sort_order     int NOT NULL DEFAULT 0,
    planned_time   varchar(20),
    memo           text,
    created_at     timestamp NOT NULL DEFAULT now(),
    CHECK ((performance_id IS NULL) <> (venue_id IS NULL))
);

-- =============== 동기화 로그 ===============
CREATE TABLE sync_logs (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider      varchar(20) NOT NULL DEFAULT 'KOPIS',        -- KOPIS | TOURAPI
    target_type   varchar(20) NOT NULL,                        -- PERFORMANCE | VENUE
    started_at    timestamp NOT NULL,
    finished_at   timestamp,
    processed_cnt int NOT NULL DEFAULT 0,
    status        varchar(20) NOT NULL,                        -- SUCCESS | PARTIAL | FAILED
    message       text
);
