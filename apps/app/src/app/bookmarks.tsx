import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Rating } from '@/components/Rating';
import { useMyWishlistsQuery, useToggleWishlistMutation } from '@/lib/hooks/queries';
import { colors, radius, space } from '@/lib/theme';

export default function BookmarksScreen() {
  const router = useRouter();
  const { data, isLoading } = useMyWishlistsQuery();
  const { remove } = useToggleWishlistMutation();

  if (isLoading) return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;

  if (!data?.length) {
    return (
      <View style={styles.empty}>
        <Ionicons name="bookmark-outline" size={48} color={colors.textFaint} />
        <Text style={styles.emptyText}>아직 즐겨찾기한 곳이 없어요</Text>
        <Text style={styles.emptySub}>장소 카드의 북마크를 눌러 저장해보세요</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.safe} contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: 24 }}>
      <Text style={styles.count}>{data.length}개 저장됨</Text>
      {data.map((w) => (
        <Pressable
          key={w.id}
          style={styles.row}
          onPress={() => w.targetType === 'VENUE' && router.push(`/venue/${w.targetId}`)}>
          <Image source={w.imageUrl} style={styles.img} contentFit="cover" />
          <View style={{ flex: 1 }}>
            <Text style={styles.name} numberOfLines={1}>{w.name}</Text>
            <View style={{ marginTop: 4 }}><Rating value={w.avgRating} count={w.reviewCount} /></View>
            <Text style={styles.type}>{w.targetType === 'VENUE' ? '장소' : '공연'}</Text>
          </View>
          <Pressable onPress={() => remove.mutate({ targetType: w.targetType, targetId: w.targetId })} hitSlop={8}>
            <Ionicons name="bookmark" size={20} color={colors.primary} />
          </Pressable>
        </Pressable>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  count: { fontSize: 12, color: colors.textFaint, marginVertical: 12 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10 },
  img: { width: 76, height: 76, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  name: { fontSize: 15, fontWeight: '700', color: colors.text },
  type: { fontSize: 12, color: colors.textFaint, marginTop: 4 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg, gap: 8 },
  emptyText: { fontSize: 16, fontWeight: '700', color: colors.text, marginTop: 8 },
  emptySub: { fontSize: 13, color: colors.textFaint },
});
