import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { Venue } from '@/lib/api/types';
import { useToggleWishlistMutation } from '@/lib/hooks/queries';
import { useBookmarkStore } from '@/lib/store/bookmarkStore';
import { colors, radius, shadow } from '@/lib/theme';
import { Rating } from './Rating';

// 가로 카드 (맞춤 추천 등 가로 스크롤)
export function VenueCard({ venue, width = 160 }: { venue: Venue; width?: number }) {
  const router = useRouter();
  const has = useBookmarkStore((s) => s.venueIds.includes(venue.id));
  const toggleLocal = useBookmarkStore((s) => s.toggle);
  const { add, remove } = useToggleWishlistMutation();

  const toggle = (id: number) => {
    const willAdd = !has;
    toggleLocal(id);
    const payload = { targetType: 'VENUE', targetId: id };
    (willAdd ? add : remove).mutate(payload);
  };

  return (
    <Pressable style={[styles.card, { width }]} onPress={() => router.push(`/venue/${venue.id}`)}>
      <View>
        <Image source={venue.imageUrl} style={[styles.img, { width }]} contentFit="cover" transition={200} />
        <Pressable style={styles.bookmark} onPress={() => toggle(venue.id)} hitSlop={8}>
          <Ionicons name={has ? 'bookmark' : 'bookmark-outline'} size={18} color={has ? colors.primary : colors.white} />
        </Pressable>
        {venue.hanbokDiscount && (
          <View style={styles.hanbokBadge}>
            <Text style={styles.hanbokText}>한복할인</Text>
          </View>
        )}
      </View>
      <View style={styles.body}>
        <Text style={styles.name} numberOfLines={1}>{venue.name}</Text>
        <View style={styles.meta}>
          <Rating value={venue.avgRating} count={venue.reviewCount} />
          {!!venue.category && <Text style={styles.cat}> · {venue.category}</Text>}
        </View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: radius.lg, backgroundColor: colors.bgCard, ...shadow.card },
  img: { height: 112, borderRadius: radius.lg, backgroundColor: colors.bgSoft },
  bookmark: {
    position: 'absolute', top: 8, right: 8,
    backgroundColor: 'rgba(0,0,0,0.28)', borderRadius: radius.pill, padding: 5,
  },
  hanbokBadge: {
    position: 'absolute', left: 8, top: 8,
    backgroundColor: colors.hanbok, borderRadius: radius.sm, paddingHorizontal: 7, paddingVertical: 3,
  },
  hanbokText: { color: colors.white, fontSize: 10, fontWeight: '800' },
  body: { padding: 10 },
  name: { fontSize: 14, fontWeight: '700', color: colors.text, marginBottom: 4 },
  meta: { flexDirection: 'row', alignItems: 'center' },
  cat: { fontSize: 12, color: colors.textFaint },
});
