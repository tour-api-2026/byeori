#!/usr/bin/env bash
# 한국관광공사 인증키 상태를 주기적으로 찍어 둔다.
#
# 키가 몇 번이나 하루이틀 만에 등록 해제됐는데, 사망 시점을 24시간 구간까지밖에
# 좁히지 못했다(마지막 성공과 첫 403 사이가 그만큼 벌어져 있었다).
# 15분마다 한 줄씩 남겨 두면 다음번엔 분 단위로 특정되고, 그 직전에 어떤 호출이
# 있었는지와 맞춰볼 수 있다. 하루 96회라 할당량 부담은 없다.
#
#   ./scripts/watch-tourapi-key.sh          # 한 번 찍기(크론이 이걸 부른다)
#   ./scripts/watch-tourapi-key.sh --tail   # 최근 기록 보기
#   ./scripts/watch-tourapi-key.sh --report # 상태가 바뀐 지점만 추려 보기
set -u
cd "$(dirname "$0")/.."
LOG="${TOURAPI_WATCH_LOG:-$HOME/.byeori/tourapi-key.log}"
mkdir -p "$(dirname "$LOG")"

case "${1:-}" in
  --tail)   tail -n "${2:-20}" "$LOG"; exit 0 ;;
  --report)
    [ -s "$LOG" ] || { echo '기록 없음'; exit 0; }
    echo '상태가 바뀐 지점:'
    awk -F'\t' '$2!=p{print "  " $1 "  " $2 "  " $3; p=$2}' "$LOG"
    echo
    echo "총 $(wc -l < "$LOG")회 기록 / 최근: $(tail -1 "$LOG" | cut -f1,2)"
    exit 0 ;;
esac

python3 - "$LOG" <<'PY'
import sys, re, pathlib, urllib.parse, urllib.request, urllib.error, json, time

log = pathlib.Path(sys.argv[1])
env = pathlib.Path('infra/.env').read_text(encoding='utf-8')
key = dict(re.findall(r'(?m)^([A-Za-z0-9_]+)=(.*)$', env)).get('TOURAPI_KEY', '').strip()
stamp = time.strftime('%Y-%m-%dT%H:%M:%S%z')
# 키 자체는 절대 기록하지 않는다. 값이 바뀌었는지만 알면 되므로 앞 4자만 남긴다.
tag = key[:4] if key else '----'

if not key:
    log.open('a').write(f'{stamp}\tNO_KEY\t인증키 미설정\t{tag}\n'); raise SystemExit

url = ('https://apis.data.go.kr/B551011/KorService2/ldongCode2'
       f'?MobileOS=ETC&MobileApp=byeori&_type=json&numOfRows=1&pageNo=1'
       f'&serviceKey={urllib.parse.quote(key, safe="")}')
t0 = time.time()
try:
    body = urllib.request.urlopen(url, timeout=20).read().decode('utf-8', 'replace')
    ms = int((time.time() - t0) * 1000)
    try:
        json.loads(body)['response']['body']['items']
        state, detail = 'OK', f'{ms}ms'
    except Exception:
        state, detail = 'ODD', body[:60].replace('\n', ' ')
except urllib.error.HTTPError as e:
    body = e.read().decode('utf-8', 'replace')
    m = re.search(r'<errMsg>([^<]*)|"errMsg"\s*:\s*"([^"]*)', body)
    err = (m.group(1) or m.group(2)) if m else f'HTTP {e.code}'
    state, detail = f'HTTP{e.code}', err
except Exception as e:
    state, detail = 'TIMEOUT', type(e).__name__

log.open('a').write(f'{stamp}\t{state}\t{detail}\t{tag}\n')
print(f'{stamp}  {state}  {detail}')
PY
