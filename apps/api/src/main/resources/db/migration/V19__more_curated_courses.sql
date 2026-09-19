-- 루트 탐색의 추천 코스를 2개에서 14개로 늘린다.
--
-- 전국 주요 전통문화 권역(서울 궁궐·북촌, 전주, 경주, 안동, 수원, 부산, 공주·부여, 대구, 제주, 강릉)을
-- 하루 코스로 묶었다. 기간이 끝나면 사라지는 행사는 넣지 않고 상설 장소만 쓴다.
-- 메모에는 바뀌지 않는 사실만 적는다(운영시간·가격은 장소 상세의 실시간 정보로 본다).
--
-- 장소는 DB id 가 아니라 공사 콘텐츠 ID(tour_content_id)로 찾는다. id 는 환경마다 다르고,
-- 콘텐츠가 없는 환경에서는 그 스톱만 빠지고 마이그레이션은 실패하지 않는다.

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('서울 궁궐 한나절', '경복궁에서 시작해 왕실 유물과 생활 문화를 보고 서촌 시장에서 마무리하는 코스', '궁궐산책', 4.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '126508', '10:00', '조선의 법궁. 흥례문과 근정전을 지나 경회루까지 걸어 보세요'),
        (2, '129811', '11:30', '경복궁 곁에 있어 바로 이어 갈 수 있는 조선 왕실 유물 박물관이에요'),
        (3, '2608977', '13:00', '경복궁 안쪽에 있는 박물관으로 우리 생활 문화를 한눈에 볼 수 있어요'),
        (4, '1590323', '14:30', '서촌의 전통시장이에요. 경복궁 서쪽으로 걸어가요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('창덕궁·종묘 세계유산 산책', '유네스코 세계유산 창덕궁과 종묘를 창경궁으로 잇고 광장시장에서 마무리하는 코스', '세계유산', 4.5)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '127642', '10:00', '유네스코 세계유산. 후원은 따로 관람하니 미리 확인해 두세요'),
        (2, '126511', '12:00', '창덕궁과 담을 맞댄 궁궐로 함양문을 지나 이어져요'),
        (3, '126510', '14:00', '조선 왕과 왕비의 신주를 모신 사당이자 유네스코 세계유산이에요'),
        (4, '132183', '16:00', '종묘에서 걸어갈 수 있는 전통시장이에요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('북촌·인사동 공예 산책', '한옥 골목과 공예 박물관, 인사동 공예 상점을 걸어서 잇는 코스', '공예·한옥', 3.5)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '127454', '10:00', '흥선대원군이 살던 집이자 고종이 자란 곳이에요'),
        (2, '126537', '11:00', '사람이 사는 한옥 동네라 조용히 둘러봐 주세요'),
        (3, '2738571', '13:00', '국내 첫 공립 공예박물관이에요'),
        (4, '128553', '14:30', '공예 상점이 모인 인사동의 나선형 건물이에요'),
        (5, '2650046', '16:00', '좁은 골목의 한옥을 고친 가게들이 모여 있어요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('전주 한옥마을 한나절', '성당과 경기전, 언덕 전망과 향교를 지나 남부시장에서 저녁까지', '한옥마을', 5.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '250331', '10:00', '한옥마을 입구에 있는 붉은 벽돌 성당이에요'),
        (2, '147656', '10:30', '태조 이성계의 어진을 모신 곳이에요'),
        (3, '126621', '12:30', '한옥마을 지붕들을 내려다볼 수 있는 언덕이에요'),
        (4, '147684', '14:00', '큰 은행나무가 있는 조선 시대 향교예요'),
        (5, '132326', '17:00', '전주의 대표 전통시장이에요. 주말 저녁에는 야시장이 열려요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('경주 신라 역사 산책', '대릉원에서 첨성대, 교촌마을과 월정교를 지나 동궁과 월지 야경까지', '역사탐방', 8.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '1492402', '10:00', '신라 고분이 모인 곳으로 천마총 내부를 볼 수 있어요'),
        (2, '126207', '11:30', '대릉원에서 걸어서 갈 수 있는 신라의 천문대예요'),
        (3, '128676', '12:30', '경주 최부잣집이 있던 한옥마을이에요'),
        (4, '2603509', '14:00', '교촌마을 옆 남천 위에 복원한 신라의 다리예요'),
        (5, '129778', '15:00', '성덕대왕신종을 볼 수 있는 박물관이에요'),
        (6, '128526', '18:00', '해가 진 뒤 연못에 비치는 야경이 유명해요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('안동 하회마을과 병산서원', '세계유산 하회마을과 병산서원을 둘러보고 안동 시내에서 찜닭으로 마무리', '세계유산', 7.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '894027', '10:00', '풍산 류씨가 대대로 살아온 유네스코 세계유산 마을이에요'),
        (2, '130150', '12:00', '하회탈을 비롯해 세계의 탈을 모은 박물관이에요'),
        (3, '126227', '13:30', '서애 류성룡을 모신 서원으로 만대루가 유명해요'),
        (4, '2611137', '17:00', '안동 시내의 찜닭 골목에서 저녁을 먹어요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('수원 화성 성곽길', '정조의 행궁에서 출발해 성곽 위 정자를 지나 시장까지 걷는 코스', '역사탐방', 4.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '129437', '10:00', '정조가 수원에 행차할 때 머문 행궁이에요'),
        (2, '1921150', '11:30', '화성을 쌓은 과정과 기록을 볼 수 있어요'),
        (3, '2613658', '13:30', '성곽 위 정자로 아래 연못과 어우러진 경치가 좋아요'),
        (4, '2752971', '15:30', '팔달문 옆 전통시장으로 순대타운이 유명해요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('부산 원도심 시장 투어', '감천문화마을에서 자갈치·깡통시장을 지나 책방골목까지', '전통시장', 5.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '1997221', '10:00', '산비탈을 따라 알록달록한 집들이 이어진 마을이에요'),
        (2, '132190', '12:30', '바다 앞 수산시장에서 점심을 먹어요'),
        (3, '1878218', '14:30', '국내 첫 상설 야시장이 열린 시장이에요'),
        (4, '1608633', '16:00', '피란 시절에 생긴 헌책방 골목이에요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('공주·부여 백제 여행', '웅진과 사비, 두 백제 도읍을 하루에 잇는 코스(차로 이동)', '역사탐방', 7.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '125949', '10:00', '백제 웅진 시기의 산성으로 성곽을 따라 걸어요'),
        (2, '129787', '11:30', '무령왕릉에서 나온 유물을 볼 수 있어요'),
        (3, '126650', '14:00', '공주에서 차로 이동해요. 백제 사비 시기의 석탑이에요'),
        (4, '130062', '15:00', '백제 금동대향로를 볼 수 있어요'),
        (5, '125984', '16:30', '백제 무왕 때 만들었다고 전하는 연못이에요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('대구 근대골목 투어', '청라언덕에서 계산성당과 고택, 약령시를 지나 서문시장까지 걷는 코스', '근대골목', 4.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '2611140', '10:00', '선교사 주택이 남아 있는 언덕이에요'),
        (2, '250332', '11:00', '청라언덕에서 3·1만세운동길 계단을 내려가면 바로예요'),
        (3, '1372956', '11:30', '시인 이상화가 말년을 보낸 집이에요'),
        (4, '2470057', '13:00', '조선 시대부터 이어진 약령시의 역사를 볼 수 있어요'),
        (5, '132532', '15:00', '대구를 대표하는 전통시장이에요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('제주 원도심 역사 산책', '조선 시대 제주의 관청 터를 둘러보고 동문시장에서 점심을', '역사탐방', 3.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '128641', '10:00', '조선 시대 제주를 다스리던 관청을 복원한 곳이에요'),
        (2, '126460', '11:00', '제주목 관아 바로 앞에 있는 오래된 누정이에요'),
        (3, '1013246', '12:00', '제주를 대표하는 상설시장에서 점심을 먹어요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

WITH c AS (
    INSERT INTO curated_courses (title, description, theme, duration_hours)
    VALUES ('강릉 오죽헌과 선교장', '율곡의 생가와 사대부가 한옥, 허균·허난설헌 공원을 지나 중앙시장까지', '한옥나들이', 5.0)
    RETURNING id)
INSERT INTO curated_course_items (course_id, venue_id, sort_order, recommended_time, note)
SELECT c.id, v.id, s.ord, s.t, s.note
FROM c
CROSS JOIN (VALUES
        (1, '129784', '10:00', '율곡 이이가 태어난 집이에요'),
        (2, '125800', '11:30', '조선 후기 사대부가의 큰 한옥이에요'),
        (3, '2773086', '13:30', '허균과 허난설헌 남매를 기리는 공원이에요'),
        (4, '132771', '15:30', '강릉을 대표하는 전통시장이에요')
    ) AS s(ord, content_id, t, note)
JOIN venues v ON v.tour_content_id = s.content_id AND v.source = 'TOURAPI';

-- 커버: V16과 같은 규칙(제목에 이름이 들어간 스톱 → 방문 순서가 앞선 스톱). 새 코스만 채운다.
UPDATE curated_courses c SET cover_image_url = (
    SELECT v.image_url
    FROM curated_course_items i
    JOIN venues v ON v.id = i.venue_id
    WHERE i.course_id = c.id AND COALESCE(v.image_url, '') <> ''
    ORDER BY (CASE WHEN c.title LIKE '%' || v.name || '%' THEN 0 ELSE 1 END), i.sort_order
    LIMIT 1)
WHERE c.cover_image_url IS NULL;
