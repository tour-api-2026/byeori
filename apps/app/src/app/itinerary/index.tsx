import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useMyItinerariesQuery } from '@/lib/hooks/queries';
import { colors, radius, shadow, space } from '@/lib/theme';

export default function ItinerariesScreen() {
  const router = useRouter();
  const { data, isLoading } = useMyItinerariesQuery();

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '내 여행 일지' }} />
      <Pressable style={styles.addBtn} onPress={() => router.push('/itinerary/new')}>
        <Ionicons name="add" size={18} color={colors.white} />
        <Text style={styles.addText}>새 여행 일지 만들기</Text>
      </Pressable>
      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : !data?.length ? (
        <View style={styles.empty}><Text style={styles.emptyText}>아직 여행 일지가 없어요</Text></View>
      ) : (
        <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg }}>
          {data.map((it) => (
            <Pressable key={it.id} style={styles.card} onPress={() => router.push(`/itinerary/${it.id}`)}>
              <View style={{ flex: 1 }}>
                <Text style={styles.title}>{it.title}</Text>
                <Text style={styles.meta}>{it.startDate} ~ {it.endDate} · {it.itemCount}곳 · {it.sourceType === 'CURATED' ? '추천코스 복사' : '직접 구성'}</Text>
              </View>
              <Ionicons name="chevron-forward" size={18} color={colors.textFaint} />
            </Pressable>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  addBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 13, margin: space.lg },
  addText: { color: colors.white, fontSize: 14, fontWeight: '800' },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { color: colors.textFaint, fontSize: 14 },
  card: { flexDirection: 'row', alignItems: 'center', backgroundColor: colors.bgCard, borderRadius: radius.md, padding: 16, marginBottom: 12, ...shadow.card },
  title: { fontSize: 16, fontWeight: '700', color: colors.text },
  meta: { fontSize: 12, color: colors.textFaint, marginTop: 4 },
});
