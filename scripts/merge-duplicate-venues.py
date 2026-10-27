#!/usr/bin/env python3
"""같은 장소가 두 줄로 들어와 있는 것을 하나로 합친다.

    python3 scripts/merge-duplicate-venues.py [--apply]

`--apply` 없이 돌리면 **아무것도 바꾸지 않고** 무엇이 바뀔지만 보여 준다. 먼저 그렇게
돌려 목록을 눈으로 확인한 뒤 적용한다.

왜 생겼나
---------
시드로 넣은 장소(id 1~8 등)와 동기화가 받아온 같은 장소가 따로 남았다. 공사 contentId
로는 못 알아본다 — 시드 쪽은 'TA-126508' 처럼 접두사가 붙어 있는데, 그 번호를 떼고 맞춰
보면 창경궁의 짝이 광화문, 국립고궁박물관의 짝이 군산 은파 물빛다리로 나온다. 시드의
번호는 지금 공사 번호와 무관하다. 그래서 **이름이 같고 500m 안**인 것으로 찾는다.

왜 숨기기만 하면 안 되나
------------------------
장소는 리뷰·찜·루트·큐레이션 코스가 참조한다. 28쌍 중 5쌍은 **양쪽 모두** 참조가 있어서,
한쪽을 그냥 숨기면 큐레이션 코스가 숨은 장소를 가리키고 사용자의 루트 한 칸이 빈다.
그래서 참조를 남길 쪽으로 옮긴 뒤 숨긴다.

지우지 않고 INACTIVE 로 둔다 — 조회는 전부 status='ACTIVE' 로 거르므로 목록·지도·검색에서
빠지고, 잘못 합쳤을 때 되돌릴 수 있다.
"""
import subprocess
import sys

# 같은 장소로 볼 거리. 위도 0.0045°(약 500m), 경도 0.0056°(위도 36도에서 약 500m).
# 28쌍이 잡히고 1km 로 늘려도 같은 28쌍이라 여기가 경계다. 궁·마을처럼 터가 넓은 곳은
# 같은 장소인데도 좌표가 400m 벌어진다(북촌한옥마을 405m, 경복궁 398m).
DLAT, DLNG = 0.0045, 0.0056

# 장소를 가리키는 모든 테이블. 하나라도 빠뜨리면 그 참조가 숨은 줄을 가리킨 채 남는다.
REFS = [
    ("reviews", "venue_id"),
    ("wishlists", "venue_id"),
    ("itinerary_items", "venue_id"),
    ("curated_course_items", "venue_id"),
    ("content_tag_votes", "venue_id"),
    ("venue_reports", "venue_id"),
    ("performances", "venue_id"),
]


def psql(sql: str) -> str:
    out = subprocess.run(
        ["docker", "exec", "-i", "byeori-postgres",
         "psql", "-U", "byeori", "-d", "byeori", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A", "-F", "\t"],
        input=sql, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit("psql 실패:\n" + out.stderr.strip())
    return out.stdout


def pairs():
    """합칠 쌍. 남길 쪽(keeper)과 숨길 쪽(loser)을 이미 정해서 돌려준다.

    남길 쪽을 고르는 순서:
      ① 공사 번호가 'TA-' 가 아닌 줄 — 시드의 TA- 번호는 지금 공사 번호와 무관해서,
         그 줄을 남기면 다음 동기화가 같은 장소를 또 새로 넣는다.
      ② 번역 출처가 좋은 줄 — 사람(HUMAN) > 공사 공식(TOURAPI) > AI > 없음.
      ③ 사진이 있는 줄.
      ④ id 가 작은 줄 — 먼저 들어온 것이고, 무엇보다 매번 같은 답이 나온다.
    """
    rows = psql(f"""
        with d as (
          select a.id a_id, b.id b_id, a.name
            from venues a join venues b
              on a.name = b.name and a.id < b.id
             and a.status = 'ACTIVE' and b.status = 'ACTIVE'
             and abs(a.lat - b.lat) <= {DLAT} and abs(a.lng - b.lng) <= {DLNG}
        ), scored as (
          select d.name, v.id,
                 (case when v.tour_content_id like 'TA-%' then 1 else 0 end) s1,
                 (case coalesce(v.tr_source,'') when 'HUMAN' then 0 when 'TOURAPI' then 1
                                                when 'AI' then 2 else 3 end) s2,
                 (case when coalesce(v.image_url,'') = '' then 1 else 0 end) s3,
                 v.id s4,
                 d.a_id, d.b_id
            from d join venues v on v.id in (d.a_id, d.b_id)
        )
        , picked as (
          select name,
                 (array_agg(id order by s1, s2, s3, s4))[1] as keeper,
                 (array_agg(id order by s1, s2, s3, s4))[2] as loser
            from scored group by name, a_id, b_id
        )
        select p.name, p.keeper, p.loser,
               -- 남길 쪽에 번호가 없고 숨길 쪽에 쓸 만한 번호가 있으면 그 값을 옮긴다
               coalesce(case when k.tour_content_id is null
                              and l.tour_content_id is not null
                              and l.tour_content_id not like 'TA-%'
                         then l.tour_content_id end, '') as move_id
          from picked p join venues k on k.id = p.keeper join venues l on l.id = p.loser
         order by p.name;
    """)
    out = []
    for line in rows.splitlines():
        p = line.split("\t")
        if len(p) == 4 and p[1] and p[2]:
            out.append((p[0], int(p[1]), int(p[2]), p[3] or None))
    return out


def plan(keeper: int, loser: int, move_id: str | None) -> list[str]:
    """loser 의 참조를 keeper 로 옮기고 loser 를 숨기는 SQL.

    move_id 는 남길 쪽으로 옮길 공사 번호. 남길 쪽이 이미 번호를 갖고 있거나 숨길 쪽에
    쓸 만한 번호가 없으면 None 이다.
    """
    sql = []
    # 같은 사용자가 양쪽을 다 찜했거나 같은 태그에 양쪽으로 투표했으면, 옮기는 순간
    # 유니크 제약에 걸린다. 겹치는 쪽을 먼저 지운다 — 옮겨도 같은 의미의 한 줄이 남는다.
    sql.append(f"""delete from wishlists w
                    where w.venue_id = {loser}
                      and exists (select 1 from wishlists k
                                   where k.venue_id = {keeper} and k.user_id = w.user_id);""")
    sql.append(f"""delete from content_tag_votes v
                    where v.venue_id = {loser}
                      and exists (select 1 from content_tag_votes k
                                   where k.venue_id = {keeper} and k.user_id = v.user_id
                                     and k.comment_tag_id = v.comment_tag_id);""")
    # 한 코스에 같은 장소가 두 번 들어가면 같은 곳을 연달아 들르는 코스가 된다.
    sql.append(f"""delete from curated_course_items i
                    where i.venue_id = {loser}
                      and exists (select 1 from curated_course_items k
                                   where k.venue_id = {keeper} and k.course_id = i.course_id);""")
    for table, col in REFS:
        sql.append(f"update {table} set {col} = {keeper} where {col} = {loser};")
    # 공사 번호를 남길 쪽으로 옮긴다. **이걸 빠뜨리면 병합이 하루 만에 되돌아간다** —
    # 남길 쪽에 번호가 없으면 다음 동기화가 숨긴 쪽의 번호를 '처음 보는 장소'로 알고
    # 새 줄을 또 넣는다(북촌한옥마을·한복남 경복궁점이 그 경우였다).
    #
    # 번호에 유니크 제약이 걸려 있다. **떼는 것이 먼저다** — 한 트랜잭션 안이라도 유니크
    # 인덱스는 문장마다 검사하므로, 숨길 쪽이 값을 쥔 채로 남길 쪽에 넣으면 거기서 막힌다.
    # 그래서 값을 미리 읽어 두고(move_id) 뗀 다음 넣는다.
    sql.append(f"update venues set tour_content_id = null where id = {loser};")
    if move_id:
        sql.append(f"update venues set tour_content_id = '{move_id}' "
                   f"where id = {keeper} and tour_content_id is null;")
    # view_logs 는 외래키가 없고 target_type 으로 갈린다.
    sql.append(f"update view_logs set target_id = {keeper} "
               f"where target_type = 'VENUE' and target_id = {loser};")
    # 리뷰를 옮겼으니 남길 쪽의 평점을 다시 센다. 안 하면 별점이 옮겨온 리뷰를 반영하지 못한다.
    sql.append(f"""update venues set
                     review_count = (select count(*) from reviews where venue_id = {keeper}),
                     avg_rating = coalesce((select round(avg(rating)::numeric, 1)
                                              from reviews where venue_id = {keeper}), 0)
                   where id = {keeper};""")
    sql.append(f"update venues set status = 'INACTIVE' where id = {loser};")
    return sql


def refs_of(vid: int) -> str:
    counts = psql(" union all ".join(
        f"select '{t}', count(*) from {t} where {c} = {vid}" for t, c in REFS))
    parts = []
    for line in counts.splitlines():
        t, n = line.split("\t")
        if int(n):
            parts.append(f"{t} {n}")
    return ", ".join(parts) if parts else "참조 없음"


def main() -> None:
    apply = "--apply" in sys.argv
    found = pairs()
    print(f"합칠 쌍: {len(found)}\n")

    statements = []
    for name, keeper, loser, move_id in found:
        print(f"  {name}")
        print(f"    남김 {keeper}: {refs_of(keeper)}"
              + (f"  ← 공사번호 {move_id} 를 넘겨받는다" if move_id else ""))
        print(f"    숨김 {loser}: {refs_of(loser)}  → 참조를 {keeper} 로 옮긴다")
        statements += plan(keeper, loser, move_id)

    if not apply:
        print(f"\n(--apply 를 주지 않아 아무것도 바꾸지 않았다. SQL {len(statements)}줄 준비됨)")
        return

    # 한 트랜잭션으로. 중간에 실패하면 통째로 되돌아간다 — 참조만 옮기고 숨기지 못한
    # 상태가 가장 다루기 나쁘다.
    psql("begin;\n" + "\n".join(statements) + "\ncommit;")
    print(f"\n{len(found)}쌍 병합 완료")


if __name__ == "__main__":
    main()
