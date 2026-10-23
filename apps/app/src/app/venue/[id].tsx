import { Ionicons } from '@expo/vector-icons';
import { Image } from '@/components/Image';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { ContentComments } from '@/components/ContentComments';
import { PerformanceCarousel, stateCaption } from '@/components/PerformanceCarousel';
import { Rating } from '@/components/Rating';
import { Skeleton, SkeletonText } from '@/components/Skeleton';
import { ReportDialog } from '@/components/ReportDialog';
import {
  useDeleteVenueMutation, useNearbyPerformancesQuery, useReportVenueMutation, useToggleWishlistMutation,
  useVenueDetailQuery, useVenuePerformancesQuery,
} from '@/lib/hooks/queries';
import { useT } from '@/lib/i18n';
import { categoryLabel } from '@/lib/i18n/labels';
import { useAuthStore } from '@/lib/store/authStore';
import { useBookmarkStore } from '@/lib/store/bookmarkStore';
import { useRecentStore } from '@/lib/store/recentStore';
import { colors, fonts, radius, space } from '@/lib/theme';

export default function VenueDetailScreen() {
  const t = useT();
  const insets = useSafeAreaInsets();   // 안드로이드는 edge-to-edge 라 시스템 바 아래까지 그린다
  const { id, mine } = useLocalSearchParams<{ id: string; mine?: string }>();
  const { data: v, isLoading, isPlaceholderData } = useVenueDetailQuery(id);
  /**
   * 공사 실시간 조회(소개글·운영시간·휴무일·문의·웹사이트)가 아직 안 온 상태.
   *
   * 목록에서 들어왔으면 이름·사진·주소·평점은 밑그림으로 이미 그려져 있고, 느린 것은
   * 이 부분뿐이다(한국어 1.2초, 외국어 첫 조회 3.8초). 그래서 화면 전체가 아니라
   * 여기만 스켈레톤으로 둔다.
   */
  const liveLoading = isPlaceholderData;
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
    Alert.alert(t('venue.deleteTitle'), t('venue.deleteBody'), [
      { text: t('common.cancel'), style: 'cancel' },
      {
        text: t('common.delete'),
        style: 'destructive',
        onPress: () =>
          del.mutate(vid, {
            onSuccess: () => router.back(),
            onError: (e: any) => Alert.alert(t('venue.deleteFail'), e?.message ?? t('common.error.unknown')),
          }),
      },
    ]);
  };
  const perfs = useVenuePerformancesQuery(vid);
  // 공연시설명으로 못 이은 행사가 1,400여 건 남아 있다. 그쪽도 좌표는 있으니
  // "여기서 열린다"고 단정하지 않고 "이 근처에서 열린다"로 보여준다.
  const nearbyPerfs = useNearbyPerformancesQuery(v ? { lat: v.lat, lng: v.lng } : null);
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
      Alert.alert(t('common.loginNeeded'), t('report.needLogin'), [
        { text: t('common.cancel'), style: 'cancel' },
        { text: t('common.login'), onPress: () => router.push('/login') },
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
          Alert.alert(t('report.doneTitle'), t('report.doneBody'));
        },
        onError: (e: any) => {
          setReporting(false);
          Alert.alert(t('report.failTitle'), e?.message ?? t('common.tryLater'));
        },
      },
    );
  };

  // 목록 캐시에 없는 경로(딥링크, 앱 재시작 직후)로 들어오면 밑그림이 없다. 그때만
  // 화면 전체를 스켈레톤으로 그린다 — 스피너와 달리 들어올 내용의 모양을 미리 보여준다.
  if (!v) {
    return isLoading
      ? <VenueDetailSkeleton onBack={() => router.back()} />
      : <View style={styles.center}><Text style={styles.empty}>{t('venue.notFound')}</Text></View>;
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

      <ScrollView contentContainerStyle={{ paddingBottom: 32 + insets.bottom }} showsVerticalScrollIndicator={false}>
        <Image source={v.imageUrl} style={styles.hero} contentFit="cover" transition={200} />

        <View style={styles.body}>
          <View style={styles.titleRow}>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>{v.name}</Text>
              <Text style={styles.cat}>{v.category ? categoryLabel(t, v.category) : ''} · {v.address}</Text>
            </View>
            <Pressable style={styles.addBtn} onPress={() => router.push('/itinerary')}>
              <Text style={styles.addBtnText}>{t('common.addToTrip')}</Text>
            </Pressable>
          </View>

          <View style={{ marginTop: 10 }}><Rating value={v.avgRating} count={v.reviewCount} size={14} /></View>

          {v.hanbokDiscount && (
            <View style={styles.hanbokBox}>
              <Text style={styles.hanbokTitle}>{t('venue.hanbokBenefit')}</Text>
              <Text style={styles.hanbokDesc}>{v.hanbokDiscountDesc}</Text>
            </View>
          )}

          {/* 소개글 — 상세를 열 때 한국관광공사 OpenAPI에서 실시간으로 받아온다. */}
          {liveLoading && (
            <View style={{ marginTop: 16 }}><SkeletonText lines={5} /></View>
          )}
          {!liveLoading && !!v.liveInfo?.overview && (
            <Pressable onPress={() => setOverviewOpen((o) => !o)}>
              <Text style={styles.overview} numberOfLines={overviewOpen ? undefined : 5}>
                {v.liveInfo.overview}
              </Text>
              <Text style={styles.overviewMore}>{overviewOpen ? t('venue.less') : t('venue.more')}</Text>
            </Pressable>
          )}

          {/* 정보 (라벨 좌 / 값 우).
              실시간 조회값을 우선 쓰고, 없을 때만 저장된 값으로 채운다. */}
          <View style={styles.info}>
            {/* 라벨과 구분선은 바로 그리고 값만 막대로 둔다. 어떤 항목이 올지는 장소마다
                달라서, 값이 온 뒤 줄 수가 줄어들 수 있다(그 아래가 조금 당겨진다).
                그래도 빈 화면보다 낫다고 보고 흔한 네 줄을 미리 그린다. */}
            {liveLoading && (['hours', 'closed', 'contact', 'website'] as const).map((k) => (
              <View key={k} style={styles.infoRow}>
                <Text style={styles.infoLabel}>{t(`venue.info.${k}`)}</Text>
                <Skeleton width="40%" height={14} />
              </View>
            ))}
            {/* 아래 줄들은 liveLoading 일 때 따로 막지 않아도 된다 — 밑그림은 liveInfo 와
                operatingHours·phone·homepageUrl 을 모두 null 로 채우므로 하나도 그려지지
                않는다(queries.ts 의 seedVenueDetail). */}
            {!!(v.liveInfo?.useTime || v.operatingHours) && (
              <InfoRow label={t('venue.info.hours')} value={(v.liveInfo?.useTime || v.operatingHours) as string} />
            )}
            {!!v.liveInfo?.restDate && <InfoRow label={t('venue.info.closed')} value={v.liveInfo.restDate} />}
            {!!(v.liveInfo?.infoCenter || v.phone) && (
              <InfoRow label={t('venue.info.contact')} value={(v.liveInfo?.infoCenter || v.phone) as string} />
            )}
            {!!v.liveInfo?.parking && <InfoRow label={t('venue.info.parking')} value={v.liveInfo.parking} />}
            {!!(v.liveInfo?.homepage || v.homepageUrl) && (
              <InfoRow label={t('venue.info.website')} value={(v.liveInfo?.homepage || v.homepageUrl) as string} />
            )}
          </View>
          {/* 공공데이터 출처 표기 — 기관명 기준(API 서비스명 단독 표기 불가) */}
          {(v.source === 'KOPIS' || v.source === 'TOURAPI') && (
            <Text style={styles.source}>
              {t('venue.source', { name: t(v.source === 'KOPIS' ? 'venue.source.kopis' : 'venue.source.tourapi') })}
            </Text>
          )}

          {/* 이 장소의 행사 — 서버가 끝난 건 빼고 준다(진행 중·예정만). */}
          {!!perfs.data?.length && (
            <View style={styles.section}>
              <Text style={styles.sectionTitle}>{t('venue.perfs')}</Text>
              <PerformanceCarousel items={perfs.data} caption={stateCaption} />
            </View>
          )}

          {/* 주변에서 열리는 행사 — 위 섹션에 이미 나온 건 뺀다. */}
          {(() => {
            const shown = new Set((perfs.data ?? []).map((p) => p.id));
            const around = (nearbyPerfs.data ?? []).filter((p) => !shown.has(p.id));
            if (!around.length) return null;
            return (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>{t('venue.perfsNearby')}</Text>
                <PerformanceCarousel items={around} caption={stateCaption} />
              </View>
            );
          })()}

          <ContentComments targetType="VENUE" targetId={vid} targetName={v.name} />
        </View>
      </ScrollView>

      <ReportDialog
        visible={reporting}
        title={t('report.venueTitle')}
        pending={reportVenue.isPending}
        onSelect={submitReport}
        onClose={() => setReporting(false)}
      />
    </SafeAreaView>
  );
}

/**
 * 목록을 거치지 않고 들어왔을 때(딥링크, 앱 재시작 직후) 그리는 밑그림.
 *
 * 뒤로가기만 살려 둔다. 하트·신고는 어떤 장소인지 모르는 상태라 누를 수 없고,
 * 비활성 아이콘을 띄우면 눌러 보고 아무 일도 안 일어나는 쪽이 더 나쁘다.
 */
function VenueDetailSkeleton({ onBack }: { onBack: () => void }) {
  const t = useT();
  const insets = useSafeAreaInsets();

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      <View style={styles.topBar}>
        <Pressable hitSlop={8} onPress={onBack}>
          <Ionicons name="chevron-back" size={24} color={colors.text} />
        </Pressable>
      </View>
      <ScrollView contentContainerStyle={{ paddingBottom: 32 + insets.bottom }} showsVerticalScrollIndicator={false}>
        {/* 높이가 실제 사진과 같아서 사진이 와도 화면이 밀리지 않는다. */}
        <View style={styles.hero} />
        <View style={styles.body}>
          <Skeleton width="62%" height={22} radius={7} />
          <View style={{ marginTop: 10 }}><Skeleton width="80%" height={13} /></View>
          <View style={{ marginTop: 12 }}><Skeleton width="34%" height={13} /></View>
          <View style={{ marginTop: 16 }}><SkeletonText lines={5} /></View>
          <View style={styles.info}>
            {(['hours', 'closed', 'contact', 'website'] as const).map((k) => (
              <View key={k} style={styles.infoRow}>
                <Text style={styles.infoLabel}>{t(`venue.info.${k}`)}</Text>
                <Skeleton width="40%" height={14} />
              </View>
            ))}
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
  empty: { textAlign: 'center', color: colors.textFaint, fontSize: 14 },
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
