-- 루트 공유 링크용 토큰.
--
-- id 를 그대로 공개하지 않는 이유: /itineraries/1, 2, 3... 을 훑으면 남의 여행 날짜와
-- 동선이 통째로 보인다. 토큰은 128비트 난수라 짐작으로 맞힐 수 없다.
--
-- null 이면 아직 공유한 적이 없다는 뜻이다. 한 번 발급하면 바뀌지 않는다 —
-- 이미 보낸 링크가 말없이 죽으면 받은 쪽이 영문을 모른다.
alter table itineraries add column share_token varchar(32);

-- 토큰으로 바로 찾는다. 유니크 인덱스는 null 을 여러 개 허용하므로 미공유 루트와 공존한다.
create unique index ux_itineraries_share_token on itineraries (share_token);
