import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from '@/components/Chip';
import { Rating } from '@/components/Rating';
import { SectionHeader } from '@/components/SectionHeader';
import { VenueCard } from '@/components/VenueCard';
import { usePerformancesQuery, useVenuesQuery } from '@/lib/hooks/queries';
import { colors, radius, shadow, space } from '@/lib/theme';

const KEYWORDS = ['전체', '한복', '카페', '체험', '문화', '맛집'];

export default function HomeScreen() {
  const router = useRouter();
  const [keyword, setKeyword] = useState('전체');

  const banner = usePerformancesQuery({ state: 'ONGOING', size: 5 });
  const recommended = useVenuesQuery({ size: 10 });
  const keyworded = useVenuesQuery({ category: keyword === '전체' ? undefined : keyword, size: 10 });
  const recent = usePerformancesQuery({ size: 10 });

  const top = banner.data?.content?.[0];

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView showsVerticalScrollIndicator={false} contentContainerStyle={{ paddingBottom: 24 }}>
        {/* 헤더 */}
        <View style={styles.header}>
          <Text style={styles.logo}>벼리</Text>
          <Pressable onPress={() => router.push('/login')}>
            <Ionicons name="person-circle-outline" size={28} color={colors.text} />
          </Pressable>
        </View>
        <Pressable style={styles.search} onPress={() => router.push('/search')}>
          <Ionicons name="search" size={18} color={colors.textFaint} />
          <Text style={styles.searchText}>전통 매장·코스를 검색해보세요</Text>
        </Pressable>

        {/* 오늘의 추천 배너 */}
        <View style={styles.section}>
          <SectionHeader title="오늘의 추천" />
          {top ? (
            <Pressable style={styles.banner} onPress={() => router.push(`/venue/${top.venueId}`)}>
              <Image source={top.posterImageUrl} style={styles.bannerImg} contentFit="cover" transition={250} />
              <View style={styles.bannerOverlay}>
                <View style={styles.bannerBadge}><Text style={styles.bannerBadgeText}>{top.genre ?? '추천'}</Text></View>
                <Text style={styles.bannerTitle} numberOfLines={2}>{top.title}</Text>
                <Rating value={top.avgRating} count={top.reviewCount} />
              </View>
            </Pressable>
          ) : (
            <Loading />
          )}
        </View>

        {/* 맞춤 추천 */}
        <View style={styles.section}>
          <SectionHeader title="맞춤 추천" onMore={() => router.push('/search')} />
          {recommended.isLoading ? <Loading /> : (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.hList}>
              {recommended.data?.content.map((v) => <VenueCard key={v.id} venue={v} />)}
            </ScrollView>
          )}
        </View>

        {/* 키워드로 탐색 */}
        <View style={styles.section}>
          <SectionHeader title="키워드로 탐색" />
          <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsScroll} contentContainerStyle={styles.chips}>
            {KEYWORDS.map((k) => <Chip key={k} label={k} selected={k === keyword} onPress={() => setKeyword(k)} />)}
          </ScrollView>
          {keyworded.isLoading ? <Loading /> : (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.hList, { marginTop: 12 }]}>
              {keyworded.data?.content.length
                ? keyworded.data.content.map((v) => <VenueCard key={v.id} venue={v} />)
                : <Text style={styles.empty}>해당 키워드의 장소가 아직 없어요</Text>}
            </ScrollView>
          )}
        </View>

        {/* 최근 콘텐츠 */}
        <View style={styles.section}>
          <SectionHeader title="최근 콘텐츠" onMore={() => router.push('/routes')} />
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.hList}>
            {recent.data?.content.map((p) => (
              <Pressable key={p.id} style={styles.perfCard} onPress={() => router.push(`/venue/${p.venueId}`)}>
                <Image source={p.posterImageUrl} style={styles.perfImg} contentFit="cover" transition={200} />
                <Text style={styles.perfTitle} numberOfLines={1}>{p.title}</Text>
                <Rating value={p.avgRating} count={p.reviewCount} />
              </Pressable>
            ))}
          </ScrollView>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function Loading() {
  return <View style={styles.loading}><ActivityIndicator color={colors.primary} /></View>;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: space.lg, paddingTop: 8, paddingBottom: 4 },
  logo: { fontSize: 24, fontWeight: '900', color: colors.primary, letterSpacing: -0.5 },
  search: {
    flexDirection: 'row', alignItems: 'center', gap: 8, marginHorizontal: space.lg, marginVertical: 10,
    backgroundColor: colors.bgSoft, borderRadius: radius.pill, paddingHorizontal: 16, paddingVertical: 12,
  },
  searchText: { color: colors.textFaint, fontSize: 14 },
  section: { paddingHorizontal: space.lg, marginTop: 18 },
  banner: { borderRadius: radius.lg, overflow: 'hidden', ...shadow.card },
  bannerImg: { width: '100%', height: 168, backgroundColor: colors.bgSoft },
  bannerOverlay: { position: 'absolute', left: 0, right: 0, bottom: 0, padding: 16, backgroundColor: 'rgba(0,0,0,0.32)' },
  bannerBadge: { alignSelf: 'flex-start', backgroundColor: colors.primary, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 3, marginBottom: 6 },
  bannerBadgeText: { color: colors.white, fontSize: 11, fontWeight: '700' },
  bannerTitle: { color: colors.white, fontSize: 19, fontWeight: '800', marginBottom: 6 },
  hList: { gap: 12, paddingRight: 8 },
  chipsScroll: { flexGrow: 0, flexShrink: 0 },
  chips: { gap: 8, paddingRight: 8, alignItems: 'center' },
  empty: { color: colors.textFaint, fontSize: 13, paddingVertical: 20 },
  perfCard: { width: 150 },
  perfImg: { width: 150, height: 100, borderRadius: radius.md, backgroundColor: colors.bgSoft, marginBottom: 6 },
  perfTitle: { fontSize: 13, fontWeight: '700', color: colors.text, marginBottom: 2 },
  loading: { paddingVertical: 30, alignItems: 'center' },
});
