import { Ionicons } from '@expo/vector-icons';
import { Stack } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useDeleteReviewMutation, useMyReviewsQuery } from '@/lib/hooks/queries';
import { colors, radius, space } from '@/lib/theme';

export default function MyReviewsScreen() {
  const { data, isLoading } = useMyReviewsQuery();
  const del = useDeleteReviewMutation();

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '내가 쓴 리뷰' }} />
      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : !data?.length ? (
        <View style={styles.empty}><Text style={styles.emptyText}>작성한 리뷰가 없어요</Text></View>
      ) : (
        <ScrollView contentContainerStyle={{ padding: space.lg }}>
          {data.map((r) => (
            <View key={r.id} style={styles.card}>
              <View style={styles.row}>
                <View style={styles.stars}>
                  {[1, 2, 3, 4, 5].map((n) => (
                    <Ionicons key={n} name={n <= r.rating ? 'star' : 'star-outline'} size={14} color={colors.star} />
                  ))}
                </View>
                <Pressable onPress={() => del.mutate(r.id)} hitSlop={8}>
                  <Ionicons name="trash-outline" size={18} color={colors.textFaint} />
                </Pressable>
              </View>
              {!!r.content && <Text style={styles.content}>{r.content}</Text>}
              <Text style={styles.meta}>{r.targetType === 'VENUE' ? '장소' : '공연'} #{r.targetId} · {r.createdAt?.slice(0, 10)}</Text>
            </View>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  emptyText: { color: colors.textFaint, fontSize: 14 },
  card: { backgroundColor: colors.bgSoft, borderRadius: radius.md, padding: 14, marginBottom: 12 },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  stars: { flexDirection: 'row', gap: 1 },
  content: { fontSize: 14, color: colors.text, marginTop: 8 },
  meta: { fontSize: 12, color: colors.textFaint, marginTop: 8 },
});
