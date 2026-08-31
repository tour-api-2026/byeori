import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { MAP_CSS, MAP_POST_FN, mapScript } from '@/lib/map/kakaoMapSource';
import type { KakaoMapHandle, KakaoMapProps } from './KakaoMapView';

/**
 * 웹: react-native-webview가 동작하지 않으므로 iframe 없이 페이지에서 직접 지도를 띄운다.
 *
 * 지도 스크립트는 네이티브와 완전히 같은 것을 쓴다(kakaoMapSource). 스크립트가 기대하는 건
 * ① #map 엘리먼트 ② window.ReactNativeWebView.postMessage 두 가지뿐이라,
 * 그 둘만 미리 갖춰 두면 수정 없이 그대로 돈다.
 *
 * iframe을 쓰지 않는 이유: 카카오 SDK는 문서 origin으로 도메인을 검사하는데
 * srcdoc/blob iframe은 origin이 모호해 거부된다. 페이지에서 직접 실행하면
 * origin이 배포 도메인 그대로라 콘솔에 등록한 값과 일치한다.
 */
export default forwardRef<KakaoMapHandle, KakaoMapProps>(function KakaoMapView(
  { jsKey, segColors, onMessage, onError },
  ref,
) {
  // 최신 콜백을 참조만 하고 effect는 다시 돌리지 않는다(지도 재생성 방지).
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;
  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;

  useImperativeHandle(
    ref,
    () => ({
      injectJavaScript: (js: string) => {
        try {
          // 간접 eval — WebView.injectJavaScript와 같이 전역 스코프에서 실행한다.
          (0, eval)(js);
        } catch (e) {
          console.warn('[map] injectJavaScript 실패', e);
        }
      },
    }),
    [],
  );

  useEffect(() => {
    if (!jsKey) return;
    let cancelled = false;

    const w = window as unknown as Record<string, unknown>;
    w.ReactNativeWebView = {
      postMessage: (data: string) => onMessageRef.current?.({ nativeEvent: { data } }),
    };

    // MAP_CSS는 WebView 문서 기준이라 html,body까지 녹색으로 칠한다.
    // 웹에서는 페이지 전체가 물들지 않도록 #map 으로만 한정한다.
    if (!document.getElementById('kakao-map-css')) {
      const style = document.createElement('style');
      style.id = 'kakao-map-css';
      style.textContent = MAP_CSS.replace('html,body,#map{', '#map{');
      document.head.appendChild(style);
    }

    const boot = () => {
      if (cancelled) return;
      try {
        // post()는 mapScript보다 먼저. 네이티브 문서에서는 별도 <script>로 들어간다.
        // window.onerror 후킹은 웹에서 앱 전역 에러까지 지도 오류로 잡아버리므로 옮기지 않는다.
        (0, eval)(MAP_POST_FN);
        (0, eval)(mapScript(segColors));
      } catch (e) {
        onErrorRef.current?.('지도 스크립트 오류: ' + String(e));
      }
    };

    if ((w.kakao as { maps?: unknown } | undefined)?.maps) {
      boot();
      return () => {
        cancelled = true;
      };
    }

    const existing = document.getElementById('kakao-map-sdk') as HTMLScriptElement | null;
    if (existing) {
      existing.addEventListener('load', boot);
      return () => {
        cancelled = true;
        existing.removeEventListener('load', boot);
      };
    }

    const script = document.createElement('script');
    script.id = 'kakao-map-sdk';
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${jsKey}&autoload=false&libraries=services`;
    script.async = true;
    script.onload = boot;
    script.onerror = () =>
      onErrorRef.current?.('카카오 SDK 스크립트 로드 실패 — 콘솔에 이 도메인이 등록되어 있는지 확인하세요.');
    document.head.appendChild(script);

    return () => {
      cancelled = true;
    };
  }, [jsKey, segColors]);

  // react-native-web 환경이지만 이 파일은 웹 전용이라 DOM 엘리먼트를 그대로 쓴다.
  return <div id="map" style={{ width: '100%', height: '100%', backgroundColor: '#EAF0E6' }} />;
});
