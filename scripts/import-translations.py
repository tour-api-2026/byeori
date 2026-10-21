#!/usr/bin/env python3
"""채운 번역 CSV 를 DB 에 넣는다.

    python3 scripts/import-translations.py docs/translation/translate-priority.csv [--apply]

`--apply` 없이 돌리면 **아무것도 바꾸지 않고** 무엇이 바뀔지만 센다. 먼저 그렇게 돌려
숫자를 확인한 뒤 적용한다.

규칙:
- `kind` + `id` 로 행을 찾는다. `ko` 는 읽기만 한다 — 다만 DB 의 현재 값과 다르면
  그 행은 **건너뛴다**. CSV 를 뽑은 뒤 동기화가 이름을 바꿨다는 뜻이고, 그 경우
  낡은 번역을 붙이면 엉뚱한 이름에 엉뚱한 번역이 달린다.
- 빈 칸은 건너뛴다. 일부만 채워 와도 그만큼만 들어간다.
- 번역을 넣은 행에는 name_hash 를 함께 찍는다. 그래야 번역 배치가 "이미 끝난 행"으로
  보고 다시 번역하지 않는다. 해시는 배치와 같은 규칙(원문 이름의 sha256)이다.
- tr_source 는 'HUMAN' 으로 남긴다. AI 가 덮어쓰면 안 되는 행을 가릴 수 있게.
"""
import csv
import hashlib
import subprocess
import sys

TABLE = {
    "venue": ("venues", "name", "name_en", "name_ja", "name_zh"),
    "perf": ("performances", "title", "title_en", "title_ja", "title_zh"),
    "course": ("curated_courses", "title", "title_en", "title_ja", "title_zh"),
    "course_desc": ("curated_courses", "description", "desc_en", "desc_ja", "desc_zh"),
}


def psql(sql: str) -> str:
    """운영 DB 는 컨테이너 안에서만 열려 있다(호스트에 포트를 열지 않는다)."""
    out = subprocess.run(
        ["docker", "exec", "-i", "byeori-postgres",
         "psql", "-U", "byeori", "-d", "byeori", "-v", "ON_ERROR_STOP=1", "-q", "-t", "-A"],
        input=sql, capture_output=True, text=True)
    if out.returncode != 0:
        sys.exit("psql 실패:\n" + out.stderr.strip())
    return out.stdout


def lit(s: str) -> str:
    """SQL 문자열 리터럴. 작은따옴표만 막으면 된다(값은 전부 문자열이다)."""
    return "'" + s.replace("'", "''") + "'"


def main() -> None:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    path, apply = sys.argv[1], "--apply" in sys.argv

    rows = list(csv.DictReader(open(path, encoding="utf-8")))
    stats = {"반영": 0, "빈칸": 0, "이름이 바뀜": 0, "행 없음": 0,
             "모르는 kind": 0, "컬럼 없음": 0}
    updates: list[str] = []

    # 번역 컬럼이 아직 없는 테이블이 있다(코스는 V29 가 만든다). 그 kind 는 건너뛴다 —
    # 모르고 돌리면 트랜잭션이 통째로 깨져 멀쩡한 행까지 안 들어간다.
    have = set(psql("""select table_name || '.' || column_name
                       from information_schema.columns
                       where table_schema = 'public';""").split())
    ready = {k for k, (t, _src, *cols) in TABLE.items()
             if all(f"{t}.{c}" in have for c in cols)}
    missing = set(TABLE) - ready
    if missing:
        print("번역 컬럼이 없어 건너뛰는 kind: " + ", ".join(sorted(missing)))

    # DB 의 현재 원문을 한 번에 읽어 둔다. 행마다 질의하면 47,500번 왕복한다.
    current: dict[tuple[str, str], str] = {}
    for kind, (table, src, *_rest) in TABLE.items():
        for line in psql(f"select id, coalesce({src}, '') from {table};").splitlines():
            if "|" in line:
                i, v = line.split("|", 1)
                current[(kind, i)] = v

    for r in rows:
        kind, rid = r["kind"], r["id"]
        if kind not in TABLE:
            stats["모르는 kind"] += 1
            continue
        if kind not in ready:
            stats["컬럼 없음"] += 1
            continue
        tr = {k: (r.get(k) or "").strip() for k in ("en", "ja", "zh")}
        if not any(tr.values()):
            stats["빈칸"] += 1
            continue
        if (kind, rid) not in current:
            stats["행 없음"] += 1
            continue
        if current[(kind, rid)] != r["ko"]:
            stats["이름이 바뀜"] += 1
            continue

        table, src, c_en, c_ja, c_zh = TABLE[kind]
        sets = [f"{col} = {lit(tr[k])}"
                for col, k in ((c_en, "en"), (c_ja, "ja"), (c_zh, "zh")) if tr[k]]
        # 설명 번역은 이름 해시와 무관하다 — 이름이 아니라 설명이다.
        if kind != "course_desc":
            h = hashlib.sha256(r["ko"].encode("utf-8")).hexdigest()
            sets += [f"name_hash = {lit(h)}", "tr_source = 'HUMAN'"]
        updates.append(f"update {table} set {', '.join(sets)} where id = {rid};")
        stats["반영"] += 1

    print(f"{path}: {len(rows)}행 읽음")
    for k, v in stats.items():
        print(f"  {k}: {v}")

    if not apply:
        print("\n(--apply 를 주지 않아 아무것도 바꾸지 않았다)")
        return

    # 한 트랜잭션으로 넣는다. 중간에 실패하면 통째로 되돌아간다 —
    # 절반만 들어간 상태는 어디까지 됐는지 알 수 없어 가장 다루기 나쁘다.
    psql("begin;\n" + "\n".join(updates) + "\ncommit;")
    print(f"\n{stats['반영']}행 반영 완료")


if __name__ == "__main__":
    main()
