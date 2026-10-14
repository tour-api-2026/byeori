-- 공모전 '공유 데이터' 제출 파일 생성.
--
-- 세 공공데이터(공사 축제/행사 · KOPIS 공연 · 서울 문화행사)를 통합·정제한 결과를
-- 한 테이블로 뽑는다. 제출 제한이 파일 1개 · 10MB 라서 CSV 하나로 끝낸다
-- (실측 12,271행 1.8MB).
--
--   docker exec byeori-postgres psql -U byeori -d byeori -f /dev/stdin \
--     < docs/submission/공유데이터-생성.sql > 벼리_공유데이터_행사통합.csv
--
-- 담는 것은 **우리가 만든 값**이고, 원본은 공공 식별자로 가리킨다. 남의 데이터를
-- 통째로 되돌려 주는 것이 아니라, 거기에 더한 것을 돌려주는 모양이어야 한다.
--
--   · 전통문화 판정        TraditionalTagger
--   · 관광지 연결          VenueNameMatcher (공연시설명 정규화 — 두 데이터에 공통 키가 없다)
--   · 출처 간 중복 식별    PerformanceDeduper
--   · 좌표 보강            KOPIS 공연시설 상세
--
-- 비공개·사용자 생성 장소(source='USER')는 뺀다. 공개할 수 없는 것이고, 애초에
-- 공공데이터에서 온 것이 아니다.
\copy (
select
  p.source                                            as 출처,
  coalesce(p.tour_content_id, p.kopis_id, p.seoul_id) as 출처_고유id,
  p.title                                             as 행사명,
  p.genre                                             as 장르,
  p.start_date                                        as 시작일,
  p.end_date                                          as 종료일,
  p.lat                                               as 위도,
  p.lng                                               as 경도,
  case when p.traditional then 'Y' else 'N' end       as 전통문화_판정,
  p.facility_name                                     as 공연시설명_원본,
  v.tour_content_id                                   as 연결된_관광지_contentid,
  v.name                                              as 연결된_관광지명,
  v.category                                          as 연결된_관광지_분류,
  coalesce(d.source, '')                              as 중복_대표_출처,
  coalesce(d.tour_content_id, d.kopis_id, d.seoul_id, '') as 중복_대표_고유id
from performances p
left join venues v on v.id = p.venue_id and v.source = 'TOURAPI'
left join performances d on d.id = p.duplicate_of
where p.source in ('TOURAPI', 'KOPIS', 'SEOUL')
  and coalesce(p.tour_content_id, p.kopis_id, p.seoul_id) is not null
order by p.source, p.start_date, p.id
) to stdout with (format csv, header true);
