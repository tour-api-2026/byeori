import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useCourseDetailQuery } from '@/lib/hooks/queries';
import { colors, radius, space } from '@/lib/theme';

export default function CourseDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { data, isLoading } = useCourseDetailQuery(Number(id));

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: data?.title ?? '추천 코스' }} />
      {isLoading || !data ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : (
        <ScrollView contentContainerStyle={{ paddingBottom: 24 + insets.bottom }}>
          <Image source={data.coverImageUrl} style={styles.cover} contentFit="cover" />
          <View style={styles.head}>
            <View style={styles.themeBadge}><Text style={styles.themeText}>{data.theme}</Text></View>
            <Text style={styles.title}>{data.title}</Text>
            <Text style={styles.desc}>{data.description}</Text>
            <Text style={styles.meta}>⏱ 약 {data.durationHours}시간 · 총 {data.items.length}곳</Text>
          </View>

          {/* 동선 타임라인 */}
          <View style={styles.timeline}>
            {data.items.map((it, idx) => (
              <Pressable
                key={it.id}
                style={styles.stop}
                onPress={() => it.targetType === 'VENUE' && router.push(`/venue/${it.targetId}`)}>
                <View style={styles.indexCol}>
                  <View style={styles.dot}><Text style={styles.dotText}>{idx + 1}</Text></View>
                  {idx < data.items.length - 1 && <View style={styles.line} />}
                </View>
                <Image source={it.imageUrl} style={styles.stopImg} contentFit="cover" />
                <View style={styles.stopBody}>
                  <View style={styles.stopType}>
                    <Ionicons name={it.targetType === 'PERFORMANCE' ? 'ticket-outline' : 'location-outline'} size={12} color={colors.primary} />
                    <Text style={styles.stopTypeText}>{it.targetType === 'PERFORMANCE' ? '행사' : '장소'}{it.recommendedTime ? ` · ${it.recommendedTime}` : ''}</Text>
                  </View>
                  <Text style={styles.stopName}>{it.name}</Text>
                  {!!it.note && <Text style={styles.stopNote}>{it.note}</Text>}
                </View>
              </Pressable>
            ))}
          </View>

          <Pressable style={styles.cta}><Text style={styles.ctaText}>내 여행 일지로 담기</Text></Pressable>
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  cover: { width: '100%', height: 200, backgroundColor: colors.bgSoft },
  head: { padding: space.lg },
  themeBadge: { alignSelf: 'flex-start', backgroundColor: colors.primarySoft, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 3, marginBottom: 8 },
  themeText: { color: colors.primary, fontSize: 11, fontWeight: '800' },
  title: { fontSize: 22, fontWeight: '900', color: colors.text },
  desc: { fontSize: 14, color: colors.textSub, marginTop: 6, lineHeight: 20 },
  meta: { fontSize: 13, color: colors.textFaint, marginTop: 10 },
  timeline: { paddingHorizontal: space.lg },
  stop: { flexDirection: 'row', gap: 12 },
  indexCol: { alignItems: 'center', width: 28 },
  dot: { width: 26, height: 26, borderRadius: 13, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  dotText: { color: colors.white, fontSize: 12, fontWeight: '800' },
  line: { flex: 1, width: 2, backgroundColor: colors.border, marginVertical: 2 },
  stopImg: { width: 64, height: 64, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  stopBody: { flex: 1, paddingBottom: 18, paddingTop: 2 },
  stopType: { flexDirection: 'row', alignItems: 'center', gap: 4 },
  stopTypeText: { fontSize: 11, color: colors.primary, fontWeight: '700' },
  stopName: { fontSize: 15, fontWeight: '700', color: colors.text, marginTop: 2 },
  stopNote: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  cta: { margin: space.lg, backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center' },
  ctaText: { color: colors.white, fontSize: 15, fontWeight: '800' },
});
