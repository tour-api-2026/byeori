import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import KakaoMapView, { type KakaoMapHandle } from '@/components/KakaoMapView';
import LoginRequired from '@/components/LoginRequired';
import type { RouteLeg, RouteStop } from '@/lib/api/itineraries';
import { useItineraryQuery, useItineraryRouteQuery } from '@/lib/hooks/queries';
import { useT } from '@/lib/i18n';
import { useMapLabels } from '@/lib/map/useMapLabels';
import type { TFn } from '@/lib/i18n/labels';
import { useAuthStore } from '@/lib/store/authStore';
import { ROUTE_SEGMENT_COLORS, segmentColor } from '@/lib/routeColors';
import { colors, fonts, radius, space } from '@/lib/theme';

const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY ?? '';
// WebView 문서의 origin. 카카오 개발자센터 Web 플랫폼 사이트 도메인에 등록된 값과 일치해야 한다.
const KAKAO_WEB_ORIGIN = 'https://byeori.seoulride.site';

const fmtKm = (m: number) => (m / 1000).toFixed(1);
// 어순이 언어마다 달라 한 문장으로 사전에 둔다(시간·분을 이어 붙이지 않는다).
const fmtMin = (t: TFn, s: number) => {
  const m = Math.round(s / 60);
  return m >= 60
    ? t('common.duration.hourMin', { h: Math.floor(m / 60), m: m % 60 })
    : t('common.duration.min', { m });
};

type Mode = 'AUTO' | 'WALK' | 'DRIVE';

/** 하루치 경로. 지도에 그대로 넘길 수 있는 모양으로 미리 잘라 둔다. */
type Day = {
  date: string;
  stops: RouteStop[];
  legs: RouteLeg[];
  path: [number, number][];
  distance: number;
  duration: number;
};

/**
 * 하루씩 끊는다.
 *
 * 사흘짜리를 한 번에 그리면 지도가 전국을 담아(서울↔전주면 세로 200km) 방문지가 모두
 * 점이 된다. 그래서 날짜별로 나눠 그 날 것만 그린다 — 날짜를 넘는 구간(전주로 내려가는
 * 200km)은 어느 날에도 속하지 않으므로 아예 그리지 않는다.
 *
 * path 는 구간들을 이어붙인 한 줄이고 legs[i].points 가 구간 i 가 거기 보탠 점의 수다.
 * 첫 구간은 제 경로 전부, 이후 구간은 앞 구간과 겹치는 이음점을 뺀 만큼이라, 중간부터
 * 자를 때는 이음점 한 점을 앞에서 되찾아야(start-1) 선이 첫 방문지에서 시작한다.
 * 그만큼 첫 구간의 points 도 하나 늘려 적는다 — 지도는 points 의 합과 path 길이가 같을
 * 때만 구간별로 자르고, 어긋나면 전부 한 줄(점선)로 그린다.
 *
 * 방문지 번호와 색은 날마다 1번부터 다시 센다. 2일차에 5·6·7이 떠 있으면 그 날 몇 곳을
 * 도는지 세어 봐야 알 수 있다.
 */
function splitByDay(
  stops: RouteStop[], legs: RouteLeg[], path: [number, number][],
): Day[] {
  if (!stops.length) return [];
  const ordered = [...stops].sort((a, b) => a.order - b.order);
  // cum[k] = legs[0..k-1] 이 보탠 점의 수 = 구간 k 가 path 에서 시작하는 자리.
  const cum = [0];
  legs.forEach((l) => cum.push(cum[cum.length - 1] + l.points));
  const sliceable =
    path.length > 1 && legs.length === ordered.length - 1
    && legs.every((l) => l.points > 0) && cum[legs.length] === path.length;

  const days: Day[] = [];
  let from = 0;
  for (let i = 1; i <= ordered.length; i++) {
    if (i < ordered.length && ordered[i].visitDate === ordered[from].visitDate) continue;
    const dayStops = ordered.slice(from, i)
      .map((s, k) => ({ ...s, order: k }));   // 번호를 1번부터 다시
    const dayLegs = legs.slice(from, i - 1);
    days.push({
      date: ordered[from].visitDate,
      stops: dayStops,
      legs: sliceable
        ? dayLegs.map((l, k) => (k === 0 && from > 0 ? { ...l, points: l.points + 1 } : l))
        : dayLegs,
      path: sliceable
        ? path.slice(from === 0 ? 0 : cum[from] - 1, cum[i - 1])
        : [],
      distance: dayLegs.reduce((n, l) => n + l.distance, 0),
      duration: dayLegs.reduce((n, l) => n + l.duration, 0),
    });
    from = i;
  }
  return days;
}

/** '2026-10-20' → '10.20' */
const fmtDate = (d: string) => d.slice(5).replace('-', '.');

export default function RouteScreen() {
  const params = useLocalSearchParams<{ id?: string }>();
  const t = useT();
  const mapLabels = useMapLabels();
  const id = Number(params.id);
  const valid = !!params.id && !Number.isNaN(id);

  // 전에는 '내 주변' 탭의 파라미터여서 로그인한 사람만 닿을 수 있었다. 이제 제 주소를
  // 가진 화면이라 로그아웃 상태로도 열린다 — 그러면 경로 요청이 401 로 떨어지고 화면에는
  // "불러오지 못했어요" 만 남아, 로그인하면 된다는 것을 알 길이 없다.
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);

  const [mode, setMode] = useState<Mode>('AUTO');
  const [dayIdx, setDayIdx] = useState(0);
  // 로그아웃 상태에서는 묻지 않는다 — 어차피 401 이고, 재시도까지 네 번 헛돈다.
  const asking = valid && isLoggedIn ? id : 0;
  const detail = useItineraryQuery(asking);
  const route = useItineraryRouteQuery(asking, mode);

  const webRef = useRef<KakaoMapHandle>(null);
  const [ready, setReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);

  const days = useMemo(
    () => (route.data ? splitByDay(route.data.stops, route.data.legs, route.data.path) : []),
    [route.data],
  );
  // 모드를 바꾸면 날짜 수는 그대로지만, 방문지를 고치고 돌아오면 줄어들 수 있다.
  const day = days[Math.min(dayIdx, Math.max(days.length - 1, 0))];

  useEffect(() => {
    if (!ready || !webRef.current || !day) return;
    const payload = JSON.stringify({ path: day.path, stops: day.stops, legs: day.legs });
    webRef.current.injectJavaScript(`window.drawRoute(${payload}); true;`);
  }, [ready, day]);

  const onMessage = (e: { nativeEvent: { data: string } }) => {
    try {
      const msg = JSON.parse(e.nativeEvent.data);
      if (msg.type === 'ready') { setReady(true); setMapError(null); }
      if (msg.type === 'error') setMapError(String(msg.msg));
    } catch {
      /* ignore */
    }
  };

  if (!isLoggedIn) {
    return (
      <View style={styles.root}>
        <Stack.Screen options={{ title: t('route.title') }} />
        <LoginRequired description={t('route.login.desc')} />
      </View>
    );
  }

  return (
    <View style={styles.root}>
      <Stack.Screen options={{ title: detail.data?.title ?? t('route.title') }} />

      {/* 날짜 탭 — 하루짜리 루트에서는 고를 것이 없으므로 숨긴다. */}
      {days.length > 1 && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          // 가로 ScrollView 는 높이를 안 주면 세로로 늘어나 지도를 화면 밖으로 밀어낸다.
          style={styles.bar}
          contentContainerStyle={styles.dayBar}
        >
          {days.map((d, i) => {
            const on = d.date === day?.date;
            return (
              <Pressable
                key={d.date}
                onPress={() => setDayIdx(i)}
                style={[styles.dayChip, on && styles.dayChipOn]}
              >
                <Text style={[styles.dayText, on && styles.dayTextOn]}>
                  {t('route.day', { n: i + 1 })}
                </Text>
                <Text style={[styles.daySub, on && styles.dayTextOn]}>
                  {fmtDate(d.date)}
                </Text>
              </Pressable>
            );
          })}
        </ScrollView>
      )}

      {/* 그 날의 요약 + 이동 수단 */}
      <View style={styles.summary}>
        <View style={{ flex: 1 }}>
          {route.isLoading ? (
            <Text style={styles.summaryText}>{t('route.calculating')}</Text>
          ) : day ? (
            <Text style={styles.summaryText}>
              {t('route.summary', { km: fmtKm(day.distance), min: fmtMin(t, day.duration), n: day.stops.length })}
            </Text>
          ) : (
            <Text style={styles.summaryText}>{t('route.failed')}</Text>
          )}
          <Text style={styles.summaryHint}>{t('route.legend')}</Text>
        </View>
        <View style={styles.modes}>
          {([['AUTO', 'route.mode.auto'], ['WALK', 'route.mode.walk'], ['DRIVE', 'route.mode.drive']] as const).map(([v, label]) => (
            <Pressable
              key={v}
              hitSlop={6}
              onPress={() => setMode(v)}
              style={[styles.modeChip, mode === v && styles.modeChipOn]}
            >
              <Text style={[styles.modeText, mode === v && styles.modeTextOn]}>{t(label)}</Text>
            </Pressable>
          ))}
        </View>
      </View>

      {/* 지도 */}
      <View style={styles.map}>
        {KAKAO_JS_KEY && !mapError ? (
          <>
            <KakaoMapView
              ref={webRef}
              jsKey={KAKAO_JS_KEY}
              segColors={ROUTE_SEGMENT_COLORS}
              baseUrl={KAKAO_WEB_ORIGIN}
              labels={mapLabels}
              onMessage={onMessage}
              onError={setMapError}
            />
            {!ready && (
              <View style={styles.mapOverlay} pointerEvents="none">
                <Text style={styles.mapOverlayText}>{mapError ?? t('route.map.loading')}</Text>
              </View>
            )}
          </>
        ) : (
          <View style={styles.mapOverlay}>
            <Text style={styles.mapOverlayText}>
              {mapError ?? t('route.map.unavailable')}
            </Text>
          </View>
        )}
      </View>

      {/* 그 날의 방문 순서 */}
      {!!day?.stops.length && (
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.bar}
          contentContainerStyle={styles.stopBar}
        >
          {day.stops.map((s, i) => (
            <View key={`${s.targetType}-${s.targetId}-${i}`} style={styles.stopPill}>
              <View style={[styles.stopNum, { backgroundColor: segmentColor(i) }]}>
                <Text style={styles.stopNumText}>{i + 1}</Text>
              </View>
              <Text style={styles.stopName} numberOfLines={1}>{s.name ?? t('routes.stop.unnamed')}</Text>
              {i < day.stops.length - 1 && (
                <Ionicons name="chevron-forward" size={12} color={colors.textFaint} />
              )}
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.bg },

  // 가로 막대는 제 내용만큼만 차지한다(flex 부모 안에서 늘어나지 않게).
  bar: { flexGrow: 0, flexShrink: 0 },
  dayBar: { paddingHorizontal: space.md, paddingTop: space.sm, gap: 6, alignItems: 'center' },
  dayChip: {
    paddingHorizontal: 14, paddingVertical: 6, borderRadius: radius.pill,
    backgroundColor: colors.bgCard, borderWidth: 1, borderColor: colors.border,
    alignItems: 'center',
  },
  dayChipOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  dayText: { fontFamily: fonts.bold, fontSize: 13, color: colors.text },
  daySub: { fontFamily: fonts.regular, fontSize: 10, color: colors.textFaint },
  dayTextOn: { color: colors.white },

  summary: {
    flexDirection: 'row', alignItems: 'center', gap: space.sm,
    paddingHorizontal: space.md, paddingVertical: space.sm,
  },
  summaryText: { fontFamily: fonts.bold, fontSize: 14, color: colors.text },
  summaryHint: { fontFamily: fonts.regular, fontSize: 11, color: colors.textFaint, marginTop: 2 },
  modes: { flexDirection: 'row', gap: 4 },
  modeChip: {
    paddingHorizontal: 10, paddingVertical: 5, borderRadius: radius.pill,
    backgroundColor: colors.bgCard, borderWidth: 1, borderColor: colors.border,
  },
  modeChipOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  modeText: { fontFamily: fonts.medium, fontSize: 12, color: colors.textSub },
  modeTextOn: { color: colors.white },

  map: { flex: 1, overflow: 'hidden' },
  mapOverlay: {
    position: 'absolute', left: 0, right: 0, top: 0, bottom: 0,
    alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bgCard,
  },
  mapOverlayText: { fontFamily: fonts.medium, fontSize: 13, color: colors.textSub },

  stopBar: { paddingHorizontal: space.md, paddingVertical: space.sm, gap: 4, alignItems: 'center' },
  stopPill: { flexDirection: 'row', alignItems: 'center', gap: 4, maxWidth: 180 },
  stopNum: { width: 18, height: 18, borderRadius: 9, alignItems: 'center', justifyContent: 'center' },
  stopNumText: { fontFamily: fonts.bold, fontSize: 10, color: colors.white },
  stopName: { fontFamily: fonts.medium, fontSize: 12, color: colors.text, flexShrink: 1 },
});
