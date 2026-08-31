import { forwardRef } from 'react';
import { WebView } from 'react-native-webview';
import { buildHtml } from '@/lib/map/kakaoMapSource';

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
};

/**
 * 네이티브: 카카오 지도 SDK를 WebView 문서로 띄운다.
 * 웹 구현은 KakaoMapView.web.tsx — 같은 스크립트를 페이지에서 직접 실행한다.
 */
export default forwardRef<KakaoMapHandle, KakaoMapProps>(function KakaoMapView(
  { jsKey, segColors, onMessage, onError, baseUrl },
  ref,
) {
  return (
    <WebView
      ref={ref as never}
      originWhitelist={['*']}
      source={{ html: buildHtml(jsKey, segColors), baseUrl }}
      onMessage={onMessage}
      javaScriptEnabled
      domStorageEnabled
      mixedContentMode="always"
      setSupportMultipleWindows={false}
      onError={(e) => onError?.('WebView 오류: ' + e.nativeEvent.description)}
      onHttpError={(e) => onError?.('HTTP 오류: ' + e.nativeEvent.statusCode)}
      style={{ flex: 1, backgroundColor: '#EAF0E6' }}
    />
  );
});
