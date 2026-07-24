import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts, radius, space } from '@/lib/theme';

const TOPICS = ['관람', '체험', '공연', '한복', '음식', '공예'];
const REGIONS = ['서울', '경기', '부산', '대전', '대구', '제주', '전주', '여수', '인천', '광주'];

export default function OnboardingScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [step, setStep] = useState(0); // 0: 주제, 1: 지역
  const [topics, setTopics] = useState<string[]>([]);
  const [regions, setRegions] = useState<string[]>([]);

  const toggle = (arr: string[], set: (v: string[]) => void, v: string) =>
    set(arr.includes(v) ? arr.filter((x) => x !== v) : [...arr, v]);

  const isTopic = step === 0;
  const items = isTopic ? TOPICS : REGIONS;
  const selected = isTopic ? topics : regions;
  const onToggle = (v: string) => (isTopic ? toggle(topics, setTopics, v) : toggle(regions, setRegions, v));

  const next = () => (isTopic ? setStep(1) : router.replace('/(tabs)'));

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

      <Text style={styles.title}>관심있는 {isTopic ? '주제' : '지역'}를 선택해주세요!</Text>
      <Text style={styles.sub}>나의 관심 {isTopic ? '주제' : '지역'}을 선택하면{'\n'}나에게 맞는 내용들을 추천해드릴게요!</Text>

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
        <Pressable style={styles.cta} onPress={next}>
          <Text style={styles.ctaText}>{isTopic ? '다음' : '시작하기'}</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.white} />
        </Pressable>
        {isTopic && (
          <Pressable hitSlop={8} onPress={() => setStep(1)}>
            <Text style={styles.skip}>건너뛰기 ›</Text>
          </Pressable>
        )}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
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
