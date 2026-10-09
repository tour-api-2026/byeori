import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { Image } from '@/components/Image';
import type { FeedItem } from '@/lib/api/feed';
import { sized } from '@/lib/img';
import { colors, fonts, radius, space } from '@/lib/theme';

/**
 * '당신을 위한 추천' 가로 줄.
 *
 * 칸마다 **왜 떴는지**를 함께 보여 준다('내 주변 0.3km', '지금 진행중', '관심 주제 · 공예').
 * 이유가 없으면 추천은 그냥 무작위 목록으로 보이고, 틀렸을 때 왜 틀렸는지도 알 수 없다.
 *
 * 장소와 행사가 한 줄에 섞인다. 서버가 둘을 같은 점수표로 세워 주므로 화면은 가르지 않는다.
 */
export function FeedCarousel({ items }: { items: FeedItem[] }) {
  const router = useRouter();
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.scroll}
      contentContainerStyle={styles.row}>
      {items.map((it) => (
        <Pressable
          key={`${it.targetType}-${it.targetId}`}
          style={styles.card}
          onPress={() => router.push(
            it.targetType === 'PERFORMANCE'
              ? `/performances/${it.targetId}?from=FEED`
              : `/venue/${it.targetId}?from=FEED`,
          )}>
          <Image
            source={sized(it.imageUrl, 300, 300)}
            style={styles.img}
            contentFit="cover"
            transition={200}
            cachePolicy="memory-disk"
          />
          <View style={styles.reason}>
            <Text style={styles.reasonText} numberOfLines={1}>{it.reason}</Text>
          </View>
          <Text style={styles.name} numberOfLines={2}>{it.name ?? '이름 없음'}</Text>
          <Text style={styles.sub} numberOfLines={1}>
            {it.targetType === 'PERFORMANCE' ? '행사' : '장소'}{it.category ? ` · ${it.category}` : ''}
          </Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  // 음수 여백으로 섹션 좌우 패딩을 벗어나 화면 끝까지 흐르게 한다.
  scroll: { flexGrow: 0, flexShrink: 0, marginHorizontal: -space.lg },
  row: { gap: 12, paddingHorizontal: space.lg },
  card: { width: 140 },
  img: { width: 140, height: 140, borderRadius: radius.lg, backgroundColor: colors.bgSoft },
  reason: {
    alignSelf: 'flex-start',
    marginTop: 8,
    backgroundColor: colors.primarySoft,
    borderRadius: radius.pill,
    paddingHorizontal: 8,
    paddingVertical: 3,
  },
  reasonText: { fontSize: 11, color: colors.primary, fontFamily: fonts.bold, fontWeight: '800' },
  name: { fontSize: 13, fontFamily: fonts.bold, color: colors.text, marginTop: 6, lineHeight: 18 },
  sub: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
});
