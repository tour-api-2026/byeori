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
  # 신규 도메인(루프가 추가하면 통과해야 함)
  smoke "/api/v1/users/me/wishlists"
  smoke "/api/v1/users/me/itineraries"
  smoke "/api/v1/users/me/reviews"
else
  fail "API not running (start: cd apps/api && ./gradlew bootRun)"
fi

echo "----------------------------------------"
if [ "$FAIL" = "0" ]; then echo "VERIFY: ALL PASS"; else echo "VERIFY: FAIL"; fi
exit $FAIL
