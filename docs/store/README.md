# Play Store 등록정보 자산

Google Play Console 스토어 등록정보에 제출하는 이미지와 그 원본입니다.

## 파일

| 파일 | 규격 | 용도 |
|---|---|---|
| `app-icon-512.png` | 512×512, RGB 24bit | **앱 아이콘**(필수). 번들 아이콘(`apps/app/assets/images/icon.png`, 1024²)을 축소한 것 |
| `feature-graphic-1024x500.png` | 1024×500, RGB 24bit | **그래픽 이미지**(필수). 없으면 등록정보를 저장할 수 없다 |
| `screenshots/01-home.png` | 1080×1920 | 홈 — 오늘의 추천·전통 테마 행사·맞춤 추천 |
| `screenshots/02-traditional-events.png` | 1080×1920 | 전통 테마 행사 목록 |
| `screenshots/03-venue-detail.png` | 1080×1920 | 장소 상세 — 한복 혜택·운영시간·진행 중인 행사 |
| `screenshots/04-explore.png` | 1080×1920 | 루트 탐색 |
| `feature-graphic.html` | — | 그래픽 이미지 조판 원본 |

> **휴대전화 스크린샷은 9:16 정확히, 각 변 1,080px 이상**으로 맞춰져 있다.
> Play Console이 요구하는 비율은 16:9 또는 9:16이고, 프로모션 자격(추천 후보)에는
> "각 변 1,080px 이상인 스크린샷 4장 이상"이 필요하다. 1080×1920이 두 조건을 동시에 만족한다.

## ⚠️ 헤드리스 Chrome의 뷰포트 크기는 믿지 말 것

`--window-size`와 실제 CSS 뷰포트가 **일치할 때도 있고 87px 작을 때도 있다.**
아래 두 명령은 모두 검증된 것이지만 오프셋이 서로 다르다.

| 명령 | 실제 캡처 | 후처리 |
|---|---|---|
| `--window-size=540,960` | 1080×1920 (오프셋 없음) | 없음 |
| `--window-size=1024,587` | 2048×1174 (뷰포트 500) | 상단 2048×1000 크롭 |

**항상 결과 크기를 확인하고 필요하면 크롭한다.** 크기를 가정하고 넘어가면
아래쪽에 흰 띠가 남거나 콘텐츠가 잘린 채로 제출된다.

## 스크린샷 다시 찍기

앱을 웹으로 띄워 캡처한다. 운영 API는 자가호스팅이라 Windows 브라우저에서
닿지 않을 수 있으므로 **로컬 백엔드**를 가리키게 한다.

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
shoot() {
  "$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=2 --window-size=540,960 --virtual-time-budget=18000 \
    --screenshot="$1.png" "http://localhost:8082$2"
}
shoot 01-home ""
shoot 02-traditional-events "/performances/traditional"
shoot 03-venue-detail "/venue/2"
shoot 04-explore "/explore"
```

캡처 폭을 412 같은 값으로 주면 레이아웃이 잘리므로 **500 이상**을 쓴다.

**지도(`/map`)는 캡처하지 말 것.** 카카오 JS 키에 `localhost:8082`가 등록돼 있지
않아 타일이 뜨지 않고 빈 배경만 나온다. 지도 화면이 필요하면 실기기에서 찍는다.

## 그래픽 이미지 다시 만들기

`feature-graphic.html`은 폰트·아이콘·스크린샷을 모두 상대경로로 참조하므로
저장소만 있으면 그대로 재생성된다.

```bash
cd docs/store
CHROME=~/.cache/ms-playwright/chromium-1208/chrome-linux64/chrome
"$CHROME" --headless=new --no-sandbox --disable-gpu --hide-scrollbars \
  --allow-file-access-from-files --force-device-scale-factor=2 \
  --window-size=1024,587 --virtual-time-budget=12000 \
  --screenshot=/tmp/fg-2x.png "file://$PWD/feature-graphic.html"

python3 -c "
from PIL import Image
im = Image.open('/tmp/fg-2x.png').convert('RGB').crop((0,0,2048,1000))
im.resize((1024,500), Image.LANCZOS).save('feature-graphic-1024x500.png', optimize=True)"
```

2배로 렌더한 뒤 축소해야 텍스트가 선명하다. 알파 채널이 있으면 Play가 거부하므로
`convert('RGB')`를 반드시 거친다.

### 조판 시 주의

- **기기 틀 안쪽 비율을 스크린샷과 똑같이 9:16으로 맞춘다.** 어긋나면
  `object-fit:cover`가 화면 위아래를 잘라내 "앱이 잘린" 그래픽이 된다.
- **회전(`transform:rotate`)을 쓰지 않는다.** 회전은 바운딩 박스를 키워
  기기가 캔버스 밖으로 삐져나간다.
- 좌측 문구의 숫자는 실제 DB 집계와 맞춰야 한다. 현재 값의 근거:
  `select count(*) from performances where traditional = true` → 1,265 (문구 "1,100+"),
  `select count(*) from venues` → 29,466 (문구 "2만 9천+"). 데이터가 줄면 문구도 낮춘다.

## 문구

스토어 등록정보 문구(간단한 설명 80자 / 자세한 설명 4000자)는 이 디렉터리가 아니라
Play Console에 직접 입력한다. 자세한 설명에는 **KOPIS·한국관광공사 출처 표기**가
포함되어야 한다(데이터 이용 의무). 루트 [`README.md`](../../README.md) 참고.
