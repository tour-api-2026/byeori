import { Ionicons } from '@expo/vector-icons';
import { useCallback, useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import KakaoMapView, { type KakaoMapHandle } from '@/components/KakaoMapView';
import { useT } from '@/lib/i18n';
import { useMapLabels } from '@/lib/map/useMapLabels';
import { ROUTE_SEGMENT_COLORS } from '@/lib/routeColors';
import { colors, fonts, radius } from '@/lib/theme';

const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY ?? '';
/** 카카오 콘솔에 등록된 도메인. 지도 WebView 문서의 origin 이 이것과 같아야 한다. */
const KAKAO_WEB_ORIGIN = 'https://byeori.seoulride.site';

/**
 * 지도를 움직여 '어디를 중심으로 돌지'를 고른다.
 *
 * 핀을 찍는 것이 아니라 **지도를 움직이면 화면 한가운데가 선택**된다(배달앱 주소 선택과
 * 같은 방식). 손가락으로 작은 핀을 정확히 누르는 것보다 모바일에서 쉽고, 핀이 화면 밖으로
 * 나가 길을 잃는 일도 없다.
 *
 * 지도 스크립트가 움직임이 멎을 때마다 중심 좌표를 보내 준다(kakaoMapSource 의 'idle').
 * 그래서 스크립트를 건드리지 않고 그 메시지만 듣는다.
 *
 * 좌표만 올려 보낸다. 그 자리의 지명은 부르는 쪽이 정한다 — 역지오코딩은 호출 비용이
 * 들고, AI 루트는 좌표만 있으면 돌아간다.
 */
export function AreaMapPicker({ lat, lng, onPick }: {
  lat: number;
  lng: number;
  onPick: (c: { lat: number; lng: number }) => void;
}) {
  const t = useT();
  const labels = useMapLabels();
  const ref = useRef<KakaoMapHandle>(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onMessage = useCallback((e: { nativeEvent: { data: string } }) => {
    let msg: { type?: string; lat?: number; lng?: number; msg?: string };
    try {
      msg = JSON.parse(e.nativeEvent.data);
    } catch {
      return;   // 지도 스크립트가 우리가 모르는 것을 보낼 수 있다. 무시한다.
    }
    if (msg.type === 'ready') {
      setReady(true);
      // 고를 지점을 화면 가운데로. 지도는 기본 위치에서 뜨므로 한 번 옮겨 준다.
      ref.current?.injectJavaScript(
        `if(window.map){map.setCenter(new kakao.maps.LatLng(${lat},${lng}));}true;`);
      return;
    }
    if (msg.type === 'error') {
      setError(msg.msg ?? t('map.loading'));
      return;
    }
    // 움직임이 멎었다 — 지금 한가운데가 고른 곳이다.
    if (msg.type === 'idle' && typeof msg.lat === 'number' && typeof msg.lng === 'number') {
      onPick({ lat: msg.lat, lng: msg.lng });
    }
  }, [lat, lng, onPick, t]);

  if (!KAKAO_JS_KEY) return null;

  return (
    <View style={styles.wrap}>
      <KakaoMapView
        ref={ref}
        jsKey={KAKAO_JS_KEY}
        segColors={ROUTE_SEGMENT_COLORS}
        baseUrl={KAKAO_WEB_ORIGIN}
        labels={labels}
        onMessage={onMessage}
        onError={setError}
      />

      {/* 한가운데 고정 핀. 지도 위에 얹을 뿐이라 지도 조작을 막지 않는다(pointerEvents="none").
          핀 끝이 가리키는 곳이 선택 지점이라 높이의 절반만큼 올려 둔다. */}
      <View style={styles.pinWrap} pointerEvents="none">
        <Ionicons name="location" size={34} color={colors.primary} />
      </View>

      {(!ready || error) && (
        <View style={styles.overlay} pointerEvents="none">
          <Text style={styles.overlayText}>{error ?? t('map.loading')}</Text>
        </View>
      )}

      <View style={styles.hint} pointerEvents="none">
        <Text style={styles.hintText}>{t('aiRoute.map.hint')}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    height: 220, borderRadius: radius.md, overflow: 'hidden',
    borderWidth: 1, borderColor: colors.border, backgroundColor: colors.bgSoft,
  },
  pinWrap: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0,
    alignItems: 'center', justifyContent: 'center',
    // 핀의 뾰족한 끝이 한가운데를 가리키게 아이콘 높이의 절반만큼 올린다
    paddingBottom: 34,
  },
  overlay: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0,
    alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bgSoft,
  },
  overlayText: { fontFamily: fonts.medium, fontSize: 13, color: colors.textFaint },
  hint: {
    position: 'absolute', left: 8, right: 8, bottom: 8,
    backgroundColor: 'rgba(255,255,255,0.92)', borderRadius: radius.sm,
    paddingVertical: 5, paddingHorizontal: 10,
  },
  hintText: { fontFamily: fonts.regular, fontSize: 11, color: colors.textSub, textAlign: 'center' },
});
