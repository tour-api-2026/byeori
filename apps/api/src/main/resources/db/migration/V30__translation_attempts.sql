-- 번역 배치가 "모델이 끝내 못 번역하는 행" 에 막히지 않게 하는 실패 횟수.
--
-- 일본어·중국어 출력에 한글이 섞여 버려지거나 응답에서 통째로 빠지는 행은 해시가 안 찍혀
-- 큐 맨 앞(ORDER BY id)에 영원히 남는다. 그런 행이 MAX_PER_RUN 을 넘으면 새로 동기화된
-- 행사에 순서가 오지 않는데 아무 로그도 없었다. 언어마다 실패가 따로 나므로 언어별로 센다.
-- 성공하면 0 으로 돌아가고, 이름이 바뀌면 모두 0 으로 비운다. 메모리 값이 아니라 컬럼인 이유는
-- 재시작해도 남아야 하기 때문이다.
ALTER TABLE venues
    ADD COLUMN tr_fail_en SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN tr_fail_ja SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN tr_fail_zh SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE performances
    ADD COLUMN tr_fail_en SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN tr_fail_ja SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN tr_fail_zh SMALLINT NOT NULL DEFAULT 0;
