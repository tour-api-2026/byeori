# AI 루트(하루 코스) 생성 — 설계

## 목표
지역·테마·날짜를 고르면 벼리가 보유한 장소·행사로 하루 코스(4~6곳)를 만들어 주고,
저장하면 기존 일정(지도·이동 경로)으로 이어진다.

## 원칙: AI가 장소를 지어내지 않는다
1. 서버가 DB에서 후보를 뽑는다 — 중심 반경 3km, 사진 있는 장소를 테마별로 나눠 최대 40곳(무작위),
   그날 열리는 행사 최대 8건.
2. OpenAI(기본 gpt-4o-mini, structured outputs strict)가 후보 ID 안에서만 골라 순서·시각·추천 이유를 쓴다.
3. 서버가 응답 ID를 후보와 대조한다. 없는 ID·중복은 버리고, 3곳 미만이면 실패. 이름·사진·좌표는 DB 값.

## API
- `GET /api/v1/ai/routes/status` (공개) → `{enabled, remainingToday}` — 키가 없으면 앱이 버튼을 숨긴다.
- `POST /api/v1/ai/routes` (로그인) → 미리보기. 저장하지 않는다.
- 저장: 기존 `POST /api/v1/itineraries` 에 `sourceType=AI`, `items[]`(추천 이유는 memo).

## 비용 상한 (1회 약 0.8원)
- 생성은 로그인 필수
- 사용자당 하루 10회, 서비스 전체 하루 300회(메모리 카운터, `AI_DAILY_LIMIT_*`)
- 같은 조건(좌표 약 100m·테마·날짜)은 1시간 재사용 — 비용 0, 한도 차감 없음. "다시 만들기"는 새로 생성
- 후보 부족·AI 실패 시 한도를 쓰지 않는다
- OpenAI 대시보드 월 예산 한도(운영자 설정)

## 설정
`infra/.env`: `OPENAI_API_KEY`(필수), `OPENAI_MODEL`, `AI_DAILY_LIMIT_PER_USER`, `AI_DAILY_LIMIT_TOTAL`

## 테스트
`AiRouteServiceTest` — 지어낸 ID·중복 제거, 최소 개수, 시각 검증, 캐시 적중, 한도 초과, 실패 시 환불, 후보 부족 시 미호출, 입력 검증, 스키마 strict 규칙.
