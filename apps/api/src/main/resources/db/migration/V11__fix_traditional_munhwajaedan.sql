-- '문화재단'(기관명)이 '문화재' 키워드에 오탐되어 traditional로 태깅된 행 정정.
-- '문화재단'을 제거한 제목에 다른 전통 키워드가 없고, 장르도 국악이 아니면 해제.
-- SEOUL 소스는 분류(CODENAME) 기반이라 항상 유지.
update performances set traditional = false
 where traditional
   and source <> 'SEOUL'
   and title like '%문화재단%'
   and coalesce(genre, '') not like '%국악%'
   and not (
        replace(title, '문화재단', '') like '%전통%'
     or replace(title, '문화재단', '') like '%국악%'
     or replace(title, '문화재단', '') like '%한옥%'
     or replace(title, '문화재단', '') like '%고궁%'
     or replace(title, '문화재단', '') like '%궁궐%'
     or replace(title, '문화재단', '') like '%문화재%'
     or replace(title, '문화재단', '') like '%민속%'
     or replace(title, '문화재단', '') like '%판소리%'
     or replace(title, '문화재단', '') like '%사물놀이%'
     or replace(title, '문화재단', '') like '%풍물%'
     or replace(title, '문화재단', '') like '%농악%'
     or replace(title, '문화재단', '') like '%탈춤%'
     or replace(title, '문화재단', '') like '%한복%'
     or replace(title, '문화재단', '') like '%궁중%'
     or replace(title, '문화재단', '') like '%무형유산%'
     or replace(title, '문화재단', '') like '%향교%'
     or replace(title, '문화재단', '') like '%서원%'
     or replace(title, '문화재단', '') like '%단오%'
     or replace(title, '문화재단', '') like '%세시풍속%'
     or replace(title, '문화재단', '') like '%경복궁%'
     or replace(title, '문화재단', '') like '%창덕궁%'
     or replace(title, '문화재단', '') like '%덕수궁%'
     or replace(title, '문화재단', '') like '%창경궁%'
     or replace(title, '문화재단', '') like '%경희궁%'
     or replace(title, '문화재단', '') like '%종묘%');
