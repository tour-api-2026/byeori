#!/usr/bin/env bash
# 벼리 검증 스크립트 — Ralph 루프의 완료 게이트 판정용.
# 모든 검사가 통과하면 마지막에 "VERIFY: ALL PASS" 출력.
set -uo pipefail
ROOT="/home/hidi/dev/byeori"
FAIL=0
note() { echo "▶ $1"; }
fail() { echo "✗ $1"; FAIL=1; }
pass() { echo "✓ $1"; }

# 1) 백엔드 컴파일
note "BE compileJava"
if (cd "$ROOT/apps/api" && ./gradlew compileJava -q 2>/tmp/be-build.log); then pass "BE compiles"; else fail "BE compile FAILED (see /tmp/be-build.log)"; tail -20 /tmp/be-build.log; fi

# 2) 프론트 타입체크
note "FE tsc --noEmit"
if (cd "$ROOT/apps/app" && npx tsc --noEmit 2>/tmp/fe-tsc.log); then pass "FE typechecks"; else fail "FE tsc FAILED"; tail -20 /tmp/fe-tsc.log; fi

# 3) API 헬스 + 엔드포인트 스모크 (API가 떠 있을 때만)
API="http://localhost:8080"
if [ "$(curl -s -o /dev/null -w '%{http_code}' $API/actuator/health 2>/dev/null)" = "200" ]; then
  pass "API up"
  smoke() {
    local code; code=$(curl -s -o /dev/null -w '%{http_code}' "$API$1" 2>/dev/null)
    if [ "$code" = "200" ]; then pass "GET $1 → 200"; else fail "GET $1 → $code"; fi
  }
  smoke "/api/v1/venues"
  smoke "/api/v1/venues/1"
  smoke "/api/v1/venues/1/performances"
  smoke "/api/v1/performances"
  smoke "/api/v1/comment-tags"
  smoke "/api/v1/curated-courses"
  smoke "/api/v1/curated-courses/1"

  # 인증이 필요한 개인 도메인. 무인증 401(가드 정상)을 먼저 확인하고,
  # infra/.env의 관리자 계정으로 토큰을 얻을 수 있으면 200까지 확인한다.
  TOKEN=""
  if [ -f "$ROOT/infra/.env" ]; then
    A_ID=$(grep -E '^ADMIN_ID=' "$ROOT/infra/.env" | cut -d= -f2- | tr -d '"'"'"'')
    A_PW=$(grep -E '^ADMIN_PASSWORD=' "$ROOT/infra/.env" | cut -d= -f2- | tr -d '"'"'"'')
    if [ -n "${A_ID:-}" ] && [ -n "${A_PW:-}" ]; then
      TOKEN=$(curl -s -X POST "$API/api/v1/auth/login" -H 'Content-Type: application/json' \
        -d "{\"id\":\"$A_ID\",\"password\":\"$A_PW\"}" 2>/dev/null \
        | python3 -c 'import json,sys
try: print((json.load(sys.stdin).get("data") or {}).get("accessToken") or "")
except Exception: print("")' 2>/dev/null)
    fi
  fi
  [ -n "$TOKEN" ] && pass "관리자 토큰 획득" || note "관리자 토큰 없음 — 인증 200 검사는 건너뜀"

  smoke_auth() {
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' "$API$1" 2>/dev/null)
    if [ "$code" = "401" ]; then pass "GET $1 (무인증) → 401 보호됨"
    else fail "GET $1 (무인증) → $code — 인증 가드가 열려 있음"; fi
    [ -z "$TOKEN" ] && return
    code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" "$API$1" 2>/dev/null)
    if [ "$code" = "200" ]; then pass "GET $1 (인증) → 200"; else fail "GET $1 (인증) → $code"; fi
  }
  smoke_auth "/api/v1/users/me/wishlists"
  smoke_auth "/api/v1/users/me/itineraries"
  smoke_auth "/api/v1/users/me/reviews"
else
  fail "API not running (start: cd apps/api && ./gradlew bootRun)"
fi

echo "----------------------------------------"
if [ "$FAIL" = "0" ]; then echo "VERIFY: ALL PASS"; else echo "VERIFY: FAIL"; fi
exit $FAIL
