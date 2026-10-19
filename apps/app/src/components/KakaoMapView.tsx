import { forwardRef, useMemo } from 'react';
import { WebView } from 'react-native-webview';
import { buildHtml, type MapLabels } from '@/lib/map/kakaoMapSource';

/** 지도에 명령을 밀어 넣는 핸들. WebView의 injectJavaScript와 같은 모양이라 호출부가 갈리지 않는다. */
export type KakaoMapHandle = { injectJavaScript: (js: string) => void };

export type KakaoMapProps = {
  jsKey: string;
  segColors: string[];
  /** 지도가 보내는 메시지(ready/select/error 등). WebView onMessage와 같은 형태. */
  onMessage: (e: { nativeEvent: { data: string } }) => void;
  onError?: (msg: string) => void;
  /** WebView 문서의 origin — 카카오 콘솔에 등록된 도메인과 일치해야 한다. */
  baseUrl: string;
  /** 지도 문구(번역본). 필수 — 선택으로 두면 빠뜨린 화면이 빈 문구를 조용히 내보낸다. */
  labels: MapLabels;
};

/**
 * 네이티브: 카카오 지도 SDK를 WebView 문서로 띄운다.
 * 웹 구현은 KakaoMapView.web.tsx — 같은 스크립트를 페이지에서 직접 실행한다.
 */
export default forwardRef<KakaoMapHandle, KakaoMapProps>(function KakaoMapView(
  { jsKey, segColors, onMessage, onError, baseUrl, labels },
  ref,
) {
  // html 이 바뀌면 WebView 가 문서를 다시 읽어 지도가 새로 뜬다. labels 는 언어가 바뀔 때만 새 객체다.
  const html = useMemo(() => buildHtml(jsKey, segColors, labels), [jsKey, segColors, labels]);
  return (
    <WebView
      ref={ref as never}
      originWhitelist={['*']}
      source={{ html, baseUrl }}
      onMessage={onMessage}
      javaScriptEnabled
      domStorageEnabled
      mixedContentMode="always"
      setSupportMultipleWindows={false}
      onError={(e) => onError?.(labels.webViewError(e.nativeEvent.description))}
      onHttpError={(e) => onError?.(labels.httpError(String(e.nativeEvent.statusCode)))}
      style={{ flex: 1, backgroundColor: '#EAF0E6' }}
    />
  );
});
