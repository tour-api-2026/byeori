#!/usr/bin/env bash
# 한국관광공사 OpenAPI 인증키 점검.
#   ./scripts/check-tourapi-key.sh                 # infra/.env 의 현재 키 점검
#   ./scripts/check-tourapi-key.sh '<새 인증키>'    # 새 키를 적용 전에 미리 점검
set -u
cd "$(dirname "$0")/.."
python3 - "${1:-}" <<'PY'
import sys, re, pathlib, urllib.parse, urllib.request, json
key = (sys.argv[1] or '').strip()
if not key:
    env = pathlib.Path('infra/.env').read_text(encoding='utf-8')
    key = dict(re.findall(r'(?m)^([A-Z_]+)=(.*)$', env)).get('TOURAPI_KEY', '').strip()
if not key:
    print('키를 찾을 수 없습니다.'); raise SystemExit(1)
print(f'키 길이 {len(key)} / %인코딩 포함 {"예" if "%" in key else "아니오"}')
OPS = [
    ('areaBasedList2',  'areaBasedList2?numOfRows=1&pageNo=1&arrange=A'),
    ('searchFestival2', 'searchFestival2?numOfRows=1&pageNo=1&arrange=A&eventStartDate=20260101'),
    ('ldongCode2',      'ldongCode2?numOfRows=1&pageNo=1'),
]
BASE = 'https://apis.data.go.kr/B551011/KorService2/'
COMMON = 'MobileOS=ETC&MobileApp=byeori&_type=json'
ok = True
for name, path in OPS:
    url = f'{BASE}{path}&{COMMON}&serviceKey={urllib.parse.quote(key, safe="")}'
    try:
        with urllib.request.urlopen(url, timeout=20) as r:
            body = r.read(600).decode('utf-8', 'replace')
        try:
            total = json.loads(body)['response']['body']['totalCount']
            print(f'  {name:16s} 정상 (totalCount={total})')
        except Exception:
            print(f'  {name:16s} 응답 이상 -> {body[:120]}'); ok = False
    except Exception as e:
        body = getattr(e, 'read', lambda: b'')().decode('utf-8', 'replace')
        m = re.search(r'"errMsg"\s*:\s*"([^"]+)"|<errMsg>([^<]+)</errMsg>', body)
        msg = (m.group(1) or m.group(2)) if m else body[:120]
        print(f'  {name:16s} 실패 {getattr(e, "code", "")} -> {msg}'); ok = False
print()
print('결과:', '전부 정상 - 적용해도 됩니다.' if ok else '실패 항목이 있습니다.')
PY
