import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from '@/components/Chip';
import { Performance } from '@/lib/api/types';
import { usePerformancesQuery } from '@/lib/hooks/queries';
import { sized } from '@/lib/img';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const TABS = [
  { label: '진행 중', state: 'ONGOING' },
  { label: '예정', state: 'UPCOMING' },
] as const;

function formatPeriod(p: Performance) {
  const d = (s?: string | null) => (s ? s.slice(5).replace('-', '.') : '');
  const start = d(p.startDate);
  const end = d(p.endDate);
  if (!start) return '';
  return end && end !== start ? `${start} ~ ${end}` : start;
}

export default function TraditionalPerformancesScreen() {
  const router = useRouter();
  const [state, setState] = useState<string>('ONGOING');
  const query = usePerformancesQuery({ traditional: true, state, size: 100 });

  const items = useMemo(() => query.data?.content ?? [], [query.data]);

  const open = (p: Performance) => {
    if (p.venueId) router.push(`/venue/${p.venueId}`);
    else if (p.externalBookingUrl) WebBrowser.openBrowserAsync(p.externalBookingUrl);
  };

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <View style={styles.tabs}>
        {TABS.map((t) => (
          <Chip key={t.state} label={t.label} selected={t.state === state} onPress={() => setState(t.state)} />
        ))}
        {!query.isLoading && (
          <Text style={styles.count}>{query.data?.totalElements ?? 0}건</Text>
        )}
      </View>

      {query.isLoading ? (
        <View style={styles.loading}><ActivityIndicator color={colors.primary} /></View>
      ) : (
        <FlatList
          data={items}
          keyExtractor={(p) => String(p.id)}
          contentContainerStyle={{ padding: space.lg, paddingTop: 4, gap: 12 }}
          ListEmptyComponent={<Text style={styles.empty}>해당하는 전통 행사가 아직 없어요</Text>}
          renderItem={({ item }) => (
            <Pressable style={styles.card} onPress={() => open(item)}>
              <Image
                source={sized(item.posterImageUrl, 200, 260)}
                style={styles.poster}
                contentFit="cover"
                transition={200}
                cachePolicy="memory-disk"
              />
              <View style={{ flex: 1, gap: 4 }}>
                <Text style={styles.title} numberOfLines={2}>{item.title}</Text>
                <Text style={styles.genre} numberOfLines={1}>{item.genre ?? '전통 행사'}</Text>
                <Text style={styles.period}>{formatPeriod(item)}</Text>
              </View>
            </Pressable>
          )}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  tabs: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: space.lg, paddingVertical: 12 },
  count: { marginLeft: 'auto', fontSize: 13, color: colors.textFaint },
  card: { flexDirection: 'row', gap: 12, backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 10, ...shadow.card },
  poster: { width: 76, height: 100, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  title: { fontSize: 15, fontFamily: fonts.bold, fontWeight: '700', color: colors.text, lineHeight: 21 },
  genre: { fontSize: 13, color: colors.textSub },
  period: { fontSize: 12, color: colors.textFaint, marginTop: 'auto' },
  empty: { color: colors.textFaint, fontSize: 13, textAlign: 'center', paddingVertical: 40 },
  loading: { paddingVertical: 40, alignItems: 'center' },
});
