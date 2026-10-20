import { Ionicons } from '@expo/vector-icons';
import { Image } from '@/components/Image';
import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import LoginRequired from '@/components/LoginRequired';
import { CountLine } from '@/components/CountLine';
import { Review } from '@/lib/api/reviews';
import { useDeleteReviewMutation, useMyReviewsQuery, useVenueDetailQuery } from '@/lib/hooks/queries';
import { useT } from '@/lib/i18n';
import { useAuthStore } from '@/lib/store/authStore';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

export default function MyReviewsScreen() {
  const t = useT();
  const insets = useSafeAreaInsets();   // 안드로이드는 edge-to-edge 라 시스템 바 아래까지 그린다
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const { data, isLoading } = useMyReviewsQuery();
  const [tab, setTab] = useState<'place' | 'route'>('place');

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: t('profile.menu.reviews') }} />
        <LoginRequired description={t('myReviews.loginDesc')} />
      </View>
    );
  }

  const placeReviews = (data ?? []).filter((r) => r.targetType === 'VENUE');
  const shown = tab === 'place' ? placeReviews : [];

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: t('profile.menu.reviews') }} />

      {/* 세그먼트 탭 */}
      <View style={styles.tabs}>
        <Tab label={t('myReviews.tabPlace')} active={tab === 'place'} onPress={() => setTab('place')} />
        <Tab label={t('myReviews.tabRoute')} active={tab === 'route'} onPress={() => setTab('route')} />
      </View>

      {isLoading ? (
        <ActivityIndicator color={colors.primary} style={{ marginTop: 40 }} />
      ) : shown.length ? (
        <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 28 + insets.bottom }}>
          <CountLine k="myReviews.count" n={shown.length} style={styles.count} numStyle={styles.countNum} />
          {shown.map((r) => <ReviewCard key={r.id} review={r} />)}
        </ScrollView>
      ) : <Empty />}
    </View>
  );
}

function Tab({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable style={styles.tab} onPress={onPress}>
      <Text style={[styles.tabText, active && styles.tabTextOn]}>{label}</Text>
      {active && <View style={styles.tabBar} />}
    </Pressable>
  );
}

function ReviewCard({ review }: { review: Review }) {
  const t = useT();
  const router = useRouter();
  const del = useDeleteReviewMutation();
  const { data: v } = useVenueDetailQuery(review.targetId);
  const region = v?.address?.split(' ')[0];
  const date = review.createdAt?.slice(0, 10).replace(/-/g, '.');

  return (
    <View style={styles.card}>
      <View style={styles.cardTop}>
        <Image source={v?.imageUrl} style={styles.thumb} contentFit="cover" />
        <View style={{ flex: 1 }}>
          <View style={styles.nameRow}>
            <Text style={styles.name} numberOfLines={1}>{v?.name ?? t('common.stopType.venue')}</Text>
            {!!region && <View style={styles.badge}><Text style={styles.badgeText}>{region}</Text></View>}
          </View>
          <View style={styles.starRow}>
            {[1, 2, 3, 4, 5].map((n) => <Ionicons key={n} name={n <= review.rating ? 'star' : 'star-outline'} size={12} color={colors.star} />)}
            <Text style={styles.date}>{t('myReviews.date', { date })}</Text>
          </View>
          {!!review.content && <Text style={styles.content} numberOfLines={3}>{review.content}</Text>}
        </View>
      </View>
      <View style={styles.actions}>
        <Pressable style={styles.action} onPress={() => router.push(`/review/write?targetType=VENUE&targetId=${review.targetId}&targetName=${encodeURIComponent(v?.name ?? '')}`)}>
          <Ionicons name="create-outline" size={15} color={colors.textSub} />
          <Text style={styles.actionText}>{t('myReviews.edit')}</Text>
        </Pressable>
        <View style={styles.actionDivider} />
        <Pressable style={styles.action} onPress={() => del.mutate(review.id)}>
          <Ionicons name="trash-outline" size={15} color={colors.textSub} />
          <Text style={styles.actionText}>{t('myReviews.delete')}</Text>
        </Pressable>
      </View>
    </View>
  );
}

function Empty() {
  const t = useT();
  return (
    <View style={styles.empty}>
      <Image source={require('../../../assets/images/empty-reviews.png')} style={styles.emptyImg} contentFit="contain" />
      <Text style={styles.emptyText}>{t('myReviews.empty')}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  tabs: { flexDirection: 'row', borderBottomWidth: 1, borderBottomColor: colors.border, backgroundColor: colors.bg },
  tab: { flex: 1, alignItems: 'center', paddingVertical: 14 },
  tabText: { fontSize: 15, color: colors.textFaint, fontFamily: fonts.medium, fontWeight: '500' },
  tabTextOn: { color: colors.text, fontFamily: fonts.bold, fontWeight: '800' },
  tabBar: { position: 'absolute', bottom: -1, height: 2.5, width: '60%', backgroundColor: colors.primary, borderRadius: 2 },
  count: { fontSize: 15, color: colors.text, fontFamily: fonts.semibold, fontWeight: '600', marginBottom: 14 },
  countNum: { color: colors.primary, fontFamily: fonts.bold, fontWeight: '800' },
  card: { backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 14, marginBottom: 14, ...shadow.card },
  cardTop: { flexDirection: 'row', gap: 12 },
  thumb: { width: 84, height: 84, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  name: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, flexShrink: 1 },
  badge: { borderWidth: 1, borderColor: colors.accent, borderRadius: radius.sm, paddingHorizontal: 6, paddingVertical: 1 },
  badgeText: { fontSize: 10, color: colors.accent, fontFamily: fonts.semibold, fontWeight: '600' },
  starRow: { flexDirection: 'row', alignItems: 'center', gap: 1, marginTop: 5 },
  date: { fontSize: 11, color: colors.textFaint, marginLeft: 6 },
  content: { fontSize: 12, color: colors.textSub, marginTop: 6, lineHeight: 17 },
  actions: { flexDirection: 'row', alignItems: 'center', borderTopWidth: 1, borderTopColor: colors.border, marginTop: 12, paddingTop: 4 },
  action: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 5, paddingVertical: 8 },
  actionText: { fontSize: 13, color: colors.textSub, fontFamily: fonts.medium, fontWeight: '500' },
  actionDivider: { width: 1, height: 16, backgroundColor: colors.border },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 18, paddingBottom: 40 },
  emptyImg: { width: 200, height: 150 },
  emptyText: { fontSize: 14, color: colors.textSub, textAlign: 'center', lineHeight: 22, fontFamily: fonts.semibold, fontWeight: '600' },
});
