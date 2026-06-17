import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import LoginRequired from '@/components/LoginRequired';
import { Rating } from '@/components/Rating';
import { Venue } from '@/lib/api/types';
import { useMyVenuesQuery } from '@/lib/hooks/queries';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

type StatusKey = 'approved' | 'review' | 'rejected';
const TABS: { key: StatusKey; label: string; color: string; soft: string }[] = [
  { key: 'approved', label: '승인완료', color: '#1F9D63', soft: '#E4F5EC' },
  { key: 'review', label: '검토 중', color: '#E0930A', soft: '#FBF0D6' },
  { key: 'rejected', label: '거절됨', color: colors.danger, soft: '#FBE7E4' },
];

export default function MyVenuesScreen() {
  const router = useRouter();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const { data, isLoading } = useMyVenuesQuery();
  const [tab, setTab] = useState<StatusKey>('approved');

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: '내가 등록한 장소' }} />
        <LoginRequired description="내가 등록한 장소는 로그인 후에 확인할 수 있어요." />
      </View>
    );
  }
  // 목록에 승인상태 필드가 없어 현재 등록분은 승인완료로 표시(검토중·거절됨은 BE 확장 시 연동)
  const shown = tab === 'approved' ? (data ?? []) : [];
  const cfg = TABS.find((t) => t.key === tab)!;

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{
        title: '내가 등록한 장소',
        headerRight: () => (
          <Pressable hitSlop={8} onPress={() => router.push('/venue/register')}>
            <Ionicons name="add" size={26} color={colors.primary} />
          </Pressable>
        ),
      }} />

      {/* 상태 탭 */}
      <View style={styles.tabs}>
        {TABS.map((t) => (
          <Pressable key={t.key} style={styles.tab} onPress={() => setTab(t.key)}>
            <Text style={[styles.tabText, tab === t.key && { color: t.color, fontFamily: fonts.bold, fontWeight: '800' }]}>{t.label}</Text>
            {tab === t.key && <View style={[styles.tabBar, { backgroundColor: t.color }]} />}
          </Pressable>
        ))}
      </View>

      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : shown.length ? (
        <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 28 }}>
          {shown.map((v) => <VenueStatusCard key={v.id} venue={v} statusLabel={cfg.label} color={cfg.color} soft={cfg.soft} />)}
        </ScrollView>
      ) : (
        <View style={styles.empty}>
          <Image source={require('../../../assets/images/empty-venues.png')} style={styles.emptyImg} contentFit="contain" />
          <Text style={styles.emptyTitle}>등록한 장소가 없습니다</Text>
          <Text style={styles.emptyText}>나만의 장소들을 공유해보세요!</Text>
          <Pressable style={styles.emptyBtn} onPress={() => router.push('/venue/register')}>
            <Text style={styles.emptyBtnText}>등록하러가기</Text>
            <Ionicons name="chevron-forward" size={14} color={colors.white} />
          </Pressable>
        </View>
      )}
    </View>
  );
}

function VenueStatusCard({ venue, statusLabel, color, soft }: { venue: Venue; statusLabel: string; color: string; soft: string }) {
  const router = useRouter();
  const region = venue.address?.split(' ')[0];
  const badges = [region, venue.category].filter(Boolean) as string[];

  return (
    <Pressable style={styles.card} onPress={() => router.push(`/venue/${venue.id}`)}>
      <View>
        <Image source={venue.imageUrl} style={styles.cover} contentFit="cover" transition={200} />
        <View style={[styles.statusBadge, { backgroundColor: color }]}><Text style={styles.statusText}>{statusLabel}</Text></View>
      </View>
      <View style={styles.body}>
        <View style={styles.nameRow}>
          <Text style={styles.name} numberOfLines={1}>{venue.name}</Text>
          {badges.map((b) => <View key={b} style={styles.badge}><Text style={styles.badgeText}>{b}</Text></View>)}
        </View>
        <Text style={styles.addr} numberOfLines={2}>{venue.address}</Text>
        <View style={{ marginTop: 6 }}><Rating value={venue.avgRating} count={venue.reviewCount} /></View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  tabs: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: colors.border },
  tab: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  tabText: { fontSize: 14, color: colors.textFaint, fontFamily: fonts.medium, fontWeight: '500' },
  tabBar: { position: 'absolute', bottom: -1, height: 2.5, width: '55%', borderRadius: 2 },
  card: { backgroundColor: colors.bgCard, borderRadius: radius.lg, marginBottom: 16, overflow: 'hidden', ...shadow.card },
  cover: { width: '100%', height: 150, backgroundColor: colors.bgSoft },
  statusBadge: { position: 'absolute', top: 12, right: 12, borderRadius: radius.pill, paddingHorizontal: 12, paddingVertical: 5 },
  statusText: { color: colors.white, fontSize: 12, fontFamily: fonts.bold, fontWeight: '800' },
  body: { padding: 14 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  name: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, flexShrink: 1 },
  badge: { borderWidth: 1, borderColor: colors.accent, borderRadius: radius.sm, paddingHorizontal: 6, paddingVertical: 1 },
  badgeText: { fontSize: 10, color: colors.accent, fontFamily: fonts.semibold, fontWeight: '600' },
  addr: { fontSize: 13, color: colors.textSub, marginTop: 6, lineHeight: 19 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8, paddingBottom: 40 },
  emptyImg: { width: 170, height: 140, marginBottom: 8 },
  emptyTitle: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 8 },
  emptyText: { fontSize: 14, color: colors.textSub },
  emptyBtn: { flexDirection: 'row', alignItems: 'center', gap: 2, backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 22, paddingVertical: 11, marginTop: 12 },
  emptyBtnText: { color: colors.white, fontSize: 14, fontFamily: fonts.bold, fontWeight: '800' },
});
