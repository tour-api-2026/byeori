import { Ionicons } from '@expo/vector-icons';
import * as Location from 'expo-location';
import { Image } from '@/components/Image';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from '@/components/Chip';
import { useTabBarHeight } from '@/components/TabBar';
import { SectionHeader } from '@/components/SectionHeader';
import { FeedCarousel } from '@/components/FeedCarousel';
import { PerformanceCarousel } from '@/components/PerformanceCarousel';
import { VenueCard } from '@/components/VenueCard';
import { Performance, VenueCardItem } from '@/lib/api/types';
import { sized } from '@/lib/img';
import { useFeedQuery, useNearbyVenuesQuery, usePerformancesQuery, useVenuesQuery } from '@/lib/hooks/queries';
import { useAuthStore } from '@/lib/store/authStore';
import { useRecentStore } from '@/lib/store/recentStore';
import { REGIONS, regionSpot } from '@/lib/regions';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const KEYWORDS = ['전체', '문화', '카페', '체험', '맛집'];

export default function HomeScreen() {
  const router = useRouter();
  const tabH = useTabBarHeight();
  const [keyword, setKeyword] = useState('전체');
  const [region, setRegion] = useState('전체');

  // 배너 — 앱 성격에 맞게 전통 행사를 먼저 걸고, 진행 중인 게 없을 때만 일반 공연으로 대체.
  const bannerTrad = usePerformancesQuery({ state: 'ONGOING', traditional: true, size: 5 });
  const bannerAny = usePerformancesQuery({ state: 'ONGOING', size: 5 });
  const banner = bannerTrad.data?.content.length ? bannerTrad : bannerAny;
  // 전통 테마 행사 — 진행 중 우선, 없으면 예정으로 대체
  const tradOngoing = usePerformancesQuery({ traditional: true, state: 'ONGOING', size: 10 });
  const tradUpcoming = usePerformancesQuery({ traditional: true, state: 'UPCOMING', size: 10 });
  const traditional = tradOngoing.data?.content.length ? tradOngoing : tradUpcoming;
  const keyworded = useVenuesQuery({ category: keyword === '전체' ? undefined : keyword, size: 6 });
  const all = useVenuesQuery({ size: 50 });

  const top = banner.data?.content?.[0];
  // 배너에 건 공연이 바로 아래 목록 첫 칸에 또 나오면 같은 포스터가 두 번 보인다.
  const traditionalItems = (traditional.data?.content ?? []).filter((p) => p.id !== top?.id);
  // 지역을 고르면 그 좌표로 실시간 조회한다. 저장 목록을 주소로 거르면
  // 상위 50건이 전부 서울이라 서울 외 지역이 비어 보였다.
  const signedIn = useAuthStore((st) => !!st.accessToken);

  /**
   * 추천에 쓸 좌표. **권한을 새로 묻지 않는다** — 홈을 열자마자 권한 창이 뜨면 거슬린다.
   * 지도 화면에서 이미 허락한 사람만 마지막으로 알던 위치를 가져온다. 없으면 좌표 없이
   * 추천하고, 그때는 거리 항이 모두 같은 값이 되어 취향·임박도가 순위를 가른다.
   */
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  useEffect(() => {
    let alive = true;
    (async () => {
      try {
        const { granted } = await Location.getForegroundPermissionsAsync();
        if (!granted) return;
        const last = await Location.getLastKnownPositionAsync();
        if (alive && last) setCoords({ lat: last.coords.latitude, lng: last.coords.longitude });
      } catch { /* 위치를 못 얻어도 추천은 돈다 */ }
    })();
    return () => { alive = false; };
  }, []);

  const feed = useFeedQuery(signedIn, coords);

  const spot = regionSpot(region);
  const byRegion = useNearbyVenuesQuery(spot);
  const regionVenues = useMemo(() => {
    const list = spot ? (byRegion.data ?? []) : (all.data?.content ?? []);
    // 위 '맞춤 추천'·'키워드로 탐색'과 같은 6개로 맞춘다(줄 끝이 어긋나 보이지 않게).
    return list.slice(0, 6);
  }, [spot, byRegion.data, all.data]);
  // 예전에는 저장 목록의 뒤 4개를 뒤집어 보여줘 무엇을 봐도 바뀌지 않았다.
  // 상세 화면에서 기기에 남긴 실제 열람 기록을 쓴다.
  const recentItems = useRecentStore((s) => s.items);
  const hydrateRecent = useRecentStore((s) => s.hydrate);
  useEffect(() => { void hydrateRecent(); }, [hydrateRecent]);
  // Grid 는 Venue 를 받는다. 최근 기록은 카드에 필요한 값만 갖고 있어 나머지를 채운다
  // (평점·리뷰는 그 시점 값을 남길 이유가 없어 저장하지 않는다).
  const recent = useMemo<VenueCardItem[]>(
    () => recentItems.slice(0, 4).map((r) => ({
      id: r.id as number,
      tourContentId: r.tourContentId,
      name: r.name,
      category: r.category,
      imageUrl: r.imageUrl,
      hanbokDiscount: false,
      avgRating: 0,
      reviewCount: 0,
    })),
    [recentItems],
  );

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: tabH + 24 }}>
        {/* 헤더 (중앙 타이틀) */}
        <View style={styles.header}><Text style={styles.title}>벼리</Text></View>

        {/* 검색창 — 카카오 장소 검색(내 주변 지도)로 이동 */}
        <Pressable style={styles.search} onPress={() => router.push('/(tabs)/map')}>
          <Ionicons name="search" size={18} color={colors.accent} />
          <Text style={styles.searchText}>장소·주소로 검색해보세요</Text>
        </Pressable>

        {/* 오늘의 추천 (히어로) */}
        <View style={styles.section}>
          <SectionHeader title="오늘의 추천" />
          {top ? (
            <Pressable style={styles.hero} onPress={() => router.push(`/performances/${top.id}`)}>
              <Image source={sized(top.posterImageUrl, 760, 380)} style={styles.heroImg} contentFit="cover" transition={250} cachePolicy="memory-disk" />
              <View style={styles.heroOverlay}>
                <Text style={styles.heroTitle} numberOfLines={1}>{top.title}</Text>
                <Text style={styles.heroSub} numberOfLines={1}>{top.genre ?? '추천 행사'} · 지금 만나보세요</Text>
                <View style={styles.heroBtn}><Text style={styles.heroBtnText}>보러가기</Text></View>
              </View>
            </Pressable>
          ) : <Loading />}
        </View>

        {/*
          맞춤 추천 — 로그인한 사람에게만. '맞춤'인데 맞출 대상이 없으면 의미가 없다.

          이 이름은 원래 바로 아래에 있던 섹션이 쓰고 있었는데, 그쪽은 useVenuesQuery({size:6})
          라 누가 봐도 같은 장소 6곳이었다. 이름만 맞춤이고 사용자 정보가 한 톨도 안 들어갔다.
          진짜 맞춤이 생겼으니 이름을 이쪽으로 가져오고 그쪽은 지웠다.
        */}
        {signedIn && (feed.isLoading || (feed.data?.length ?? 0) > 0) && (
          <View style={styles.section}>
            <SectionHeader title="맞춤 추천" />
            {feed.data?.length ? <FeedCarousel items={feed.data} /> : <Loading />}
          </View>
        )}

        {/* 전통 테마 행사 */}
        <View style={styles.section}>
          <SectionHeader title="전통 테마 행사" onMore={() => router.push('/performances/traditional')} />
          {traditional.isLoading
            ? <Loading />
            : (traditionalItems.length
              ? <PerformanceCarousel items={traditionalItems} />
              : <Text style={styles.empty}>진행 중인 전통 행사가 아직 없어요</Text>)}
        </View>

        {/* 키워드로 탐색 */}
        <View style={styles.section}>
          <SectionHeader title="키워드로 탐색" onMore={() => router.push('/search')} />
          <Chips items={KEYWORDS} value={keyword} onChange={setKeyword} />
          {keyworded.isLoading
            ? <Loading />
            : (keyworded.data?.content.length
              ? <Grid venues={keyworded.data.content} />
              : <Text style={styles.empty}>해당 키워드의 장소가 아직 없어요</Text>)}
        </View>

        {/* 지역으로 탐색 */}
        <View style={styles.section}>
          <SectionHeader title="지역으로 탐색" onMore={() => router.push('/search')} />
          <Chips items={[...REGIONS]} value={region} onChange={setRegion} />
          {regionVenues.length
            ? <Grid venues={regionVenues} />
            : <Text style={styles.empty}>해당 지역의 장소가 아직 없어요</Text>}
        </View>

        {/* 최근 본 장소 — 아직 본 게 없으면 섹션째 숨긴다(빈 칸이 남지 않게) */}
        {recent.length > 0 && (
          <View style={styles.section}>
            <SectionHeader title="최근 본 장소" onMore={() => router.push('/search')} />
            <Grid venues={recent} />
          </View>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function Grid({ venues }: { venues: VenueCardItem[] }) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.rowScroll} contentContainerStyle={styles.row}>
      {venues.map((v) => <VenueCard key={v.id} venue={v} width={150} />)}
    </ScrollView>
  );
}

function Chips({ items, value, onChange }: { items: string[]; value: string; onChange: (s: string) => void }) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsScroll} contentContainerStyle={styles.chips}>
      {items.map((k) => <Chip key={k} label={k} selected={k === value} onPress={() => onChange(k)} />)}
    </ScrollView>
  );
}

function Loading() {
  return <View style={styles.loading}><ActivityIndicator color={colors.primary} /></View>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: { alignItems: 'center', paddingTop: 10, paddingBottom: 6 },
  title: { fontSize: 20, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, letterSpacing: 1 },
  search: {
    flexDirection: 'row', alignItems: 'center', gap: 8, marginHorizontal: space.lg, marginVertical: 8,
    backgroundColor: colors.white, borderWidth: 1.5, borderColor: colors.accent, borderRadius: radius.pill,
    paddingHorizontal: 16, paddingVertical: 11,
  },
  searchText: { color: colors.textFaint, fontSize: 14 },
  section: { paddingHorizontal: space.lg, marginTop: 22 },
  hero: { borderRadius: radius.lg, overflow: 'hidden', ...shadow.card },
  heroImg: { width: '100%', height: 180, backgroundColor: colors.bgSoft },
  heroOverlay: { position: 'absolute', left: 0, right: 0, bottom: 0, top: 0, padding: 18, justifyContent: 'flex-end', backgroundColor: 'rgba(20,24,45,0.34)' },
  heroTitle: { color: colors.white, fontSize: 20, fontFamily: fonts.bold, fontWeight: '800' },
  heroSub: { color: 'rgba(255,255,255,0.9)', fontSize: 13, marginTop: 4 },
  heroBtn: { alignSelf: 'flex-start', backgroundColor: colors.white, borderRadius: radius.pill, paddingHorizontal: 16, paddingVertical: 8, marginTop: 12 },
  heroBtnText: { color: colors.primary, fontSize: 13, fontFamily: fonts.bold, fontWeight: '800' },
  rowScroll: { flexGrow: 0, flexShrink: 0, marginHorizontal: -space.lg },
  row: { gap: 12, paddingHorizontal: space.lg },
  chipsScroll: { flexGrow: 0, flexShrink: 0, marginBottom: 14 },
  chips: { gap: 8, paddingRight: 8, alignItems: 'center' },
  empty: { color: colors.textFaint, fontSize: 13, paddingVertical: 16 },
  loading: { paddingVertical: 30, alignItems: 'center' },
});
