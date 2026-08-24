# Play Store 등록정보 자산

Google Play Console 스토어 등록정보에 제출하는 이미지와 그 원본입니다.

## 파일

| 파일 | 규격 | 용도 |
|---|---|---|
| `feature-graphic-1024x500.png` | 1024×500, RGB 24bit | **그래픽 이미지**(필수). 없으면 등록정보를 저장할 수 없다 |
| `screenshots/01-home.png` | 1000×1860 | 홈 — 전통 테마 행사 섹션 |
| `screenshots/02-traditional-events.png` | 1000×1860 | 전통 테마 행사 목록 |
| `screenshots/03-venue-detail.png` | 1000×1860 | 장소 상세 — 한복 혜택·진행 중인 행사 |
| `screenshots/04-explore.png` | 1000×1860 | 루트 탐색 |
| `feature-graphic.html` | — | 그래픽 이미지 조판 원본 |

> 휴대전화 스크린샷은 Play 규정상 **가로:세로 비율이 2:1을 넘으면 안 된다.**
> 캡처 원본(1000×2020, 2.02)은 제한을 넘어 1860px로 잘라 1.86로 맞춘 것이다.

## 스크린샷 다시 찍기

앱을 웹으로 띄워 헤드리스 Chrome으로 캡처한다. 운영 API는 자가호스팅이라
Windows 브라우저에서 닿지 않을 수 있으므로 **로컬 백엔드**를 가리키게 한다.

```bash
cd infra && docker compose up -d          # 로컬 API(127.0.0.1:8080)
cd ../apps/app
EXPO_PUBLIC_API_URL=http://localhost:8080/api/v1 \
EXPO_PUBLIC_KAKAO_JS_KEY=c67499ab00bd0367a2de44ba3990169d \
EXPO_PUBLIC_KAKAO_NATIVE_KEY=7f769acfbde8a4f7d9dedba42e0580a4 \
npx expo start --web --port 8082
```

```bash
CHROME=~/.cache/ms-playwright/chromium-1208/chrome-linux64/chrome
"$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size=500,1010 --virtual-time-budget=15000 \
  --screenshot=01-home.png http://localhost:8082
```

⚠️ **헤드리스 Chrome의 뷰포트는 `--window-size`보다 세로 87px 작다.**
원하는 CSS 뷰포트가 500이면 `--window-size=...,587`로 준다. 또한 폭을 412로 주면
레이아웃이 잘리므로 **500 이상**을 쓴다. 캡처 후 하단 여백을 1860px로 잘라낸다.

## 그래픽 이미지 다시 만들기

`feature-graphic.html`은 폰트·아이콘·스크린샷을 모두 상대경로로 참조하므로
저장소만 있으면 그대로 재생성된다(픽셀 동일 확인됨).

```bash
cd docs/store
CHROME=~/.cache/ms-playwright/chromium-1208/chrome-linux64/chrome
"$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --allow-file-access-from-files --force-device-scale-factor=2 \
  --window-size=1024,587 --virtual-time-budget=9000 \
  --screenshot=/tmp/fg-2x.png "file://$PWD/feature-graphic.html"

python3 -c "
from PIL import Image
im = Image.open('/tmp/fg-2x.png').convert('RGB').crop((0,0,2048,1000))
im.resize((1024,500), Image.LANCZOS).save('feature-graphic-1024x500.png')"
```

2배로 렌더한 뒤 축소해야 텍스트가 선명하다. 알파 채널이 있으면 Play가 거부하므로
`convert('RGB')`를 반드시 거친다.

## 문구

스토어 등록정보 문구(간단한 설명 80자 / 자세한 설명 4000자)는 이 디렉터리가 아니라
Play Console에 직접 입력한다. 자세한 설명에는 **KOPIS·한국관광공사 출처 표기**가
포함되어야 한다(데이터 이용 의무). 루트 [`README.md`](../../README.md) 참고.
