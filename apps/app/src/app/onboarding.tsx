import { Ionicons } from '@expo/vector-icons';
import { useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { useMyInterestsQuery, useSaveInterestsMutation } from '@/lib/hooks/queries';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, space } from '@/lib/theme';

const TOPICS = ['관람', '체험', '공연', '한복', '음식', '공예'];
const REGIONS = ['서울', '경기', '부산', '대전', '대구', '제주', '전주', '여수', '인천', '광주'];

/**
 * 관심 주제·지역 고르기.
 *
 * 두 가지 자리에서 쓴다 — 로그인 직후 처음 한 번, 그리고 마이에서 다시 고칠 때.
 * `edit=1` 로 들어오면 고치는 중이라 저장 뒤 뒤로 가고, 아니면 홈으로 보낸다.
 *
 * 전에는 고른 값을 서버로 보내지 않고 버렸다. 게다가 이 화면으로 오는 길이 아예 없어
 * 주소를 직접 쳐야만 보였다.
 */
export default function OnboardingScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { edit } = useLocalSearchParams<{ edit?: string }>();
  const editing = edit === '1';
  const signedIn = useAuthStore((s) => !!s.accessToken);
  const { data: saved, isLoading } = useMyInterestsQuery(signedIn);
  const save = useSaveInterestsMutation();

  const [step, setStep] = useState(0); // 0: 주제, 1: 지역
  const [topics, setTopics] = useState<string[]>([]);
  const [regions, setRegions] = useState<string[]>([]);

  // 전에 고른 것이 있으면 켜 둔 채로 연다. 고치러 들어왔는데 빈 화면이면 다시 다 골라야 한다.
  useEffect(() => {
    if (!saved) return;
    setTopics(saved.topics);
    setRegions(saved.regions);
  }, [saved]);

  const toggle = (arr: string[], set: (v: string[]) => void, v: string) =>
    set(arr.includes(v) ? arr.filter((x) => x !== v) : [...arr, v]);

  const isTopic = step === 0;
  // '주제를' / '지역을' — 받침에 따라 조사가 갈린다. 붙여 쓰면 한쪽이 늘 틀린다.
  const whatObj = isTopic ? '주제를' : '지역을';
  const items = isTopic ? TOPICS : REGIONS;
  const selected = isTopic ? topics : regions;
  const onToggle = (v: string) => (isTopic ? toggle(topics, setTopics, v) : toggle(regions, setRegions, v));

  const leave = () => (editing ? router.back() : router.replace('/(tabs)'));

  /**
   * 마지막 단계에서 저장하고 나간다.
   *
   * 비로그인이면 보낼 곳이 없으므로 그냥 나간다 — 고른 값을 기기에 담아 두었다가 나중에
   * 올리는 길도 있지만, 로그인 뒤에만 이 화면이 열리므로 지금은 생길 일이 아니다.
   * 저장이 실패해도 막지 않는다. 관심사 때문에 앱을 못 들어가면 안 된다.
   */
  const next = () => {
    if (isTopic) { setStep(1); return; }
    if (!signedIn) { leave(); return; }
    save.mutate({ topics, regions }, {
      onSuccess: leave,
      onError: (e: any) => Alert.alert('관심사를 저장하지 못했어요', e?.message ?? '잠시 후 다시 시도해 주세요.', [
        { text: '다시 시도' }, { text: '그냥 시작', onPress: leave },
      ]),
    });
  };

  if (signedIn && isLoading) {
    return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;
  }

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* 상단: 이전 + 페이지 점 */}
      <View style={styles.top}>
        {step === 1 ? (
          <Pressable style={styles.back} hitSlop={8} onPress={() => setStep(0)}>
            <Ionicons name="chevron-back" size={20} color={colors.text} />
            <Text style={styles.backText}>이전으로</Text>
          </Pressable>
        ) : <View style={{ width: 80 }} />}
        <View style={styles.dots}>
          <View style={[styles.dot, step === 0 && styles.dotActive]} />
          <View style={[styles.dot, step === 1 && styles.dotActive]} />
        </View>
        <View style={{ width: 80 }} />
      </View>

      <Text style={styles.title}>관심있는 {whatObj} 선택해주세요!</Text>
      <Text style={styles.sub}>나의 관심 {whatObj} 선택하면{'\n'}나에게 맞는 내용들을 추천해드릴게요!</Text>

      <ScrollView contentContainerStyle={styles.gridWrap} showsVerticalScrollIndicator={false}>
        <View style={styles.grid}>
          {items.map((v) => {
            const on = selected.includes(v);
            return (
              <Pressable
                key={v}
                style={[isTopic ? styles.topicCard : styles.regionCard, on && styles.cardOn]}
                onPress={() => onToggle(v)}>
                <Text style={[styles.cardText, on && styles.cardTextOn]}>{v}</Text>
              </Pressable>
            );
          })}
        </View>
      </ScrollView>

      <View style={[styles.bottom, { paddingBottom: 16 + insets.bottom }]}>
        <Pressable style={[styles.cta, save.isPending && { opacity: 0.6 }]} disabled={save.isPending} onPress={next}>
          {save.isPending
            ? <ActivityIndicator color={colors.white} />
            : <>
                <Text style={styles.ctaText}>{isTopic ? '다음' : editing ? '저장' : '시작하기'}</Text>
                <Ionicons name="chevron-forward" size={16} color={colors.white} />
              </>}
        </Pressable>
        {isTopic ? (
          <Pressable hitSlop={8} onPress={() => setStep(1)}>
            <Text style={styles.skip}>건너뛰기 ›</Text>
          </Pressable>
        ) : !editing && (
          <Pressable hitSlop={8} disabled={save.isPending} onPress={leave}>
            <Text style={styles.skip}>건너뛰기 ›</Text>
          </Pressable>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  top: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: space.lg, paddingTop: 8, paddingBottom: 16 },
  back: { flexDirection: 'row', alignItems: 'center', gap: 2, width: 80 },
  backText: { fontSize: 14, color: colors.text, fontFamily: fonts.medium, fontWeight: '500' },
  dots: { flexDirection: 'row', gap: 6 },
  dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: colors.border },
  dotActive: { width: 22, backgroundColor: colors.primary },
  title: { fontSize: 22, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, textAlign: 'center', marginTop: 8 },
  sub: { fontSize: 13, color: colors.textFaint, textAlign: 'center', lineHeight: 20, marginTop: 10 },
  gridWrap: { padding: space.lg, paddingTop: 24 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 14 },
  topicCard: { width: '48%', height: 116, backgroundColor: colors.bgCard, borderRadius: radius.lg, alignItems: 'center', justifyContent: 'center', borderWidth: 1.5, borderColor: 'transparent' },
  regionCard: { width: '48%', height: 56, backgroundColor: colors.bgCard, borderRadius: radius.md, alignItems: 'center', justifyContent: 'center', borderWidth: 1.5, borderColor: 'transparent' },
  cardOn: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  cardText: { fontSize: 15, fontFamily: fonts.semibold, fontWeight: '600', color: colors.textSub },
  cardTextOn: { color: colors.primary },
  bottom: { paddingHorizontal: space.lg, paddingTop: 8, alignItems: 'center', gap: 12 },
  cta: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4, alignSelf: 'stretch', backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16 },
  ctaText: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
  skip: { color: colors.textFaint, fontSize: 14, paddingVertical: 4 },
});
