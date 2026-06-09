import { Ionicons } from '@expo/vector-icons';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from '@/components/Chip';
import { VenueCard } from '@/components/VenueCard';
import { useVenuesQuery } from '@/lib/hooks/queries';
import { colors, fonts, radius, space } from '@/lib/theme';

const CATS = ['전체', '문화', '카페', '체험', '맛집'];
const REGIONS = ['전체', '서울', '부산', '대구', '전주', '제주'];
const PER_PAGE = 6;

export default function SearchScreen() {
  const [keyword, setKeyword] = useState('');
  const [cat, setCat] = useState('전체');
  const [region, setRegion] = useState('전체');
  const [page, setPage] = useState(0);

  const { data, isLoading } = useVenuesQuery({
    keyword: keyword || undefined,
    category: cat === '전체' ? undefined : cat,
    size: 60,
  });

  const filtered = useMemo(() => {
    const list = data?.content ?? [];
    return region === '전체' ? list : list.filter((v) => v.address?.includes(region));
  }, [data, region]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PER_PAGE));
  const cur = Math.min(page, totalPages - 1);
  const pageItems = filtered.slice(cur * PER_PAGE, cur * PER_PAGE + PER_PAGE);

  const reset = (fn: () => void) => { fn(); setPage(0); };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* 검색창 */}
      <View style={styles.searchBar}>
        <Ionicons name="search" size={18} color={colors.accent} />
        <TextInput
          style={styles.input}
          placeholder="장소를 검색해보세요"
          placeholderTextColor={colors.textFaint}
          value={keyword}
          onChangeText={(t) => reset(() => setKeyword(t))}
        />
      </View>

      {/* 카테고리 칩 */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsScroll} contentContainerStyle={styles.chips}>
        {CATS.map((c) => <Chip key={c} label={c} selected={c === cat} onPress={() => reset(() => setCat(c))} />)}
      </ScrollView>
      {/* 지역 칩 */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsScroll} contentContainerStyle={styles.chips}>
        {REGIONS.map((r) => <Chip key={r} label={r} selected={r === region} onPress={() => reset(() => setRegion(r))} />)}
      </ScrollView>

      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: 24, paddingTop: 6 }}>
          {pageItems.length ? (
            <>
              <View style={styles.grid}>
                {pageItems.map((v) => <VenueCard key={v.id} venue={v} />)}
              </View>
              {totalPages > 1 && (
                <View style={styles.pager}>
                  <PagerBtn icon="chevron-back" disabled={cur === 0} onPress={() => setPage(cur - 1)} />
                  {Array.from({ length: totalPages }, (_, i) => i).slice(Math.max(0, cur - 2), Math.max(0, cur - 2) + 5).map((i) => (
                    <Pressable key={i} style={[styles.pageNum, i === cur && styles.pageNumOn]} onPress={() => setPage(i)}>
                      <Text style={[styles.pageNumText, i === cur && styles.pageNumTextOn]}>{i + 1}</Text>
                    </Pressable>
                  ))}
                  <PagerBtn icon="chevron-forward" disabled={cur >= totalPages - 1} onPress={() => setPage(cur + 1)} />
                </View>
              )}
            </>
          ) : <Text style={styles.empty}>검색 결과가 없어요</Text>}
        </ScrollView>
      )}
    </SafeAreaView>
  );
}

function PagerBtn({ icon, disabled, onPress }: { icon: any; disabled: boolean; onPress: () => void }) {
  return (
    <Pressable style={styles.pageNum} disabled={disabled} onPress={onPress}>
      <Ionicons name={icon} size={16} color={disabled ? colors.border : colors.textSub} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  searchBar: {
    flexDirection: 'row', alignItems: 'center', gap: 8, marginHorizontal: space.lg, marginTop: 10, marginBottom: 8,
    backgroundColor: colors.white, borderWidth: 1.5, borderColor: colors.accent, borderRadius: radius.pill, paddingHorizontal: 16, paddingVertical: 11,
  },
  input: { flex: 1, fontSize: 15, color: colors.text },
  chipsScroll: { flexGrow: 0, flexShrink: 0 },
  chips: { gap: 8, paddingHorizontal: space.lg, paddingVertical: 6, alignItems: 'center' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', marginTop: 6 },
  empty: { fontSize: 14, color: colors.textFaint, textAlign: 'center', marginTop: 40 },
  pager: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 8 },
  pageNum: { width: 32, height: 32, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  pageNumOn: { backgroundColor: colors.primary },
  pageNumText: { fontSize: 14, color: colors.textSub, fontFamily: fonts.semibold, fontWeight: '600' },
  pageNumTextOn: { color: colors.white },
});
