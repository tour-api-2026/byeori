-- 큐레이션 코스는 우리가 쓴 글이라 사람이 번역한다. 14건뿐이고 루트 탐색 탭 전체가
-- 이것이라 첫인상을 좌우한다 — AI 번역의 들쭉날쭉함을 둘 자리가 아니다.
--
-- 여기서는 컬럼만 만든다. 번역문은 이미 사람이 채운 CSV(docs/translation/)에 있고
-- scripts/import-translations.py 가 넣는다 — 같은 글을 SQL 에 한 번 더 적어
-- 두 곳이 어긋나게 하지 않으려는 것이다. 값이 없는 동안 API 는 한국어 원본을 준다.
ALTER TABLE curated_courses
    ADD COLUMN title_en VARCHAR(255),
    ADD COLUMN title_ja VARCHAR(255),
    ADD COLUMN title_zh VARCHAR(255),
    ADD COLUMN desc_en  TEXT,
    ADD COLUMN desc_ja  TEXT,
    ADD COLUMN desc_zh  TEXT;
