import { Image } from '@/components/Image';
import { useRouter } from 'expo-router';
import { Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import { Performance } from '@/lib/api/types';
import { sized } from '@/lib/img';
import { colors, fonts, radius, space } from '@/lib/theme';

/**
 * 행사 포스터를 가로로 훑는 줄. 홈과 장소 상세가 같은 모양을 쓴다.
 *
 * 홈에만 있던 지역 컴포넌트였는데 장소 상세에서도 같은 걸 쓰게 되어 꺼냈다.
 * 복사해 두면 한쪽만 고치게 된다.
 *
 * 설명줄(caption)만 화면마다 다르다 — 홈은 장르, 장소 상세는 진행 상태를 보여준다.
 * 장소 안에서는 장르보다 "지금 하는지 곧 하는지"가 궁금하기 때문이다.
 */
export function PerformanceCarousel({
  items,
  caption = genreCaption,
}: {
  items: Performance[];
  caption?: (p: Performance) => string;
}) {
  const router = useRouter();
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.scroll}
      contentContainerStyle={styles.row}>
      {items.map((p) => (
        <Pressable key={p.id} style={styles.card} onPress={() => router.push(`/performances/${p.id}`)}>
          <Image
            source={sized(p.posterImageUrl, 300, 400)}
            style={styles.img}
            contentFit="cover"
            transition={200}
            cachePolicy="memory-disk"
          />
          <Text style={styles.title} numberOfLines={2}>{p.title}</Text>
          <Text style={styles.sub} numberOfLines={1}>{caption(p)}</Text>
        </Pressable>
      ))}
    </ScrollView>
  );
}

/** 홈 기본값 — 장르 · 시작일. */
function genreCaption(p: Performance) {
  return `${p.genre ?? '전통 행사'}${p.startDate ? ` · ${md(p.startDate)}~` : ''}`;
}

/** 장소 상세용 — 진행 상태 · 기간. */
export function stateCaption(p: Performance) {
  const label = p.state === 'ONGOING' ? '진행중' : p.state === 'UPCOMING' ? '예정' : '종료';
  if (!p.startDate) return label;
  const period = p.endDate && p.endDate !== p.startDate
    ? `${md(p.startDate)}~${md(p.endDate)}`
    : md(p.startDate);
  return `${label} · ${period}`;
}

/** 2026-10-02 → 10.02 */
function md(date: string) {
  return date.slice(5).replace('-', '.');
}

const styles = StyleSheet.create({
  // 음수 여백으로 섹션 좌우 패딩을 벗어나 화면 끝까지 흐르게 한다.
  scroll: { flexGrow: 0, flexShrink: 0, marginHorizontal: -space.lg },
  row: { gap: 12, paddingHorizontal: space.lg },
  card: { width: 130 },
  img: { width: 130, height: 170, borderRadius: radius.lg, backgroundColor: colors.bgSoft },
  title: { fontSize: 13, fontFamily: fonts.bold, color: colors.text, marginTop: 8, lineHeight: 18 },
  sub: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
});
