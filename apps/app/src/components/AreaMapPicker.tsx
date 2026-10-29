import { Ionicons } from '@expo/vector-icons';
import { useCallback, useEffect, useRef, useState } from 'react';
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

  /**
   * 코드로 마지막에 옮긴 좌표.
   *
   * 지도를 코드로 옮기면 그 직후에도 idle 이 온다. 그 메아리를 "사용자가 옮겼다"로 받으면
   * 칩을 누르자마자 이름이 '지도에서 고른 곳'으로 바뀐다. 그래서 들어온 좌표가 우리가
   * 방금 설정한 값과 같으면 흘려보낸다.
   *
   * 처음엔 idle 횟수를 세어 건너뛰었는데 깨졌다 — 지도가 보내는 idle 수가 상황마다
   * 달라서(처음 뜰 때와 setCenter 가 한 번으로 합쳐지기도 한다) 하나 더 먹으면 **진짜
   * 사용자 조작까지 삼킨다.** 실제로 운영에서 지도를 부산으로 옮겨도 이름이 안 바뀌었다.
   * 좌표를 비교하면 몇 번 오든 상관이 없다.
   */
  const applied = useRef<{ lat: number; lng: number } | null>(null);

  const center = useCallback((la: number, ln: number) => {
    applied.current = { lat: la, lng: ln };
    ref.current?.injectJavaScript(
      `if(window.map){map.setCenter(new kakao.maps.LatLng(${la},${ln}));}true;`);
  }, []);

  // 칩으로 지역을 고르면 지도도 그리로 옮긴다. 안 옮기면 지도가 엉뚱한 곳을 비추면서
  // 아래 이름만 바뀌어, 무엇이 선택된 것인지 알 수 없다.
  useEffect(() => {
    if (ready) center(lat, lng);
  }, [ready, lat, lng, center]);

  const onMessage = useCallback((e: { nativeEvent: { data: string } }) => {
    let msg: { type?: string; lat?: number; lng?: number; msg?: string };
    try {
      msg = JSON.parse(e.nativeEvent.data);
    } catch {
      return;   // 지도 스크립트가 우리가 모르는 것을 보낼 수 있다. 무시한다.
    }
    if (msg.type === 'ready') {
      setReady(true);   // 가운데로 옮기는 일은 위 useEffect 가 한다
      return;
    }
    if (msg.type === 'error') {
      setError(msg.msg ?? t('map.loading'));
      return;
    }
    // 움직임이 멎었다 — 지금 한가운데가 고른 곳이다.
    if (msg.type === 'idle' && typeof msg.lat === 'number' && typeof msg.lng === 'number') {
      // 우리가 방금 옮긴 자리의 메아리면 흘려보낸다. 11m 는 setCenter 가 그대로 돌아오는
      // 오차를 덮을 만큼 작아, 사용자가 조금 민 것까지 삼키지 않는다.
      const a = applied.current;
      if (a && Math.abs(a.lat - msg.lat) < 0.0001 && Math.abs(a.lng - msg.lng) < 0.0001) return;
      applied.current = null;
      onPick({ lat: msg.lat, lng: msg.lng });
    }
  }, [onPick, t]);

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
      {/* 지도 배경이 밝든 어둡든 보이게 흰 핀을 뒤에 한 겹 깐다. 테두리처럼 보인다. */}
      <View style={styles.pinWrap} pointerEvents="none">
        <View>
          <Ionicons name="location" size={40} color={colors.white} />
          <View style={styles.pinFront}>
            <Ionicons name="location" size={32} color={colors.primary} />
          </View>
        </View>
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
  // zIndex 가 필요하다. 카카오 지도는 자기 요소에 높은 z-index 를 주므로, 이것을 빼면
  // 지도가 뜨는 순간 핀도 힌트도 지도 아래로 깔려 **보이지 않는다**(운영에서 그랬다).
  // 지도가 실패해 회색 상자만 있을 때는 멀쩡히 보여서, 로컬 미리보기로는 못 잡는 종류다.
  pinWrap: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, zIndex: 10,
    alignItems: 'center', justifyContent: 'center',
    // 핀의 뾰족한 끝이 한가운데를 가리키게 아이콘 높이의 절반만큼 올린다
    paddingBottom: 40,
  },
  // 흰 핀 위에 겹쳐 놓는다. 크기 차이의 절반만큼 밀어 가운데를 맞춘다.
  pinFront: { position: 'absolute', left: 4, top: 4 },
  overlay: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0, zIndex: 10,
    alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bgSoft,
  },
  overlayText: { fontFamily: fonts.medium, fontSize: 13, color: colors.textFaint },
  hint: {
    position: 'absolute', left: 8, right: 8, bottom: 8, zIndex: 10,
    backgroundColor: 'rgba(255,255,255,0.92)', borderRadius: radius.sm,
    paddingVertical: 5, paddingHorizontal: 10,
  },
  hintText: { fontFamily: fonts.regular, fontSize: 11, color: colors.textSub, textAlign: 'center' },
});
