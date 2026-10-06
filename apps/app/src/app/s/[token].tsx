import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Image } from '@/components/Image';
import KakaoMapView, { type KakaoMapHandle } from '@/components/KakaoMapView';
import type { SharedStop } from '@/lib/api/itineraries';
import { useSharedItineraryQuery } from '@/lib/hooks/queries';
import { sized } from '@/lib/img';
import { ROUTE_SEGMENT_COLORS } from '@/lib/routeColors';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const KAKAO_JS_KEY = process.env.EXPO_PUBLIC_KAKAO_JS_KEY ?? '';
const KAKAO_WEB_ORIGIN = 'https://byeori.seoulride.site';

/** 'YYYY-MM-DD' 두 날짜의 일수 차이 + 1 = 며칠째. */
function dayNo(start: string, date: string) {
  return Math.round((Date.parse(date) - Date.parse(start)) / 86400000) + 1;
}

/** 2026-10-12 → 10.12 */
const md = (d: string) => d.slice(5).replace('-', '.');

/**
 * 공유 링크로 들어온 사람이 보는 루트.
 *
 * 편집 화면(itinerary/[id])을 재사용하지 않는다. 그쪽은 달력·장소 추가·편집 시트가 엮인
 * 편집기라, 읽기 전용으로 쓰려면 그 상태를 전부 꺼야 한다. 끄는 조건이 하나라도 새면
 * 남의 루트에 편집 버튼이 보인다 — 읽기만 하는 화면은 애초에 쓰기 경로가 없는 편이 안전하다.
 *
 * 로그인을 묻지 않는다. 벼리를 처음 보는 사람이 링크를 받는 자리라서다.
 */
export default function SharedItineraryScreen() {
  const { token } = useLocalSearchParams<{ token: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { data, isLoading, isError } = useSharedItineraryQuery(String(token ?? ''));
  const signedIn = useAuthStore((s) => !!s.accessToken);

  const mapRef = useRef<KakaoMapHandle>(null);
  const [mapReady, setMapReady] = useState(false);
  // 지도를 접었는지. 카카오 SDK 가 끝내 뜨지 않으면 빈 상자를 남기지 않고 치운다.
  const [mapGone, setMapGone] = useState(false);

  // 좌표가 있는 곳만 지도에 올린다. 방문 순서대로 선이 이어진다.
  const pinned = (data?.stops ?? []).filter((s) => s.lat != null && s.lng != null);

  /**
   * 지도가 끝내 안 뜨면 상자를 치운다.
   *
   * 카카오 SDK 는 등록되지 않은 도메인에서 스크립트 자체가 차단되는데, 그때 onError 조차
   * 오지 않아 녹색 빈 상자만 남는다. 링크를 받은 사람은 그게 고장인지 로딩인지 모른다.
   * 루트 목록은 지도 없이도 온전하므로, 안 뜨면 조용히 접는 편이 낫다.
   */
  useEffect(() => {
    if (mapReady) return;
    const t = setTimeout(() => setMapGone(true), 8000);
    return () => clearTimeout(t);
  }, [mapReady]);

  useEffect(() => {
    if (!mapReady || !mapRef.current || pinned.length === 0) return;
    // path 는 비워 둔다 — drawRoute 는 stops 만 보고 직선으로 잇는다(도로 경로 호출 없음).
    const payload = JSON.stringify({
      path: [],
      stops: pinned.map((s, i) => ({ order: i, lat: s.lat, lng: s.lng })),
    });
    mapRef.current.injectJavaScript(`window.drawRoute(${payload}); true;`);
  }, [mapReady, data]); // eslint-disable-line react-hooks/exhaustive-deps

  if (isLoading) {
    return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;
  }

  if (isError || !data) {
    return (
      <View style={[styles.center, { padding: space.lg }]}>
        <Stack.Screen options={{ title: '공유된 루트' }} />
        <Ionicons name="link-outline" size={40} color={colors.border} />
        <Text style={styles.errorTitle}>링크를 열 수 없어요</Text>
        <Text style={styles.errorBody}>주소가 잘못되었거나, 만든 사람이 루트를 지웠을 수 있어요.</Text>
        <Pressable style={styles.errorBtn} onPress={() => router.replace('/')}>
          <Text style={styles.errorBtnText}>벼리 둘러보기</Text>
        </Pressable>
      </View>
    );
  }

  // 날짜별로 묶는다. 서버가 visitDate, sortOrder 순으로 주므로 순서를 다시 만지지 않는다.
  const byDate: Record<string, SharedStop[]> = {};
  data.stops.forEach((s) => { (byDate[s.visitDate] ||= []).push(s); });
  const dates = Object.keys(byDate).sort();
  const nights = dayNo(data.startDate, data.endDate) - 1;

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '공유된 루트' }} />
      <ScrollView contentContainerStyle={{ paddingBottom: 28 + insets.bottom }}>
        <View style={styles.head}>
          <Text style={styles.title}>{data.title}</Text>
          <Text style={styles.meta}>
            {md(data.startDate)} ~ {md(data.endDate)} · {nights > 0 ? `${nights}박 ${nights + 1}일` : '당일'} · {data.stops.length}곳
          </Text>
        </View>

        {pinned.length > 0 && KAKAO_JS_KEY && !mapGone ? (
          <View style={styles.mapBox}>
            <KakaoMapView
              ref={mapRef}
              jsKey={KAKAO_JS_KEY}
              segColors={ROUTE_SEGMENT_COLORS}
              baseUrl={KAKAO_WEB_ORIGIN}
              onMessage={(e) => {
                try {
                  if (JSON.parse(e.nativeEvent.data)?.type === 'ready') setMapReady(true);
                } catch { /* 지도에서 오는 다른 메시지는 이 화면에서 쓰지 않는다 */ }
              }}
            />
          </View>
        ) : null}

        {dates.map((date) => (
          <View key={date} style={styles.daySection}>
            <View style={styles.dayBar}>
              <Text style={styles.dayBarText}>{dayNo(data.startDate, date)}일차</Text>
              <Text style={styles.dayBarDate}>{date}</Text>
            </View>
            <View style={styles.card}>
              {byDate[date].map((s, i, arr) => (
                <Pressable
                  key={`${s.targetType}-${s.targetId}-${s.sortOrder}`}
                  style={styles.stop}
                  onPress={() => router.push(
                    s.targetType === 'PERFORMANCE' ? `/performances/${s.targetId}` : `/venue/${s.targetId}`,
                  )}>
                  <View style={styles.rail}>
                    {i > 0 && <View style={[styles.rLine, styles.rTop]} />}
                    {i < arr.length - 1 && <View style={[styles.rLine, styles.rBot]} />}
                    <View style={styles.stopNum}><Text style={styles.stopNumText}>{i + 1}</Text></View>
                  </View>
                  {s.imageUrl ? (
                    <Image source={sized(s.imageUrl, 120, 120)} style={styles.thumb} contentFit="cover" transition={200} />
                  ) : (
                    <View style={[styles.thumb, styles.thumbEmpty]}>
                      <Ionicons name="image-outline" size={16} color={colors.border} />
                    </View>
                  )}
                  <View style={{ flex: 1 }}>
                    <Text style={styles.stopName} numberOfLines={2}>{s.name ?? '장소'}</Text>
                    <Text style={styles.stopMeta}>
                      {s.targetType === 'PERFORMANCE' ? '행사' : '장소'}{s.plannedTime ? ` · ${s.plannedTime}` : ''}
                    </Text>
                  </View>
                  <Ionicons name="chevron-forward" size={16} color={colors.textFaint} />
                </Pressable>
              ))}
            </View>
          </View>
        ))}

        {/* 받은 사람이 다음에 할 일. 아직 로그인하지 않았다면 그쪽이 먼저다. */}
        <Pressable
          style={styles.cta}
          onPress={() => router.push(signedIn ? '/(tabs)/routes' : '/login')}>
          <Ionicons name={signedIn ? 'map-outline' : 'log-in-outline'} size={18} color={colors.white} />
          <Text style={styles.ctaText}>
            {signedIn ? '내 루트 보러 가기' : '벼리 시작하고 내 루트 만들기'}
          </Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg, gap: 10 },

  errorTitle: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 6 },
  errorBody: { fontSize: 13, color: colors.textFaint, textAlign: 'center', lineHeight: 20 },
  errorBtn: { marginTop: 14, backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 20, paddingVertical: 11 },
  errorBtnText: { color: colors.white, fontSize: 14, fontFamily: fonts.bold, fontWeight: '800' },

  head: { paddingHorizontal: space.lg, paddingTop: 16, paddingBottom: 14 },
  title: { fontSize: 22, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, lineHeight: 30 },
  meta: { fontSize: 13, color: colors.textFaint, marginTop: 6 },

  mapBox: {
    height: 220,
    marginHorizontal: space.lg,
    borderRadius: radius.lg,
    overflow: 'hidden',
    backgroundColor: '#EAF0E6',
  },

  daySection: { marginTop: 18 },
  dayBar: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    marginHorizontal: space.lg, backgroundColor: colors.text,
    borderTopLeftRadius: radius.md, borderTopRightRadius: radius.md,
    paddingHorizontal: 14, paddingVertical: 10,
  },
  dayBarText: { color: colors.white, fontSize: 14, fontFamily: fonts.bold, fontWeight: '800' },
  dayBarDate: { color: colors.white, fontSize: 12, opacity: 0.75 },
  card: {
    marginHorizontal: space.lg, backgroundColor: colors.white,
    borderBottomLeftRadius: radius.md, borderBottomRightRadius: radius.md,
    paddingHorizontal: 14, paddingVertical: 6,
    ...(Platform.OS === 'web' ? {} : shadow.card),
  },

  stop: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10 },
  rail: { width: 24, alignItems: 'center', alignSelf: 'stretch', justifyContent: 'center' },
  rLine: { position: 'absolute', width: 2, backgroundColor: colors.border, left: 11 },
  rTop: { top: 0, height: '50%' },
  rBot: { bottom: 0, height: '50%' },
  stopNum: { width: 22, height: 22, borderRadius: 11, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  stopNumText: { color: colors.white, fontSize: 11, fontFamily: fonts.bold, fontWeight: '800' },
  thumb: { width: 48, height: 48, borderRadius: radius.sm, backgroundColor: colors.bgSoft },
  thumbEmpty: { alignItems: 'center', justifyContent: 'center' },
  stopName: { fontSize: 14, fontFamily: fonts.semibold, fontWeight: '600', color: colors.text, lineHeight: 20 },
  stopMeta: { fontSize: 12, color: colors.textFaint, marginTop: 2 },

  cta: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8,
    marginTop: 24, marginHorizontal: space.lg,
    backgroundColor: colors.primary, borderRadius: radius.pill, paddingVertical: 14,
  },
  ctaText: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
});
