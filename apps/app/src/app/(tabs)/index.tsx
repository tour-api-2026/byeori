import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from '@/components/Chip';
import { SectionHeader } from '@/components/SectionHeader';
import { VenueCard } from '@/components/VenueCard';
import { Venue } from '@/lib/api/types';
import { usePerformancesQuery, useVenuesQuery } from '@/lib/hooks/queries';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const KEYWORDS = ['전체', '문화', '카페', '체험', '맛집'];
const REGIONS = ['전체', '종로구', '중구', '용산구'];

export default function HomeScreen() {
  const router = useRouter();
  const [keyword, setKeyword] = useState('전체');
  const [region, setRegion] = useState('전체');

  const banner = usePerformancesQuery({ state: 'ONGOING', size: 5 });
  const recommended = useVenuesQuery({ size: 6 });
  const keyworded = useVenuesQuery({ category: keyword === '전체' ? undefined : keyword, size: 6 });
  const all = useVenuesQuery({ size: 50 });

  const top = banner.data?.content?.[0];
  const regionVenues = useMemo(() => {
    const list = all.data?.content ?? [];
    return (region === '전체' ? list : list.filter((v) => v.address?.includes(region))).slice(0, 4);
  }, [all.data, region]);
  const recent = (all.data?.content ?? []).slice(-4).reverse();

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 24 }}>
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
            <Pressable style={styles.hero} onPress={() => router.push(`/venue/${top.venueId}`)}>
              <Image source={top.posterImageUrl} style={styles.heroImg} contentFit="cover" transition={250} />
              <View style={styles.heroOverlay}>
                <Text style={styles.heroTitle} numberOfLines={1}>{top.title}</Text>
                <Text style={styles.heroSub} numberOfLines={1}>{top.genre ?? '추천 행사'} · 지금 만나보세요</Text>
                <View style={styles.heroBtn}><Text style={styles.heroBtnText}>보러가기</Text></View>
              </View>
            </Pressable>
          ) : <Loading />}
        </View>

        {/* 맞춤 추천 */}
        <View style={styles.section}>
          <SectionHeader title="맞춤 추천" onMore={() => router.push('/search')} />
          {recommended.isLoading ? <Loading /> : <Grid venues={recommended.data?.content ?? []} />}
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
          <Chips items={REGIONS} value={region} onChange={setRegion} />
          {regionVenues.length
            ? <Grid venues={regionVenues} />
            : <Text style={styles.empty}>해당 지역의 장소가 아직 없어요</Text>}
        </View>

        {/* 최근 본 장소 */}
        <View style={styles.section}>
          <SectionHeader title="최근 본 장소" onMore={() => router.push('/search')} />
          <Grid venues={recent} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function Grid({ venues }: { venues: Venue[] }) {
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
