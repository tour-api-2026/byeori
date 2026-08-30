import { ScrollViewStyleReset } from 'expo-router/html';
import type { PropsWithChildren } from 'react';

/**
 * 웹 정적 export의 HTML 문서 껍데기. 모든 페이지가 이 head를 공유한다.
 *
 * 기본 export에는 PWA 관련 태그가 전혀 들어가지 않아 휴대폰에서 "브라우저로 연 웹사이트"로
 * 보인다. manifest·아이콘·상태바 색을 여기서 붙여 홈 화면에 추가하면 주소창 없이
 * 앱처럼 뜨도록 한다.
 */
export default function Root({ children }: PropsWithChildren) {
  return (
    <html lang="ko">
      <head>
        <meta charSet="utf-8" />
        <meta httpEquiv="X-UA-Compatible" content="IE=edge" />
        {/* viewport-fit=cover: 노치 있는 기기에서 화면 끝까지 채운다.
            maximum-scale=1: iOS가 입력창 포커스 때 확대해 버리는 동작을 막는다. */}
        <meta
          name="viewport"
          content="width=device-width, initial-scale=1, maximum-scale=1, viewport-fit=cover"
        />

        <title>벼리 — 한국 전통문화 여행 가이드</title>
        <meta name="description" content="전국 관광 명소와 전통 공연·축제를 지도에서 찾는 한국 여행 가이드" />

        {/* ── PWA ── */}
        <link rel="manifest" href="/manifest.json" />
        <meta name="theme-color" content="#263176" />
        <meta name="application-name" content="벼리" />

        {/* iOS는 manifest의 display/아이콘을 아직 다 따르지 않아 별도 태그가 필요하다. */}
        <meta name="mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="default" />
        <meta name="apple-mobile-web-app-title" content="벼리" />
        <link rel="apple-touch-icon" href="/icons/apple-touch-icon.png" />
        <link rel="icon" type="image/png" sizes="192x192" href="/icons/icon-192.png" />

        {/* ── 공유 미리보기 ── */}
        <meta property="og:type" content="website" />
        <meta property="og:site_name" content="벼리" />
        <meta property="og:title" content="벼리 — 한국 전통문화 여행 가이드" />
        <meta property="og:description" content="전국 관광 명소와 전통 공연·축제를 지도에서 찾는 한국 여행 가이드" />
        <meta property="og:image" content="/icons/icon-512.png" />

        {/* RN Web의 body 스크롤 리셋 — 제거하면 앱 안 스크롤이 깨진다. */}
        <ScrollViewStyleReset />

        {/* 데스크톱 판별. 폭으로 추측하면 안 된다 —
            디스플레이 크기를 키운 휴대폰은 CSS 뷰포트가 900px을 넘기도 하고,
            hover/pointer로 보면 터치스크린 노트북이 휴대폰으로 잡힌다.
            기기 종류를 직접 보고 html에 표식을 남긴다. 렌더 전에 실행돼 깜빡임이 없다. */}
        <script dangerouslySetInnerHTML={{ __html: DEVICE_FLAG }} />

        <style dangerouslySetInnerHTML={{ __html: BODY_STYLE }} />
      </head>
      <body>{children}</body>
    </html>
  );
}

/**
 * 넓은 화면에서 앱이 가로로 늘어나면 데스크톱 웹사이트처럼 보인다.
 * 모바일 앱 레이아웃 그대로 가운데 고정폭 기둥에 담는다(캐치테이블 등 모바일 웹앱과 같은 방식).
 * 탭바·플로팅 버튼은 모두 #root 안에 있으므로 함께 기둥 폭으로 제한된다.
 */
/**
 * 휴대폰이 아닐 때만 html에 is-desktop 을 붙인다.
 * userAgentData.mobile 이 있으면 그걸 우선 쓰고, 없으면 UA 문자열로 판별한다.
 * 실패하면 표식을 붙이지 않는다 = 전체 폭(모바일 레이아웃) — 안전한 쪽으로 기운다.
 */
const DEVICE_FLAG = `
try {
  var uad = navigator.userAgentData;
  var ua = navigator.userAgent || '';
  var isMobile = (uad && uad.mobile === true)
    || /Android|iPhone|iPod|Windows Phone|Mobile|Silk|Kindle|BlackBerry|Opera Mini/i.test(ua);
  // 브라우저의 "데스크톱 사이트" 모드를 켜면 UA가 데스크톱으로 바뀌어 위 판별이 뚫린다.
  // 터치 하드웨어는 못 숨기므로 주 입력이 손가락이면(coarse) 무조건 모바일로 본다.
  // 마우스 달린 터치스크린 노트북은 주 포인터가 fine이라 여기 걸리지 않는다.
  var coarse = window.matchMedia && matchMedia('(pointer: coarse)').matches;
  if (!isMobile && !coarse) document.documentElement.classList.add('is-desktop');
} catch (e) {}
`;

const BODY_STYLE = `
html, body { background-color: #F8F8FB; }
body { overscroll-behavior-y: none; }

/* 데스크톱(html.is-desktop)에서만 모바일 앱 레이아웃을 가운데 기둥에 담는다.
   휴대폰에는 이 표식이 붙지 않으므로 화면이 아무리 넓어도 전체 폭을 그대로 쓴다.
   창을 좁히면(600px 미만) 데스크톱에서도 기둥을 풀어 전체 폭으로 돌아간다. */
@media (min-width: 600px) {
  html.is-desktop { background-color: #E4E6EE; }
  html.is-desktop body { background-color: transparent; }
  html.is-desktop #root {
    max-width: 480px;
    margin: 0 auto;
    background-color: #F8F8FB;
    box-shadow: 0 0 0 1px rgba(26,26,31,.06), 0 12px 40px rgba(26,26,31,.14);
  }
}
`;
