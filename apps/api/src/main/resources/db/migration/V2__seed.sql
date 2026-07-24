-- 데모 시드 데이터 (Figma 화면 기준: 경복궁·창경궁·북촌·궁중문화축전 등)
-- 이미지는 데모용 placeholder(picsum). FK는 이름 기준 서브쿼리로 연결.

-- ===== 사용자 =====
INSERT INTO users (name, email, password_hash, role, email_verified) VALUES
  ('벼리 운영자', 'admin@byeori.app', '$2a$10$demoDemoDemoDemoDemoDe', 'ADMIN', true),
  ('김여행',      'user@byeori.app',  '$2a$10$demoDemoDemoDemoDemoDe', 'USER',  true);

-- ===== 코멘트 태그 마스터 =====
INSERT INTO comment_tags (name) VALUES
  ('분위기 좋음'), ('야경 최고'), ('재방문 의사'), ('인생샷 명소'), ('가성비 좋음');

-- ===== 장소 =====
INSERT INTO venues (name, address, lat, lng, operating_hours, phone, image_url, category, hanbok_discount, hanbok_discount_desc, avg_rating, review_count, source, tour_content_id) VALUES
  ('경복궁',          '서울 종로구 사직로 161',     37.5796170, 126.9770410, '09:00~18:00 (화 휴무)', '02-3700-3900', 'https://picsum.photos/seed/gyeongbok/640/420',  '문화', true,  '한복 착용 시 무료 입장',  4.8, 1320, 'TOURAPI', 'TA-126508'),
  ('창경궁',          '서울 종로구 창경궁로 185',   37.5780500, 126.9950100, '09:00~21:00',          '02-762-4868',  'https://picsum.photos/seed/changgyeong/640/420','문화', true,  '한복 착용 시 무료 입장',  4.7,  860, 'TOURAPI', 'TA-126512'),
  ('국립고궁박물관',  '서울 종로구 효자로 12',      37.5757800, 126.9750900, '10:00~18:00',          '02-3701-7500', 'https://picsum.photos/seed/palacemuseum/640/420','문화', false, NULL,                     4.6,  540, 'TOURAPI', 'TA-129382');

INSERT INTO venues (name, address, lat, lng, operating_hours, image_url, category, hanbok_discount, hanbok_discount_desc, avg_rating, review_count, source) VALUES
  ('북촌한옥마을',    '서울 종로구 계동길 37',     37.5826500, 126.9830800, '상시 개방',     'https://picsum.photos/seed/bukchon/640/420',  '체험', false, NULL,                  4.5, 980, 'TOURAPI'),
  ('한복남 경복궁점', '서울 종로구 자하문로 5',    37.5770000, 126.9740000, '09:00~19:00',   'https://picsum.photos/seed/hanboknam/640/420','한복', true,  '대여 시 헤어 무료',     4.6, 410, 'MANUAL'),
  ('차 마시는 뜰',    '서울 종로구 북촌로11가길', 37.5820000, 126.9850000, '11:00~21:00',   'https://picsum.photos/seed/teahouse/640/420', '카페', true,  '한복 착용 시 10% 할인', 4.7, 320, 'MANUAL'),
  ('오설록 티하우스 북촌','서울 종로구 북촌로 5길', 37.5815000, 126.9845000, '10:00~22:00', 'https://picsum.photos/seed/osulloc/640/420',  '카페', false, NULL,                  4.4, 600, 'MANUAL'),
  ('통인시장',        '서울 종로구 자하문로15길', 37.5805000, 126.9700000, '07:00~21:00',   'https://picsum.photos/seed/tongin/640/420',   '맛집', false, NULL,                  4.3, 750, 'TOURAPI');

-- USER 등록 장소(거지맵식 공유)
INSERT INTO venues (name, address, lat, lng, image_url, category, source, visibility, created_by_user_id)
SELECT '숨은 포토스팟 돌담길', '서울 종로구 율곡로', 37.5760000, 126.9900000,
       'https://picsum.photos/seed/photospot/640/420', '체험', 'USER', 'PUBLIC', u.id
FROM users u WHERE u.email = 'user@byeori.app';

-- ===== 공연 / 행사 =====
INSERT INTO performances (venue_id, title, genre, poster_image_url, start_date, end_date, state, external_booking_url, avg_rating, review_count, source, tour_content_id)
SELECT v.id, '경복궁 별빛야행', '야간개장', 'https://picsum.photos/seed/byeolbit/640/420', DATE '2026-05-01', DATE '2026-08-31', 'ONGOING', 'https://www.gyeongbokgung.go.kr', 4.9, 2100, 'TOURAPI', 'TA-PF-1001'
FROM venues v WHERE v.name = '경복궁';

INSERT INTO performances (venue_id, title, genre, poster_image_url, start_date, end_date, state, avg_rating, review_count, source, tour_content_id)
SELECT v.id, '창경궁 야간개장', '야간개장', 'https://picsum.photos/seed/changnight/640/420', DATE '2026-06-15', DATE '2026-09-30', 'UPCOMING', 4.7, 540, 'TOURAPI', 'TA-PF-1002'
FROM venues v WHERE v.name = '창경궁';

INSERT INTO performances (venue_id, title, genre, poster_image_url, start_date, end_date, state, avg_rating, review_count, source, tour_content_id)
SELECT v.id, '궁중문화축전', '축제', 'https://picsum.photos/seed/gungfest/640/420', DATE '2026-05-04', DATE '2026-06-10', 'ONGOING', 4.6, 880, 'TOURAPI', 'TA-PF-1003'
FROM venues v WHERE v.name = '경복궁';

INSERT INTO performances (venue_id, title, genre, poster_image_url, start_date, end_date, state, avg_rating, review_count, source)
SELECT v.id, '고궁 특별전: 왕실의 보물', '전시', 'https://picsum.photos/seed/royaltreasure/640/420', DATE '2026-04-01', DATE '2026-07-31', 'ONGOING', 4.5, 300, 'MANUAL'
FROM venues v WHERE v.name = '국립고궁박물관';

-- ===== 추천 코스 =====
INSERT INTO curated_courses (title, description, theme, cover_image_url, duration_hours) VALUES
  ('경복궁 별빛야행 풀코스', '한복 입고 즐기는 경복궁 야경과 전통차 한 잔까지', '한복나들이', 'https://picsum.photos/seed/course1/640/420', 4.5),
  ('북촌 전통 디저트 투어',  '북촌 골목의 전통 디저트와 찻집을 잇는 미식 코스',     '미식투어',   'https://picsum.photos/seed/course2/640/420', 3.0);

-- 코스1 구성 (한복남 → 별빛야행 → 차 마시는 뜰)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, 1, '오후', '한복 대여 후 출발'
FROM curated_courses c, venues v WHERE c.title='경복궁 별빛야행 풀코스' AND v.name='한복남 경복궁점';
INSERT INTO curated_course_items (course_id, performance_id, sort_order, recommended_time, note)
SELECT c.id, p.id, 2, '19:00', '별빛야행 관람'
FROM curated_courses c, performances p WHERE c.title='경복궁 별빛야행 풀코스' AND p.title='경복궁 별빛야행';
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, 3, '21:00', '전통차로 마무리'
FROM curated_courses c, venues v WHERE c.title='경복궁 별빛야행 풀코스' AND v.name='차 마시는 뜰';

-- 코스2 구성 (북촌 → 오설록 → 통인시장)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time)
SELECT c.id, v.id, 1, '오전' FROM curated_courses c, venues v WHERE c.title='북촌 전통 디저트 투어' AND v.name='북촌한옥마을';
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time)
SELECT c.id, v.id, 2, '오후' FROM curated_courses c, venues v WHERE c.title='북촌 전통 디저트 투어' AND v.name='오설록 티하우스 북촌';
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time)
SELECT c.id, v.id, 3, '저녁' FROM curated_courses c, venues v WHERE c.title='북촌 전통 디저트 투어' AND v.name='통인시장';
