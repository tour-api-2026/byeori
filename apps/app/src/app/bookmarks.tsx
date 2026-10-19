import { Ionicons } from '@expo/vector-icons';
import { Image } from '@/components/Image';
import { Stack, useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { CountLine } from '@/components/CountLine';
import LoginRequired from '@/components/LoginRequired';
import { Rating } from '@/components/Rating';
import { Refresh } from '@/components/Refresh';
import { Wishlist } from '@/lib/api/wishlists';
import { useMyWishlistsQuery, useVenueDetailQuery } from '@/lib/hooks/queries';
import { useT } from '@/lib/i18n';
import { categoryLabel } from '@/lib/i18n/labels';
import { useAuthStore } from '@/lib/store/authStore';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

export default function BookmarksScreen() {
  const t = useT();
  const insets = useSafeAreaInsets();   // 안드로이드는 edge-to-edge 라 시스템 바 아래까지 그린다
  const router = useRouter();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const { data, isLoading } = useMyWishlistsQuery();

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: t('profile.menu.bookmarks') }} />
        <LoginRequired description={t('bookmarks.loginDesc')} />
      </View>
    );
  }

  if (isLoading) return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: t('profile.menu.bookmarks') }} />
      {!data?.length ? (
        <View style={styles.empty}>
          <Image source={require('../../assets/images/empty-bookmarks.png')} style={styles.emptyImg} contentFit="contain" />
          <Text style={styles.emptyTitle}>{t('bookmarks.emptyTitle')}</Text>
          <Pressable style={styles.emptyBtn} onPress={() => router.replace('/(tabs)')}>
            <Text style={styles.emptyBtnText}>{t('bookmarks.emptyBtn')}</Text>
            <Ionicons name="chevron-forward" size={14} color={colors.white} />
          </Pressable>
        </View>
      ) : (
        <ScrollView
          refreshControl={<Refresh />}
          contentContainerStyle={{ padding: space.lg, paddingBottom: 28 + insets.bottom }}
          showsVerticalScrollIndicator={false}>
          <CountLine k="bookmarks.count" n={data.length} style={styles.count} numStyle={styles.countNum} />
          <View style={styles.grid}>
            {data.map((w) => <BookmarkCard key={w.id} item={w} />)}
          </View>
        </ScrollView>
      )}
    </View>
  );
}

function BookmarkCard({ item }: { item: Wishlist }) {
  const router = useRouter();
  const t = useT();
  const isVenue = item.targetType === 'VENUE';
  const { data: v } = useVenueDetailQuery(isVenue ? item.targetId : 0);
  const region = v?.address?.split(' ')[0];
  const badges = [region, v?.category && categoryLabel(t, v.category)].filter(Boolean) as string[];

  return (
    <Pressable style={styles.card} onPress={() => isVenue && router.push(`/venue/${item.targetId}`)}>
      <Image source={item.imageUrl} style={styles.img} contentFit="cover" transition={200} />
      <View style={styles.body}>
        <View style={styles.nameRow}>
          <Text style={styles.name} numberOfLines={1}>{item.name ?? t('common.stopType.venue')}</Text>
          {badges.slice(0, 2).map((b) => <View key={b} style={styles.badge}><Text style={styles.badgeText}>{b}</Text></View>)}
        </View>
        {!!v?.address && <Text style={styles.addr} numberOfLines={2}>{v.address}</Text>}
        <View style={{ marginTop: 6 }}><Rating value={item.avgRating} count={item.reviewCount} /></View>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  count: { fontSize: 15, color: colors.text, fontFamily: fonts.semibold, fontWeight: '600', marginBottom: 14 },
  countNum: { color: colors.primary, fontFamily: fonts.bold, fontWeight: '800' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' },
  card: { width: '48.5%', backgroundColor: colors.bgCard, borderRadius: radius.lg, marginBottom: 14, overflow: 'hidden', ...shadow.card },
  img: { width: '100%', height: 100, backgroundColor: colors.bgSoft },
  body: { padding: 10 },
  nameRow: { flexDirection: 'row', alignItems: 'center', gap: 4, flexWrap: 'wrap' },
  name: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  badge: { borderWidth: 1, borderColor: colors.accent, borderRadius: radius.sm, paddingHorizontal: 5, paddingVertical: 1 },
  badgeText: { fontSize: 9, color: colors.accent, fontFamily: fonts.semibold, fontWeight: '600' },
  addr: { fontSize: 11, color: colors.textSub, marginTop: 4, lineHeight: 15 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8, paddingBottom: 40 },
  emptyImg: { width: 200, height: 150, marginBottom: 10 },
  emptyTitle: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, textAlign: 'center', lineHeight: 24 },
  emptyBtn: { flexDirection: 'row', alignItems: 'center', gap: 2, backgroundColor: colors.primary, borderRadius: radius.pill, paddingHorizontal: 22, paddingVertical: 11, marginTop: 14 },
  emptyBtnText: { color: colors.white, fontSize: 14, fontFamily: fonts.bold, fontWeight: '800' },
});
