import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, Share, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ItinerarySummary } from '@/lib/api/itineraries';
import { useItineraryQuery, useMyItinerariesQuery } from '@/lib/hooks/queries';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const pad = (n: number) => String(n).padStart(2, '0');
function todayIso() { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; }
function dayLabel(start: string, end: string) {
  const t = todayIso();
  if (t < start) return { big: 'D-' + Math.round((+new Date(start) - +new Date(t)) / 86400000), small: '여행 전' };
  if (t > end) return { big: '완료', small: '지난 여행' };
  const n = Math.round((+new Date(t + 'T00:00:00') - +new Date(start + 'T00:00:00')) / 86400000) + 1;
  return { big: `${n}일차`, small: '오늘' };
}

export default function RoutesScreen() {
  const router = useRouter();
  const mine = useMyItinerariesQuery();
  const list = mine.data ?? [];
  const featured = list[0];

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <Text style={styles.h1}>내 여행 루트</Text>
      <ScrollView contentContainerStyle={{ paddingHorizontal: space.lg, paddingBottom: 28 }} showsVerticalScrollIndicator={false}>
        {mine.isLoading ? <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} /> : (
          <>
            <Text style={styles.lead}>지금 나의 여행 루트는?</Text>
            {featured ? <Featured summary={featured} /> : (
              <View style={styles.emptyCard}>
                <Text style={styles.emptyText}>아직 만든 여행 루트가 없어요</Text>
              </View>
            )}

            <View style={styles.makeCard}>
              <Text style={styles.makeText}>나만의 새로운 루트를{'\n'}만들어 보세요!</Text>
              <Pressable style={styles.makeBtn} onPress={() => router.push('/itinerary/new')}>
                <Text style={styles.makeBtnText}>새 루트 만들기</Text>
                <Ionicons name="chevron-forward" size={13} color={colors.white} />
              </Pressable>
            </View>

            {list.length > 0 && <Text style={styles.listLabel}>내 여행 루트 목록</Text>}
            {list.map((it) => <RouteCard key={it.id} summary={it} />)}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

function Featured({ summary }: { summary: ItinerarySummary }) {
  const router = useRouter();
  const { data } = useItineraryQuery(summary.id);
  const stops = (data?.items ?? []).slice(0, 3);
  const d = dayLabel(summary.startDate, summary.endDate);

  return (
    <View style={styles.featured}>
      <View style={styles.featuredLeft}>
        <Text style={styles.featuredName} numberOfLines={2}>{summary.title}</Text>
        <View style={{ alignItems: 'center', marginVertical: 12 }}>
          <Text style={styles.featuredSmall}>{d.small}</Text>
          <Text style={styles.featuredBig}>{d.big}</Text>
        </View>
        <Pressable style={styles.confirmBtn} onPress={() => router.push(`/itinerary/${summary.id}`)}>
          <Text style={styles.confirmText}>전체 루트 확인</Text>
          <Ionicons name="chevron-forward" size={13} color={colors.white} />
        </Pressable>
      </View>
      <View style={styles.featuredRight}>
        {stops.length ? stops.map((s, i, arr) => (
          <View key={s.id} style={styles.tRow}>
            <View style={styles.rail}>
              {i > 0 && <View style={[styles.rLine, styles.rTop]} />}
              {i < arr.length - 1 && <View style={[styles.rLine, styles.rBot]} />}
              <View style={styles.tlNum}><Text style={styles.tlNumText}>{i + 1}</Text></View>
            </View>
            <Text style={styles.tlName} numberOfLines={1}>{s.name ?? '장소'}</Text>
          </View>
        )) : <Text style={styles.tlEmpty}>장소를 추가해보세요</Text>}
      </View>
    </View>
  );
}

function RouteCard({ summary }: { summary: ItinerarySummary }) {
  const router = useRouter();
  const { data } = useItineraryQuery(summary.id);
  const stops = data?.items ?? [];

  const share = () => Share.share({ message: `[벼리] '${summary.title}' 여행 루트를 함께 보아요! (${summary.startDate} ~ ${summary.endDate})` });

  return (
    <View style={styles.card}>
      <View style={styles.cardHead}>
        <View style={{ flex: 1 }}>
          <Text style={styles.cardTitle} numberOfLines={1}>{summary.title}</Text>
          <Text style={styles.cardMeta}>{summary.itemCount}곳 · {summary.startDate} ~ {summary.endDate}</Text>
        </View>
        <Pressable style={styles.shareBtn} onPress={share}>
          <Text style={styles.shareText}>공유하기</Text>
        </Pressable>
      </View>
      <View style={styles.cardBody}>
        {stops.length ? (
          <View>
            {stops.slice(0, 5).map((s, i, arr) => (
              <View key={s.id} style={styles.tRow}>
                <View style={styles.rail}>
                  {i > 0 && <View style={[styles.rLine, styles.rTop]} />}
                  {i < arr.length - 1 && <View style={[styles.rLine, styles.rBot]} />}
                  <View style={styles.stopNum}><Text style={styles.stopNumText}>{i + 1}</Text></View>
                </View>
                <Text style={styles.stopName} numberOfLines={1}>{s.name ?? '장소'}</Text>
              </View>
            ))}
          </View>
        ) : <Text style={styles.stopEmpty}>아직 담은 장소가 없어요</Text>}
        <Pressable style={styles.editBtn} onPress={() => router.push(`/itinerary/${summary.id}`)}>
          <Text style={styles.editText}>루트 편집</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  h1: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, textAlign: 'center', paddingVertical: 14 },
  lead: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 6, marginBottom: 12 },
  emptyCard: { backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 28, alignItems: 'center', ...shadow.card },
  emptyText: { fontSize: 14, color: colors.textFaint },
  // featured
  featured: { flexDirection: 'row', backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 16, ...shadow.card },
  featuredLeft: { width: 128, alignItems: 'center', justifyContent: 'center', borderRightWidth: 1, borderRightColor: colors.border, paddingRight: 12 },
  featuredName: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, textAlign: 'center' },
  featuredSmall: { fontSize: 12, color: colors.textFaint },
  featuredBig: { fontSize: 26, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 2 },
  confirmBtn: { flexDirection: 'row', alignItems: 'center', gap: 2, backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 12, paddingVertical: 7 },
  confirmText: { color: colors.white, fontSize: 11, fontFamily: fonts.bold, fontWeight: '800' },
  featuredRight: { flex: 1, paddingLeft: 8, justifyContent: 'center' },
  // 타임라인 (번호 + 연결선)
  tRow: { flexDirection: 'row', alignItems: 'center', minHeight: 40 },
  rail: { width: 26, alignSelf: 'stretch', alignItems: 'center', justifyContent: 'center', marginRight: 6 },
  rLine: { position: 'absolute', width: 2, left: 12, backgroundColor: colors.border },
  rTop: { top: 0, bottom: '50%' },
  rBot: { top: '50%', bottom: 0 },
  tlNum: { width: 22, height: 22, borderRadius: 11, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  tlNumText: { color: colors.white, fontSize: 11, fontFamily: fonts.bold, fontWeight: '800' },
  tlName: { flex: 1, fontSize: 13, color: colors.textSub, fontFamily: fonts.medium, fontWeight: '500' },
  tlEmpty: { fontSize: 13, color: colors.textFaint },
  // 새 루트 만들기 카드
  makeCard: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 16, marginVertical: 18, ...shadow.card },
  makeText: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, lineHeight: 20 },
  makeBtn: { flexDirection: 'row', alignItems: 'center', gap: 2, backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 16, paddingVertical: 10 },
  makeBtnText: { color: colors.white, fontSize: 13, fontFamily: fonts.bold, fontWeight: '800' },
  listLabel: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 6, marginBottom: 12 },
  // route card
  card: { backgroundColor: colors.bgCard, borderRadius: radius.lg, marginBottom: 16, overflow: 'hidden', ...shadow.card },
  cardHead: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: colors.primary, paddingHorizontal: 16, paddingVertical: 12 },
  cardTitle: { fontSize: 15, fontFamily: fonts.bold, fontWeight: '800', color: colors.white },
  cardMeta: { fontSize: 12, color: 'rgba(255,255,255,0.8)', marginTop: 2 },
  shareBtn: { backgroundColor: 'rgba(255,255,255,0.18)', borderRadius: radius.sm, paddingHorizontal: 10, paddingVertical: 6 },
  shareText: { color: colors.white, fontSize: 12, fontFamily: fonts.semibold, fontWeight: '600' },
  cardBody: { padding: 16 },
  stopNum: { width: 24, height: 24, borderRadius: 12, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  stopNumText: { color: colors.white, fontSize: 12, fontFamily: fonts.bold, fontWeight: '800' },
  stopName: { flex: 1, fontSize: 14, fontFamily: fonts.medium, fontWeight: '500', color: colors.text },
  stopEmpty: { fontSize: 13, color: colors.textFaint },
  editBtn: { alignSelf: 'flex-end', borderWidth: 1, borderColor: colors.primary, borderRadius: radius.sm, paddingHorizontal: 14, paddingVertical: 7, marginTop: 4 },
  editText: { color: colors.primary, fontSize: 12, fontFamily: fonts.semibold, fontWeight: '600' },
});
