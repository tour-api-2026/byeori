import { Ionicons } from '@expo/vector-icons';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { VenueRow } from '@/components/VenueRow';
import { useVenuesQuery } from '@/lib/hooks/queries';
import { useBookmarkStore } from '@/lib/store/bookmarkStore';
import { colors, space } from '@/lib/theme';

export default function BookmarksScreen() {
  const ids = useBookmarkStore((s) => s.venueIds);
  const { data } = useVenuesQuery({ size: 100 });
  const saved = (data?.content ?? []).filter((v) => ids.includes(v.id));

  if (saved.length === 0) {
    return (
      <View style={styles.empty}>
        <Ionicons name="bookmark-outline" size={48} color={colors.textFaint} />
        <Text style={styles.emptyText}>아직 즐겨찾기한 장소가 없어요</Text>
        <Text style={styles.emptySub}>카드의 북마크를 눌러 저장해보세요</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.safe} contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: 24 }}>
      <Text style={styles.count}>{saved.length}개 저장됨</Text>
      {saved.map((v) => <VenueRow key={v.id} venue={v} />)}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  count: { fontSize: 12, color: colors.textFaint, marginVertical: 12 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg, gap: 8 },
  emptyText: { fontSize: 16, fontWeight: '700', color: colors.text, marginTop: 8 },
  emptySub: { fontSize: 13, color: colors.textFaint },
});
