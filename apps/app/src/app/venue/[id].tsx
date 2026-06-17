import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Rating } from '@/components/Rating';
import {
  useContentTagsQuery, useReviewsQuery, useToggleWishlistMutation,
  useVenueDetailQuery, useVenuePerformancesQuery, useVoteTagMutation,
} from '@/lib/hooks/queries';
import { useBookmarkStore } from '@/lib/store/bookmarkStore';
import { colors, radius, space } from '@/lib/theme';

export default function VenueDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const vid = Number(id);
  const router = useRouter();
  const { data: v, isLoading } = useVenueDetailQuery(vid);
  const perfs = useVenuePerformancesQuery(vid);
  const tags = useContentTagsQuery('VENUE', vid);
  const reviews = useReviewsQuery('VENUE', vid);
  const { vote, unvote } = useVoteTagMutation('VENUE', vid);
  const has = useBookmarkStore((s) => s.venueIds.includes(vid));
  const toggleLocal = useBookmarkStore((s) => s.toggle);
  const wishlist = useToggleWishlistMutation();

  const toggle = (id2: number) => {
    const willAdd = !has;
    toggleLocal(id2);
    (willAdd ? wishlist.add : wishlist.remove).mutate({ targetType: 'VENUE', targetId: id2 });
  };

  if (isLoading || !v) {
    return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;
  }

  return (
    <View style={styles.safe}>
      <ScrollView contentContainerStyle={{ paddingBottom: 32 }} showsVerticalScrollIndicator={false}>
        {/* 히어로 */}
        <View>
          <Image source={v.imageUrl} style={styles.hero} contentFit="cover" transition={200} />
          <SafeAreaView edges={['top']} style={styles.heroBar}>
            <Pressable style={styles.iconBtn} onPress={() => router.back()}><Ionicons name="chevron-back" size={22} color={colors.white} /></Pressable>
            <Pressable style={styles.iconBtn} onPress={() => toggle(vid)}>
              <Ionicons name={has ? 'bookmark' : 'bookmark-outline'} size={20} color={colors.white} />
            </Pressable>
          </SafeAreaView>
        </View>

        <View style={styles.body}>
          <View style={styles.titleRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>{v.name}</Text>
              <Text style={styles.cat}>{v.category} · {v.address}</Text>
              <View style={{ marginTop: 6 }}><Rating value={v.avgRating} count={v.reviewCount} /></View>
            </View>
            <Pressable style={styles.addBtn} onPress={() => router.push('/itinerary')}><Text style={styles.addBtnText}>내 여행에{'\n'}추가</Text></Pressable>
          </View>

          {/* 한복 혜택 */}
          {v.hanbokDiscount && (
            <View style={styles.hanbokBox}>
              <Text style={styles.hanbokTitle}>👘 한복 혜택</Text>
              <Text style={styles.hanbokDesc}>{v.hanbokDiscountDesc}</Text>
            </View>
          )}

          {/* 정보 */}
          <View style={styles.info}>
            {!!v.operatingHours && <InfoRow icon="time-outline" text={v.operatingHours} />}
            {!!v.phone && <InfoRow icon="call-outline" text={v.phone} />}
            {!!v.homepageUrl && <InfoRow icon="globe-outline" text={v.homepageUrl} />}
            {(v.source === 'KOPIS' || v.source === 'TOURAPI') && (
              <Text style={styles.source}>출처: {v.source === 'KOPIS' ? '공연예술통합전산망(KOPIS)' : '한국관광공사 TourAPI'}</Text>
            )}
          </View>

          {/* 이 시설의 공연 */}
          {!!perfs.data?.length && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>진행 중인 행사</Text>
              {perfs.data.map((p) => (
                <View key={p.id} style={styles.perfRow}>
                  <Image source={p.posterImageUrl} style={styles.perfImg} contentFit="cover" />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.perfTitle}>{p.title}</Text>
                    <Text style={styles.perfDate}>{p.startDate} ~ {p.endDate}</Text>
                  </View>
                  <View style={[styles.stateBadge, p.state === 'ONGOING' && styles.stateOn]}>
                    <Text style={[styles.stateText, p.state === 'ONGOING' && styles.stateTextOn]}>{p.state === 'ONGOING' ? '진행중' : p.state === 'UPCOMING' ? '예정' : '종료'}</Text>
                  </View>
                </View>
              ))}
            </View>
          )}

          {/* 방문자 코멘트 태그 (탭하여 투표) */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>방문자 코멘트</Text>
            <View style={styles.tagWrap}>
              {tags.data?.map((t) => (
                <Pressable
                  key={t.commentTagId}
                  style={[styles.tag, t.voted && styles.tagOn]}
                  onPress={() => (t.voted ? unvote.mutate(t.commentTagId) : vote.mutate(t.commentTagId))}>
                  <Text style={[styles.tagText, t.voted && styles.tagTextOn]}>
                    {t.name}{t.count > 0 ? ` ${t.count}` : ''}
                  </Text>
                </Pressable>
              ))}
            </View>
          </View>

          {/* 리뷰 */}
          <View style={styles.section}>
            <View style={styles.reviewHead}>
              <Text style={styles.sectionTitle}>리뷰 {reviews.data?.length ? `(${reviews.data.length})` : ''}</Text>
              <Pressable
                style={styles.writeBtn}
                onPress={() => router.push(`/review/write?targetType=VENUE&targetId=${vid}&targetName=${encodeURIComponent(v.name)}`)}>
                <Text style={styles.writeText}>코멘트 작성</Text>
              </Pressable>
            </View>
            {reviews.data?.length ? reviews.data.map((r) => (
              <View key={r.id} style={styles.reviewCard}>
                <View style={styles.reviewStars}>
                  {[1, 2, 3, 4, 5].map((n) => (
                    <Ionicons key={n} name={n <= r.rating ? 'star' : 'star-outline'} size={12} color={colors.star} />
                  ))}
                  <Text style={styles.reviewUser}>사용자{r.userId}</Text>
                </View>
                {!!r.content && <Text style={styles.reviewContent}>{r.content}</Text>}
              </View>
            )) : <Text style={styles.noReview}>첫 리뷰를 남겨보세요</Text>}
          </View>
        </View>
      </ScrollView>
    </View>
  );
}

function InfoRow({ icon, text }: { icon: any; text: string }) {
  return (
    <View style={styles.infoRow}>
      <Ionicons name={icon} size={16} color={colors.textFaint} />
      <Text style={styles.infoText} numberOfLines={1}>{text}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  hero: { width: '100%', height: 260, backgroundColor: colors.bgSoft },
  heroBar: { position: 'absolute', top: 0, left: 0, right: 0, flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: space.md, paddingTop: 6 },
  iconBtn: { backgroundColor: 'rgba(0,0,0,0.3)', borderRadius: radius.pill, padding: 8 },
  body: { padding: space.lg },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  name: { fontSize: 22, fontWeight: '900', color: colors.text },
  cat: { fontSize: 13, color: colors.textFaint, marginTop: 4 },
  addBtn: { backgroundColor: colors.primary, borderRadius: radius.md, paddingHorizontal: 16, paddingVertical: 10 },
  addBtnText: { color: colors.white, fontSize: 13, fontWeight: '800', textAlign: 'center' },
  hanbokBox: { backgroundColor: '#FDECEC', borderRadius: radius.md, padding: 14, marginTop: 16 },
  hanbokTitle: { fontSize: 14, fontWeight: '800', color: colors.hanbok },
  hanbokDesc: { fontSize: 13, color: '#A53A3D', marginTop: 4 },
  info: { marginTop: 16, gap: 10 },
  infoRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  infoText: { fontSize: 14, color: colors.textSub, flex: 1 },
  source: { fontSize: 11, color: colors.textFaint, marginTop: 4 },
  section: { marginTop: 24 },
  sectionTitle: { fontSize: 16, fontWeight: '800', color: colors.text, marginBottom: 12 },
  perfRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 8 },
  perfImg: { width: 56, height: 56, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  perfTitle: { fontSize: 14, fontWeight: '700', color: colors.text },
  perfDate: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  stateBadge: { backgroundColor: colors.bgSoft, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 4 },
  stateOn: { backgroundColor: colors.primarySoft },
  stateText: { fontSize: 11, fontWeight: '700', color: colors.textFaint },
  stateTextOn: { color: colors.primary },
  tagWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  tag: { backgroundColor: colors.bgSoft, borderRadius: radius.pill, paddingHorizontal: 12, paddingVertical: 7, borderWidth: 1, borderColor: 'transparent' },
  tagOn: { backgroundColor: colors.primarySoft, borderColor: colors.primary },
  tagText: { fontSize: 13, color: colors.textSub, fontWeight: '600' },
  tagTextOn: { color: colors.primary },
  reviewHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  writeBtn: { backgroundColor: colors.primary, borderRadius: radius.sm, paddingHorizontal: 12, paddingVertical: 6 },
  writeText: { color: colors.white, fontSize: 12, fontWeight: '700' },
  reviewCard: { backgroundColor: colors.bgSoft, borderRadius: radius.md, padding: 12, marginBottom: 8 },
  reviewStars: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  reviewUser: { fontSize: 12, color: colors.textFaint, marginLeft: 6 },
  reviewContent: { fontSize: 13, color: colors.text, marginTop: 6 },
  noReview: { fontSize: 13, color: colors.textFaint },
});
