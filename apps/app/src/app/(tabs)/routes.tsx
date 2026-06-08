import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { SectionHeader } from '@/components/SectionHeader';
import { useCoursesQuery, useMyItinerariesQuery } from '@/lib/hooks/queries';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

export default function RoutesScreen() {
  const router = useRouter();
  const courses = useCoursesQuery();
  const mine = useMyItinerariesQuery();

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <Text style={styles.h1}>루트</Text>
      <ScrollView contentContainerStyle={{ paddingBottom: 24 }} showsVerticalScrollIndicator={false}>
        {/* 루트 만들기 CTA */}
        <Pressable style={styles.create} onPress={() => router.push('/itinerary/new')}>
          <View style={styles.createIcon}><Ionicons name="add" size={22} color={colors.white} /></View>
          <View style={{ flex: 1 }}>
            <Text style={styles.createTitle}>새 여행 루트 만들기</Text>
            <Text style={styles.createSub}>달력으로 일정을 짜고 장소를 담아보세요</Text>
          </View>
          <Ionicons name="chevron-forward" size={18} color={colors.white} />
        </Pressable>

        {/* 내 여행 일지 */}
        <View style={styles.section}>
          <SectionHeader title="내 여행 일지" onMore={() => router.push('/itinerary')} />
          {mine.isLoading ? <ActivityIndicator color={colors.primary} /> : (
            mine.data?.length ? mine.data.slice(0, 3).map((it) => (
              <Pressable key={it.id} style={styles.itinRow} onPress={() => router.push(`/itinerary/${it.id}`)}>
                <View style={styles.itinIcon}><Ionicons name="map" size={18} color={colors.primary} /></View>
                <View style={{ flex: 1 }}>
                  <Text style={styles.itinTitle}>{it.title}</Text>
                  <Text style={styles.itinMeta}>{it.startDate} ~ {it.endDate} · {it.itemCount}곳</Text>
                </View>
                <Ionicons name="chevron-forward" size={18} color={colors.textFaint} />
              </Pressable>
            )) : <Text style={styles.empty}>아직 만든 여행 일지가 없어요</Text>
          )}
        </View>

        {/* 추천 루트 */}
        <View style={styles.section}>
          <SectionHeader title="추천 루트" />
          {courses.isLoading ? <ActivityIndicator color={colors.primary} /> : (
            <View style={{ gap: 16 }}>
              {courses.data?.map((c) => (
                <Pressable key={c.id} style={styles.card} onPress={() => router.push(`/course/${c.id}`)}>
                  <Image source={c.coverImageUrl} style={styles.cover} contentFit="cover" transition={200} />
                  <View style={styles.body}>
                    <View style={styles.themeBadge}><Text style={styles.themeText}>{c.theme}</Text></View>
                    <Text style={styles.title}>{c.title}</Text>
                    <Text style={styles.desc} numberOfLines={2}>{c.description}</Text>
                    <View style={styles.metaRow}>
                      <Ionicons name="time-outline" size={14} color={colors.textFaint} />
                      <Text style={styles.meta}>약 {c.durationHours}시간</Text>
                      <Ionicons name="chevron-forward" size={16} color={colors.textFaint} style={{ marginLeft: 'auto' }} />
                    </View>
                  </View>
                </Pressable>
              ))}
            </View>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  h1: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, textAlign: 'center', paddingVertical: 14 },
  create: { flexDirection: 'row', alignItems: 'center', gap: 12, marginHorizontal: space.lg, backgroundColor: colors.primary, borderRadius: radius.lg, padding: 16, ...shadow.card },
  createIcon: { width: 40, height: 40, borderRadius: 20, backgroundColor: 'rgba(255,255,255,0.18)', alignItems: 'center', justifyContent: 'center' },
  createTitle: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
  createSub: { color: 'rgba(255,255,255,0.85)', fontSize: 12, marginTop: 2 },
  section: { paddingHorizontal: space.lg, marginTop: 24 },
  itinRow: { flexDirection: 'row', alignItems: 'center', gap: 12, backgroundColor: colors.bgCard, borderRadius: radius.md, padding: 14, marginBottom: 10, ...shadow.card },
  itinIcon: { width: 36, height: 36, borderRadius: 18, backgroundColor: colors.primarySoft, alignItems: 'center', justifyContent: 'center' },
  itinTitle: { fontSize: 15, fontFamily: fonts.semibold, fontWeight: '600', color: colors.text },
  itinMeta: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  empty: { fontSize: 13, color: colors.textFaint, paddingVertical: 8 },
  card: { borderRadius: radius.lg, backgroundColor: colors.bgCard, overflow: 'hidden', ...shadow.card },
  cover: { width: '100%', height: 150, backgroundColor: colors.bgSoft },
  body: { padding: 14 },
  themeBadge: { alignSelf: 'flex-start', backgroundColor: colors.primarySoft, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 3, marginBottom: 6 },
  themeText: { color: colors.primary, fontSize: 11, fontFamily: fonts.bold, fontWeight: '800' },
  title: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  desc: { fontSize: 13, color: colors.textSub, marginTop: 4 },
  metaRow: { flexDirection: 'row', alignItems: 'center', gap: 4, marginTop: 10 },
  meta: { fontSize: 12, color: colors.textFaint },
});
