-- 필수 명소 카테고리 — 이미 들어와 있는 행을 옮긴다.
--
-- 앞으로 들어올 것은 CategoryMapper 가 동기화 때 분류한다. 그런데 한강공원은 이미
-- 관광지(12)로 들어와 '문화'·'체험'에 들어 있고, 증분 동기화는 바뀐 것만 받으므로
-- 가만두면 영원히 옛 분류로 남는다. 여기서 한 번 옮긴다.
--
-- 브랜드 매장(올리브영·다이소·백화점)은 지금 DB 에 한 건도 없다 — 쇼핑(38)에서 분류가
-- null 이라 수집 단계에서 통째로 버려져 왔다. 그쪽은 배포 뒤 전량 재수집
-- (POST /api/v1/admin/sync/trigger?only=venues) 으로 들어온다.
--
-- 조건은 CategoryMapper.isMustVisit 과 같은 규칙이다. 한쪽만 고치면 다음 동기화가
-- 되돌려 놓으므로 둘을 함께 본다.
UPDATE venues
   SET category = '필수명소'
 WHERE category IS DISTINCT FROM '필수명소'
   AND (name ~ '^(올리브영|다이소|(롯데|신세계|현대|갤러리아|AK|NC) ?백화점)'
        OR name LIKE '%한강공원%');
