import { Ionicons } from '@expo/vector-icons';
import { Stack, useRouter } from 'expo-router';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useDeleteItineraryMutation, useMyItinerariesQuery } from '@/lib/hooks/queries';
import { colors, radius, shadow, space } from '@/lib/theme';

export default function ItinerariesScreen() {
  const router = useRouter();
  const { data, isLoading } = useMyItinerariesQuery();
  const del = useDeleteItineraryMutation();

  // 만들기만 되고 지울 수단이 없었다. 서버·API·훅은 이미 있었고 화면만 비어 있었다.
  const confirmDelete = (id: number, title: string) =>
    Alert.alert('여행 일지 삭제', `'${title}'을(를) 삭제할까요? 되돌릴 수 없습니다.`, [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => del.mutate(id, {
          onError: (e: any) => Alert.alert('삭제 실패', e?.message ?? '오류가 발생했습니다.'),
        }),
      },
    ]);

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
              <Pressable
                hitSlop={10}
                style={styles.delBtn}
                onPress={() => confirmDelete(it.id, it.title)}
              >
                <Ionicons name="trash-outline" size={18} color={colors.textFaint} />
              </Pressable>
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
  delBtn: { padding: 6, marginRight: 2 },
});
