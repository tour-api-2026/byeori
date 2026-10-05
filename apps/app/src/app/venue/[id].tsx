import { Ionicons } from '@expo/vector-icons';
import { Image } from '@/components/Image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { ContentComments } from '@/components/ContentComments';
import { PerformanceCarousel, stateCaption } from '@/components/PerformanceCarousel';
import { Rating } from '@/components/Rating';
import { ReportDialog } from '@/components/ReportDialog';
import {
  useDeleteVenueMutation, useReportVenueMutation, useToggleWishlistMutation,
  useVenueDetailQuery, useVenuePerformancesQuery,
} from '@/lib/hooks/queries';
import { useAuthStore } from '@/lib/store/authStore';
import { useBookmarkStore } from '@/lib/store/bookmarkStore';
import { useRecentStore } from '@/lib/store/recentStore';
import { colors, fonts, radius, space } from '@/lib/theme';

export default function VenueDetailScreen() {
  const { id, mine } = useLocalSearchParams<{ id: string; mine?: string }>();
  const { data: v, isLoading } = useVenueDetailQuery(id);
  // 콘텐츠 ID도 숫자라 URL만 보고 우리 id로 단정하면 엉뚱한 장소의 리뷰·위시리스트를
  // 건드릴 수 있다. 우리 레코드 id는 응답에서만 받는다(없으면 0 → 관련 기능 비활성).
  const vid = v?.id ?? 0;

  // 홈의 '최근 본 장소'에 쓰려고 기기에 남긴다. 서버로는 보내지 않는다.
  const pushRecent = useRecentStore((s) => s.push);
  useEffect(() => {
    if (!v?.name) return;
    void pushRecent({
      id: v.id ?? null,
      tourContentId: v.tourContentId ?? null,
      name: v.name,
      address: v.address ?? null,
      category: v.category ?? null,
      imageUrl: v.imageUrl ?? null,
    });
  }, [v?.id, v?.tourContentId, v?.name, pushRecent]);
  const isMine = mine === '1';
  const router = useRouter();
  const del = useDeleteVenueMutation();

  const confirmDelete = () => {
    Alert.alert('장소 삭제', '이 장소를 삭제할까요? 되돌릴 수 없습니다.', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () =>
          del.mutate(vid, {
            onSuccess: () => router.back(),
            onError: (e: any) => Alert.alert('삭제 실패', e?.message ?? '오류'),
          }),
      },
    ]);
  };
  const perfs = useVenuePerformancesQuery(vid);
  const has = useBookmarkStore((s) => s.venueIds.includes(vid));
  const toggleLocal = useBookmarkStore((s) => s.toggle);
  const wishlist = useToggleWishlistMutation();

  const toggle = () => {
    const willAdd = !has;
    toggleLocal(vid);
    (willAdd ? wishlist.add : wishlist.remove).mutate({ targetType: 'VENUE', targetId: vid });
  };

  // 신고 — 구글 UGC 정책상 사용자 생성 콘텐츠(장소·리뷰)에는 신고 수단이 필요
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const reportVenue = useReportVenueMutation();
  const [reporting, setReporting] = useState(false);
  // 공사 API 소개글은 수십 줄인 경우가 있어, 접어두지 않으면 운영시간·문의가 화면 밖으로 밀린다.
  const [overviewOpen, setOverviewOpen] = useState(false);

  const openReport = () => {
    if (!isLoggedIn) {
      Alert.alert('로그인 필요', '신고하려면 로그인이 필요해요.', [
        { text: '취소', style: 'cancel' },
        { text: '로그인', onPress: () => router.push('/login') },
      ]);
      return;
    }
    setReporting(true);
  };

  const submitReport = (reason: string) => {
    reportVenue.mutate(
      { id: vid, reason },
      {
        onSuccess: () => {
          setReporting(false);
          Alert.alert('신고 접수', '신고가 접수되었어요. 운영진이 확인 후 조치합니다.');
        },
        onError: (e: any) => {
          setReporting(false);
          Alert.alert('신고 실패', e?.message ?? '잠시 후 다시 시도해주세요.');
        },
      },
    );
  };

  if (isLoading || !v) {
    return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* 상단 바 */}
      <View style={styles.topBar}>
        <Pressable hitSlop={8} onPress={() => router.back()}><Ionicons name="chevron-back" size={24} color={colors.text} /></Pressable>
        <View style={styles.topActions}>
          {isMine && (
            <>
              <Pressable hitSlop={8} onPress={() => router.push(`/venue/register?editId=${vid}`)}>
                <Ionicons name="create-outline" size={23} color={colors.text} />
              </Pressable>
              <Pressable hitSlop={8} onPress={confirmDelete} disabled={del.isPending}>
                <Ionicons name="trash-outline" size={22} color={colors.danger} />
              </Pressable>
            </>
          )}
          {!isMine && (
            <Pressable hitSlop={8} onPress={openReport}>
              <Ionicons name="flag-outline" size={21} color={colors.textFaint} />
            </Pressable>
          )}
          <Pressable hitSlop={8} onPress={toggle}><Ionicons name={has ? 'heart' : 'heart-outline'} size={24} color={has ? colors.hanbok : colors.text} /></Pressable>
        </View>
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

          {/* 소개글 — 상세를 열 때 한국관광공사 OpenAPI에서 실시간으로 받아온다. */}
          {!!v.liveInfo?.overview && (
            <Pressable onPress={() => setOverviewOpen((o) => !o)}>
              <Text style={styles.overview} numberOfLines={overviewOpen ? undefined : 5}>
                {v.liveInfo.overview}
              </Text>
              <Text style={styles.overviewMore}>{overviewOpen ? '접기' : '더보기'}</Text>
            </Pressable>
          )}

          {/* 정보 (라벨 좌 / 값 우).
              실시간 조회값을 우선 쓰고, 없을 때만 저장된 값으로 채운다. */}
          <View style={styles.info}>
            {!!(v.liveInfo?.useTime || v.operatingHours) && (
              <InfoRow label="운영시간" value={(v.liveInfo?.useTime || v.operatingHours) as string} />
            )}
            {!!v.liveInfo?.restDate && <InfoRow label="휴무일" value={v.liveInfo.restDate} />}
            {!!(v.liveInfo?.infoCenter || v.phone) && (
              <InfoRow label="문의" value={(v.liveInfo?.infoCenter || v.phone) as string} />
            )}
            {!!v.liveInfo?.parking && <InfoRow label="주차" value={v.liveInfo.parking} />}
            {!!(v.liveInfo?.homepage || v.homepageUrl) && (
              <InfoRow label="웹사이트" value={(v.liveInfo?.homepage || v.homepageUrl) as string} />
            )}
          </View>
          {/* 공공데이터 출처 표기 — 기관명 기준(API 서비스명 단독 표기 불가) */}
          {(v.source === 'KOPIS' || v.source === 'TOURAPI') && (
            <Text style={styles.source}>
              출처: ⓒ{v.source === 'KOPIS' ? '공연예술통합전산망(KOPIS)' : '한국관광공사'}
            </Text>
          )}

          {/* 이 장소의 행사 — 서버가 끝난 건 빼고 준다(진행 중·예정만). */}
          {!!perfs.data?.length && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>진행 중·예정 행사</Text>
              <PerformanceCarousel items={perfs.data} caption={stateCaption} />
            </View>
          )}

          <ContentComments targetType="VENUE" targetId={vid} targetName={v.name} />
        </View>
      </ScrollView>

      <ReportDialog
        visible={reporting}
        title="장소 신고"
        pending={reportVenue.isPending}
        onSelect={submitReport}
        onClose={() => setReporting(false)}
      />
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
  topActions: { flexDirection: 'row', alignItems: 'center', gap: 16 },
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
  overview: { fontSize: 14, lineHeight: 22, color: colors.textSub, marginTop: 16 },
  overviewMore: { fontSize: 13, fontFamily: fonts.semibold, fontWeight: '600', color: colors.primary, marginTop: 6 },
  source: { fontSize: 11, color: colors.textFaint, marginTop: 8 },
  section: { marginTop: 26 },
  sectionTitle: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginBottom: 12 },
});
