#!/usr/bin/env bash
#
# 관리자 동기화를 돌린다. 로그인과 토큰 처리를 대신한다.
#
#   bash scripts/admin-sync.sh venues          # 장소 전량 재수집
#   bash scripts/admin-sync.sh multilang       # 공사 다국어판 공식 이름 가져오기
#   bash scripts/admin-sync.sh translate 100   # AI 번역 배치(건수 제한)
#   bash scripts/admin-sync.sh incremental     # 증분 동기화
#   bash scripts/admin-sync.sh dedupe          # 행사 중복 쓸기
#
# 비밀번호를 화면에 띄우지도, 명령 기록에 남기지도 않는다 — infra/.env 에서 직접 읽는다.
# 액세스 토큰은 1시간짜리라 부를 때마다 새로 받는다.
#
# **서버가 있는 호스트에서, 레포 루트에서** 돌린다. 관리자 API 는 외부에 열려 있지 않다
# (nginx 가 /api/v1/admin 을 내보내지 않는다). 그래서 127.0.0.1:8080 으로 직접 부른다.
set -euo pipefail

ONLY="${1:-}"
LIMIT="${2:-}"
API="http://127.0.0.1:8080/api/v1"
ENV_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/infra/.env"

if [ -z "$ONLY" ]; then
  sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
fi

[ -f "$ENV_FILE" ] || { echo "infra/.env 가 없다: $ENV_FILE" >&2; exit 1; }

ADMIN_ID=$(grep -m1 '^ADMIN_ID=' "$ENV_FILE" | cut -d= -f2-)
ADMIN_PASSWORD=$(grep -m1 '^ADMIN_PASSWORD=' "$ENV_FILE" | cut -d= -f2-)
if [ -z "${ADMIN_ID:-}" ] || [ -z "${ADMIN_PASSWORD:-}" ]; then
  echo "infra/.env 에 ADMIN_ID / ADMIN_PASSWORD 가 없다." >&2
  exit 1
fi

# 비밀번호가 ps 목록이나 명령 기록에 보이지 않게 본문은 표준입력으로 넘긴다.
TOKEN=$(printf '{"id":"%s","password":"%s"}' "$ADMIN_ID" "$ADMIN_PASSWORD" \
  | curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' --data-binary @- \
  | python3 -c "
import json,sys
d=json.load(sys.stdin)
if not d.get('success'):
    sys.exit('로그인 실패: ' + str((d.get('error') or {}).get('message')))
print(d['data']['accessToken'])")

URL="$API/admin/sync/trigger?only=$ONLY"
[ -n "$LIMIT" ] && URL="$URL&limit=$LIMIT"

echo "돌린다: only=$ONLY${LIMIT:+ limit=$LIMIT}"
echo "(전량 재수집은 수십 분 걸린다. 끝날 때까지 두고, 중간에 API 를 재시작하지 말 것 —"
echo " 재시작하면 돌던 동기화가 함께 죽는다.)"
echo

# 전량 재수집이 길어 기본 타임아웃으로는 끊긴다.
curl -s -X POST --max-time 7200 -H "Authorization: Bearer $TOKEN" "$URL" \
  | python3 -c "
import json,sys
raw=sys.stdin.read()
try:
    d=json.loads(raw)
except Exception:
    print(raw[:300]); raise SystemExit(1)
if not d.get('success'):
    sys.exit('실패: ' + str(d.get('error')))
for k, v in (d.get('data') or {}).items():
    print(f'  {k}: {v}' + ('   (안 돌림)' if v == -1 else ''))"
