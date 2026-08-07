-- 전통 테마 행사 태깅 컬럼. 판정 로직은 TraditionalTagger와 동일 기준으로 기존 행 백필.
alter table performances add column traditional boolean not null default false;

update performances set traditional = true
 where genre like '%국악%'
    or title like '%전통%' or title like '%국악%' or title like '%한옥%'
    or title like '%고궁%' or title like '%궁궐%' or title like '%문화재%'
    or title like '%민속%' or title like '%판소리%' or title like '%사물놀이%'
    or title like '%풍물%' or title like '%농악%' or title like '%탈춤%'
    or title like '%한복%' or title like '%궁중%' or title like '%무형유산%'
    or title like '%향교%' or title like '%서원%' or title like '%단오%'
    or title like '%세시풍속%'
    or title like '%경복궁%' or title like '%창덕궁%' or title like '%덕수궁%'
    or title like '%창경궁%' or title like '%경희궁%' or title like '%종묘%';

create index idx_performances_traditional on performances (traditional) where traditional;
