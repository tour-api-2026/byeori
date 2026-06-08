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
import { colors, fonts, radius, space } from '@/lib/theme';

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

  const toggle = () => {
    const willAdd = !has;
    toggleLocal(vid);
    (willAdd ? wishlist.add : wishlist.remove).mutate({ targetType: 'VENUE', targetId: vid });
  };

  if (isLoading || !v) {
    return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* 상단 바 */}
      <View style={styles.topBar}>
        <Pressable hitSlop={8} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color={colors.text} /></Pressable>
        <Pressable hitSlop={8} onPress={toggle}><Ionicons name={has ? 'heart' : 'heart-outline'} size={24} color={has ? colors.hanbok : colors.text} /></Pressable>
      </View>

      <ScrollView contentContainerStyle={{ paddingBottom: 32 }} showsVerticalScrollIndicator={false}>
        <Image source={v.imageUrl} style={styles.hero} contentFit="cover" transition={200} />

        <View style={styles.body}>
          <View style={styles.titleRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>{v.name}</Text>
              <Text style={styles.cat}>{v.category} · {v.address}</Text>
            </View>
            <Pressable style={styles.addBtn} onPress={() => router.push('/itinerary')}>
              <Text style={styles.addBtnText}>내 여행에 추가</Text>
            </Pressable>
          </View>

          <View style={{ marginTop: 10 }}><Rating value={v.avgRating} count={v.reviewCount} size={14} /></View>

          {v.hanbokDiscount && (
            <View style={styles.hanbokBox}>
              <Text style={styles.hanbokTitle}>👘 한복 혜택</Text>
              <Text style={styles.hanbokDesc}>{v.hanbokDiscountDesc}</Text>
            </View>
          )}

          {/* 정보 (라벨 좌 / 값 우) */}
          <View style={styles.info}>
            {!!v.operatingHours && <InfoRow label="운영시간" value={v.operatingHours} />}
            {!!v.phone && <InfoRow label="전화번호" value={v.phone} />}
            {!!v.homepageUrl && <InfoRow label="웹사이트" value={v.homepageUrl} />}
          </View>
          {(v.source === 'KOPIS' || v.source === 'TOURAPI') && (
            <Text style={styles.source}>출처: {v.source === 'KOPIS' ? '공연예술통합전산망(KOPIS)' : '한국관광공사 TourAPI'}</Text>
          )}

          {/* 진행 중인 행사 */}
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

          {/* 방문자 코멘트 (태그 투표) */}
          <View style={styles.section}>
            <View style={styles.commentHead}>
              <Text style={styles.sectionTitle}>방문자 코멘트</Text>
              <Pressable hitSlop={8} onPress={() => router.push(`/review/write?targetType=VENUE&targetId=${vid}&targetName=${encodeURIComponent(v.name)}`)}>
                <Text style={styles.writeLink}>코멘트 작성 +</Text>
              </Pressable>
            </View>
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

            {/* 리뷰 */}
            <View style={{ marginTop: 14, gap: 10 }}>
              {reviews.data?.length ? reviews.data.map((r) => (
                <View key={r.id} style={styles.reviewCard}>
                  <View style={styles.reviewTop}>
                    <View style={styles.reviewAvatar} />
                    <Text style={styles.reviewUser}>사용자{r.userId}</Text>
                  </View>
                  <View style={styles.reviewStars}>
                    {[1, 2, 3, 4, 5].map((n) => (
                      <Ionicons key={n} name={n <= r.rating ? 'star' : 'star-outline'} size={12} color={colors.star} />
                    ))}
                  </View>
                  {!!r.content && <Text style={styles.reviewContent}>{r.content}</Text>}
                </View>
              )) : <Text style={styles.noReview}>첫 코멘트를 남겨보세요</Text>}
            </View>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.infoRow}>
      <Text style={styles.infoLabel}>{label}</Text>
      <Text style={styles.infoValue} numberOfLines={1}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  topBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: space.md, paddingVertical: 8 },
  hero: { width: '100%', height: 230, backgroundColor: colors.bgSoft },
  body: { padding: space.lg },
  titleRow: { flexDirection: 'row', alignItems: 'flex-start', gap: 12 },
  name: { fontSize: 22, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  cat: { fontSize: 13, color: colors.textFaint, marginTop: 4 },
  addBtn: { backgroundColor: colors.primary, borderRadius: radius.md, paddingHorizontal: 14, paddingVertical: 11 },
  addBtnText: { color: colors.white, fontSize: 13, fontFamily: fonts.bold, fontWeight: '800' },
  hanbokBox: { backgroundColor: '#FDECEC', borderRadius: radius.md, padding: 14, marginTop: 16 },
  hanbokTitle: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '800', color: colors.hanbok },
  hanbokDesc: { fontSize: 13, color: '#A53A3D', marginTop: 4 },
  info: { marginTop: 18, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: 6 },
  infoRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingVertical: 11, borderBottomWidth: 1, borderBottomColor: colors.border },
  infoLabel: { fontSize: 14, color: colors.textSub, fontFamily: fonts.medium, fontWeight: '500' },
  infoValue: { fontSize: 14, color: colors.text, fontFamily: fonts.medium, fontWeight: '500', flexShrink: 1, textAlign: 'right', marginLeft: 16 },
  source: { fontSize: 11, color: colors.textFaint, marginTop: 8 },
  section: { marginTop: 26 },
  sectionTitle: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginBottom: 12 },
  perfRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 8 },
  perfImg: { width: 56, height: 56, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  perfTitle: { fontSize: 14, fontFamily: fonts.semibold, fontWeight: '600', color: colors.text },
  perfDate: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  stateBadge: { backgroundColor: colors.bgSoft, borderRadius: radius.sm, paddingHorizontal: 8, paddingVertical: 4 },
  stateOn: { backgroundColor: colors.primarySoft },
  stateText: { fontSize: 11, fontFamily: fonts.semibold, fontWeight: '600', color: colors.textFaint },
  stateTextOn: { color: colors.primary },
  commentHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  writeLink: { fontSize: 13, color: colors.accent, fontFamily: fonts.semibold, fontWeight: '600' },
  tagWrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  tag: { backgroundColor: colors.white, borderRadius: radius.pill, paddingHorizontal: 14, paddingVertical: 8, borderWidth: 1, borderColor: colors.accent },
  tagOn: { backgroundColor: colors.accent },
  tagText: { fontSize: 13, color: colors.accent, fontFamily: fonts.medium, fontWeight: '500' },
  tagTextOn: { color: colors.white },
  reviewCard: { backgroundColor: colors.bgSoft, borderRadius: radius.md, padding: 14 },
  reviewTop: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  reviewAvatar: { width: 28, height: 28, borderRadius: 14, backgroundColor: colors.border },
  reviewUser: { fontSize: 13, fontFamily: fonts.semibold, fontWeight: '600', color: colors.text },
  reviewStars: { flexDirection: 'row', alignItems: 'center', gap: 1, marginTop: 8 },
  reviewContent: { fontSize: 13, color: colors.textSub, marginTop: 8, lineHeight: 19 },
  noReview: { fontSize: 13, color: colors.textFaint },
});
