import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { ActivityIndicator, Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { usePerformanceQuery } from '@/lib/hooks/queries';
import { sized } from '@/lib/img';
import { colors, fonts, radius, space } from '@/lib/theme';

const STATE_LABEL: Record<string, string> = {
  ONGOING: '진행 중',
  UPCOMING: '예정',
  ENDED: '종료',
};

/**
 * 행사 상세.
 *
 * 전에는 목록에서 카드를 누르면 예매처로 바로 튕기거나(서울 행사) 아무 일도 일어나지
 * 않았다(KOPIS 공연 — 예매 링크가 없다). 우리 화면에서 포스터·기간·장소를 보여주고,
 * 예매처가 있으면 그때 내보낸다.
 */
export default function PerformanceDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const { data: p, isLoading } = usePerformanceQuery(Number(id));

  if (isLoading) {
    return (
      <SafeAreaView style={styles.safe}>
        <ActivityIndicator color={colors.primary} style={{ marginTop: 60 }} />
      </SafeAreaView>
    );
  }
  if (!p) {
    return (
      <SafeAreaView style={styles.safe}>
        <Text style={styles.empty}>행사를 찾을 수 없어요</Text>
      </SafeAreaView>
    );
  }

  const period = p.startDate === p.endDate ? p.startDate : `${p.startDate} ~ ${p.endDate}`;
  // 좌표만 있고 우리 장소로 연결되지 않은 행사가 대부분이라, 그때는 지도 앱으로 넘긴다.
  const openMap = () => {
    if (p.lat == null || p.lng == null) return;
    Linking.openURL(`https://map.kakao.com/link/map/${encodeURIComponent(p.title)},${p.lat},${p.lng}`);
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <ScrollView contentContainerStyle={{ paddingBottom: 40 }}>
        <View style={styles.header}>
          <Pressable hitSlop={10} onPress={() => router.back()}>
            <Ionicons name="chevron-back" size={24} color={colors.text} />
          </Pressable>
        </View>

        <Image
          source={sized(p.posterImageUrl, 720, 960)}
          style={styles.poster}
          contentFit="cover"
          transition={200}
        />

        <View style={styles.body}>
          <View style={styles.badges}>
            <View style={[styles.badge, p.state === 'ONGOING' && styles.badgeOn]}>
              <Text style={[styles.badgeText, p.state === 'ONGOING' && styles.badgeTextOn]}>
                {STATE_LABEL[p.state] ?? p.state}
              </Text>
            </View>
            {p.genre ? (
              <View style={styles.badge}><Text style={styles.badgeText}>{p.genre}</Text></View>
            ) : null}
            {p.traditional ? (
              <View style={[styles.badge, styles.badgeTrad]}>
                <Text style={[styles.badgeText, styles.badgeTextTrad]}>전통</Text>
              </View>
            ) : null}
          </View>

          <Text style={styles.title}>{p.title}</Text>

          <View style={styles.rows}>
            <Row icon="calendar-outline" label="기간" value={period} />
            {p.lat != null && p.lng != null ? (
              <Row icon="location-outline" label="위치" value="지도에서 보기" onPress={openMap} />
            ) : null}
          </View>

          {p.overview ? (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>소개</Text>
              <Text style={styles.overview}>{p.overview}</Text>
              <Text style={styles.source}>출처: ⓒ한국관광공사</Text>
            </View>
          ) : null}

          {p.venueId ? (
            <Pressable style={styles.linkBtn} onPress={() => router.push(`/venue/${p.venueId}`)}>
              <Ionicons name="business-outline" size={17} color={colors.primary} />
              <Text style={styles.linkText}>이 행사가 열리는 장소 보기</Text>
              <Ionicons name="chevron-forward" size={17} color={colors.textFaint} />
            </Pressable>
          ) : null}

          {p.externalBookingUrl ? (
            <Pressable
              style={styles.bookBtn}
              onPress={() => WebBrowser.openBrowserAsync(p.externalBookingUrl as string)}
            >
              <Text style={styles.bookText}>예매·자세히 보기</Text>
            </Pressable>
          ) : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function Row({
  icon, label, value, onPress,
}: { icon: keyof typeof Ionicons.glyphMap; label: string; value: string; onPress?: () => void }) {
  const content = (
    <View style={styles.row}>
      <Ionicons name={icon} size={16} color={colors.textFaint} />
      <Text style={styles.rowLabel}>{label}</Text>
      <Text style={[styles.rowValue, onPress && styles.rowLink]} numberOfLines={2}>{value}</Text>
    </View>
  );
  return onPress ? <Pressable onPress={onPress}>{content}</Pressable> : content;
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: { paddingHorizontal: space.lg, paddingVertical: 10 },
  poster: { width: '100%', height: 320, backgroundColor: colors.bgCard },
  body: { padding: space.lg },
  badges: { flexDirection: 'row', gap: 6, marginBottom: 10, flexWrap: 'wrap' },
  badge: { paddingHorizontal: 9, paddingVertical: 4, borderRadius: 999, backgroundColor: colors.bgCard },
  badgeOn: { backgroundColor: colors.primarySoft },
  badgeTrad: { backgroundColor: '#FDECEC' },
  badgeText: { fontSize: 11, fontWeight: '700', color: colors.textFaint },
  badgeTextOn: { color: colors.primary },
  badgeTextTrad: { color: colors.hanbok },
  title: { fontSize: 20, fontWeight: '800', color: colors.text, lineHeight: 28 },
  rows: { marginTop: 14, gap: 10 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  rowLabel: { fontSize: 13, color: colors.textFaint, width: 40 },
  rowValue: { fontSize: 14, color: colors.text, flex: 1 },
  rowLink: { color: colors.accent, fontWeight: '700' },
  section: { marginTop: 22 },
  sectionTitle: { fontSize: 15, fontWeight: '800', color: colors.text, marginBottom: 8 },
  overview: { fontSize: 14, color: colors.text, lineHeight: 22 },
  source: { fontSize: 11, color: colors.textFaint, marginTop: 10 },
  linkBtn: {
    flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 22,
    backgroundColor: colors.bgCard, borderRadius: radius.md, padding: 14,
  },
  linkText: { flex: 1, fontSize: 14, fontWeight: '700', color: colors.text },
  bookBtn: {
    marginTop: 12, backgroundColor: colors.primary, borderRadius: radius.md,
    paddingVertical: 14, alignItems: 'center',
  },
  bookText: { color: colors.white, fontSize: 15, fontWeight: '800' },
  empty: { textAlign: 'center', marginTop: 60, color: colors.textFaint, fontSize: 14 },
});
