import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { DimensionValue, Pressable, StyleSheet, Text, View } from 'react-native';
import { Venue } from '@/lib/api/types';
import { colors, fonts, radius, shadow } from '@/lib/theme';
import { Rating } from './Rating';

// 카드: 이미지 + 이름 + 골드 별점 + 태그 (Figma 리디자인). 2열 그리드/가로 스크롤 공용.
export function VenueCard({ venue, width = '48.5%' }: { venue: Venue; width?: DimensionValue }) {
  const router = useRouter();
  const tags: string[] = [];
  if (venue.hanbokDiscount) tags.push('한복');
  if (venue.category && venue.category !== '한복') tags.push(venue.category);

  return (
    <Pressable style={[styles.card, { width }]} onPress={() => router.push(`/venue/${venue.id}`)}>
      <Image source={venue.imageUrl} style={styles.img} contentFit="cover" transition={200} />
      <View style={styles.body}>
        <Text style={styles.name} numberOfLines={1}>{venue.name}</Text>
        <Rating value={venue.avgRating} />
        {tags.length > 0 && (
          <View style={styles.tags}>
            {tags.slice(0, 2).map((t) => (
              <View key={t} style={[styles.tag, t === '한복' && styles.tagHanbok]}>
                <Text style={[styles.tagText, t === '한복' && styles.tagTextHanbok]}>{t}</Text>
              </View>
            ))}
          </View>
        )}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: radius.lg, backgroundColor: colors.bgCard, marginBottom: 14, ...shadow.card },
  img: { width: '100%', height: 110, borderTopLeftRadius: radius.lg, borderTopRightRadius: radius.lg, backgroundColor: colors.bgSoft },
  body: { padding: 10 },
  name: { fontSize: 14, fontFamily: fonts.semibold, fontWeight: '600', color: colors.text, marginBottom: 4 },
  tags: { flexDirection: 'row', gap: 5, marginTop: 7 },
  tag: { backgroundColor: colors.bgSoft, borderRadius: radius.sm, paddingHorizontal: 7, paddingVertical: 3 },
  tagHanbok: { backgroundColor: '#FDECEC' },
  tagText: { fontSize: 10, color: colors.textSub, fontFamily: fonts.medium, fontWeight: '500' },
  tagTextHanbok: { color: colors.hanbok },
});
