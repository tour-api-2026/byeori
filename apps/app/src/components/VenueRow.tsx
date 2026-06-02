import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Venue } from '@/lib/api/types';
import { useBookmarkStore } from '@/lib/store/bookmarkStore';
import { colors, radius } from '@/lib/theme';
import { Rating } from './Rating';

// 세로 리스트 행 (검색·즐겨찾기)
export function VenueRow({ venue }: { venue: Venue }) {
  const router = useRouter();
  const has = useBookmarkStore((s) => s.venueIds.includes(venue.id));
  const toggle = useBookmarkStore((s) => s.toggle);

  return (
    <Pressable style={styles.row} onPress={() => router.push(`/venue/${venue.id}`)}>
      <Image source={venue.imageUrl} style={styles.img} contentFit="cover" transition={150} />
      <View style={styles.body}>
        <Text style={styles.name} numberOfLines={1}>{venue.name}</Text>
        <Text style={styles.addr} numberOfLines={1}>{venue.address}</Text>
        <View style={styles.metaRow}>
          <Rating value={venue.avgRating} count={venue.reviewCount} />
          {!!venue.category && <Text style={styles.cat}>· {venue.category}</Text>}
          {venue.hanbokDiscount && (
            <View style={styles.hanbok}><Text style={styles.hanbokText}>한복할인</Text></View>
          )}
        </View>
      </View>
      <Pressable onPress={() => toggle(venue.id)} hitSlop={8}>
        <Ionicons name={has ? 'bookmark' : 'bookmark-outline'} size={20} color={has ? colors.primary : colors.textFaint} />
      </Pressable>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10 },
  img: { width: 76, height: 76, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  body: { flex: 1 },
  name: { fontSize: 15, fontWeight: '700', color: colors.text },
  addr: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 5 },
  cat: { fontSize: 12, color: colors.textFaint },
  hanbok: { backgroundColor: '#FDECEC', borderRadius: radius.sm, paddingHorizontal: 6, paddingVertical: 2 },
  hanbokText: { color: colors.hanbok, fontSize: 10, fontWeight: '800' },
});
