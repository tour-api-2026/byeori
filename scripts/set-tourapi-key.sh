#!/usr/bin/env bash
# 한국관광공사 인증키를 안전하게 교체한다.
#   ./scripts/set-tourapi-key.sh
# 키는 화면에도 셸 기록에도 남지 않게 표준입력으로만 받는다.
# 실제 호출로 검증한 뒤에만 .env 를 고치고, 실패하면 아무것도 바꾸지 않는다.
set -euo pipefail
cd "$(dirname "$0")/.."

printf '포털의 "일반 인증키(Decoding)" 를 붙여넣고 Enter: ' >&2
read -rs KEY; printf '\n' >&2
KEY="$(printf '%s' "$KEY" | tr -d '[:space:]')"
[ -n "$KEY" ] || { echo '키가 비어 있습니다.' >&2; exit 1; }

echo '검증 중...' >&2
TOURAPI_CANDIDATE="$KEY" ./scripts/check-tourapi-key.sh "$KEY" || {
  echo >&2
  echo '검증 실패 — .env 는 그대로 두었습니다. 포털에서 키를 다시 복사해 주세요.' >&2
  exit 1
}

BAK="infra/.env.bak.$(date +%Y%m%d-%H%M%S)"
cp infra/.env "$BAK"
KEY="$KEY" python3 - <<'PY'
import os, re, pathlib
p = pathlib.Path('infra/.env'); s = p.read_text(encoding='utf-8')
new = 'TOURAPI_KEY=' + os.environ['KEY']
s2, n = re.subn(r'(?m)^TOURAPI_KEY=.*$', lambda _: new, s)
p.write_text(s2 if n else s.rstrip('\n') + '\n' + new + '\n', encoding='utf-8')
PY
echo "적용 완료 (백업: $BAK)" >&2

docker compose -f infra/docker-compose.yml up -d api >/dev/null
curl -sf --retry 60 --retry-delay 2 --retry-connrefused --retry-all-errors \
     --max-time 200 -o /dev/null http://127.0.0.1:8080/
docker exec seoulride-nginx nginx -s reload   # 컨테이너 IP 가 바뀌므로 필수
echo '재기동 완료.' >&2
