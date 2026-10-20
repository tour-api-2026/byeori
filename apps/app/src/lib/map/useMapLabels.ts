import { useMemo } from 'react';
import { useT } from '@/lib/i18n';
import type { MapLabels } from './kakaoMapSource';

/**
 * 지도에 넘길 번역 문구. 지도 스크립트는 WebView 안에서 돌아 useT() 가 닿지 않으므로
 * 화면이 이 훅으로 만들어 KakaoMapView 의 labels 로 넘긴다.
 * 언어가 바뀔 때만 새 객체가 되게 메모한다(지도가 불필요하게 다시 뜨지 않도록).
 */
export function useMapLabels(): MapLabels {
  const t = useT();
  return useMemo(
    () => ({
      sdkLoadFail: t('map.err.sdkLoad'),
      sdkBlocked: t('map.err.sdkBlocked'),
      webViewError: (detail) => t('map.err.webview', { detail }),
      httpError: (detail) => t('map.err.http', { detail }),
      scriptError: (detail) => t('map.err.script', { detail }),
      injectFail: t('map.err.inject'),
    }),
    [t],
  );
}
