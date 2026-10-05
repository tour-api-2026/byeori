import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { ReportDialog } from '@/components/ReportDialog';
import {
  useBlockUserMutation, useContentTagsQuery, useReportReviewMutation,
  useReviewsQuery, useVoteTagMutation,
} from '@/lib/hooks/queries';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius } from '@/lib/theme';

/**
 * 방문자 코멘트 — 태그 투표 + 리뷰 목록 + 신고·차단.
 *
 * 장소와 행사가 같은 화면을 쓴다. 서버 쪽은 처음부터 targetType 으로 갈라져 있어서
 * (리뷰·태그·위시리스트 모두) 화면만 한 벌로 모으면 된다. 두 화면에 복사해 두면
 * 한쪽만 고치는 사고가 나므로 여기 한 곳에서만 고치도록 뺐다.
 *
 * 신고·차단이 함께 있는 건 구글 UGC 정책 때문이다 — 사용자가 쓴 글을 보여주는 화면에는
 * 신고 수단이 있어야 한다. 행사·장소 자체의 신고는 이 컴포넌트 밖(상단 바)에 있다.
 * 행사는 공공데이터라 콘텐츠 신고 대상이 아니고, 코멘트만 신고 대상이다.
 */
export function ContentComments({
  targetType,
  targetId,
  targetName,
}: {
  targetType: 'VENUE' | 'PERFORMANCE';
  targetId: number;
  targetName: string;
}) {
  const router = useRouter();
  const tags = useContentTagsQuery(targetType, targetId);
  const reviews = useReviewsQuery(targetType, targetId);
  const { vote, unvote } = useVoteTagMutation(targetType, targetId);

  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const myId = useAuthStore((s) => s.user?.id);
  const reportReview = useReportReviewMutation();
  const blockUser = useBlockUserMutation();
  const [reportId, setReportId] = useState<number | null>(null);

  const needLogin = (what: string) => {
    Alert.alert('로그인 필요', `${what}하려면 로그인이 필요해요.`, [
      { text: '취소', style: 'cancel' },
      { text: '로그인', onPress: () => router.push('/login') },
    ]);
  };

  const openReport = (reviewId: number) => {
    if (!isLoggedIn) return needLogin('신고');
    setReportId(reviewId);
  };

  const submitReport = (reason: string) => {
    if (reportId == null) return;
    reportReview.mutate(
      { id: reportId, reason },
      {
        onSuccess: () => {
          setReportId(null);
          Alert.alert('신고 접수', '신고가 접수되었어요. 운영진이 확인 후 조치합니다.');
        },
        onError: (e: any) => {
          setReportId(null);
          Alert.alert('신고 실패', e?.message ?? '잠시 후 다시 시도해주세요.');
        },
      },
    );
  };

  const confirmBlock = (targetUserId: number) => {
    if (!isLoggedIn) return needLogin('차단');
    Alert.alert('사용자 차단', '이 사용자의 리뷰가 더 이상 보이지 않아요. 차단은 마이 > 차단한 사용자에서 해제할 수 있어요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '차단',
        style: 'destructive',
        onPress: () =>
          blockUser.mutate(targetUserId, {
            onSuccess: () => Alert.alert('차단 완료', '이 사용자의 리뷰를 더 이상 표시하지 않아요.'),
            onError: (e: any) => Alert.alert('차단 실패', e?.message ?? '잠시 후 다시 시도해주세요.'),
          }),
      },
    ]);
  };

  const writeHref =
    `/review/write?targetType=${targetType}&targetId=${targetId}&targetName=${encodeURIComponent(targetName)}`;

  return (
    <View style={styles.section}>
      <View style={styles.commentHead}>
        <Text style={styles.sectionTitle}>방문자 코멘트</Text>
        <Pressable hitSlop={8} onPress={() => router.push(writeHref as never)}>
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

      <View style={{ marginTop: 14, gap: 10 }}>
        {reviews.data?.length ? reviews.data.map((r) => (
          <View key={r.id} style={styles.reviewCard}>
            <View style={styles.reviewTop}>
              <View style={styles.reviewAvatar} />
              <Text style={styles.reviewUser}>사용자{r.userId}</Text>
              {String(r.userId) !== String(myId ?? '') && (
                <View style={styles.reviewActions}>
                  <Pressable hitSlop={8} onPress={() => openReport(r.id)}>
                    <Ionicons name="flag-outline" size={15} color={colors.textFaint} />
                  </Pressable>
                  <Pressable hitSlop={8} onPress={() => confirmBlock(r.userId)}>
                    <Ionicons name="person-remove-outline" size={15} color={colors.textFaint} />
                  </Pressable>
                </View>
              )}
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

      <ReportDialog
        visible={reportId != null}
        title="리뷰 신고"
        pending={reportReview.isPending}
        onSelect={submitReport}
        onClose={() => setReportId(null)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  section: { marginTop: 26 },
  sectionTitle: { fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginBottom: 12 },
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
  reviewActions: { flexDirection: 'row', alignItems: 'center', gap: 14, marginLeft: 'auto' },
  reviewStars: { flexDirection: 'row', alignItems: 'center', gap: 1, marginTop: 8 },
  reviewContent: { fontSize: 13, color: colors.textSub, marginTop: 8, lineHeight: 19 },
  noReview: { fontSize: 13, color: colors.textFaint },
});
