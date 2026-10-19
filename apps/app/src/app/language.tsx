import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LOCALES, LOCALE_NAMES, type Locale } from '@/lib/i18n';
import { useLocaleStore } from '@/lib/store/localeStore';
import { colors, fonts, radius, space } from '@/lib/theme';

/** 각 언어를 대표하는 깃발. 국가가 아니라 언어 표시라 중국어는 간체 기준이다. */
const FLAG: Record<Locale, string> = { ko: '🇰🇷', en: '🇺🇸', ja: '🇯🇵', zh: '🇨🇳' };

/**
 * 첫 실행 때 언어를 고른다.
 *
 * 기기 언어로 미리 골라 둔 채로 연다 — 맞으면 한 번 누르고 지나가고, 아니면 바꾼다.
 * 기기 언어만 쓰면 영어폰을 쓰는 일본인처럼 어긋나는 사람이 길을 잃고(설정이 한국어로
 * 보이면 바꾸는 곳을 찾지도 못한다), 카드만 쓰면 대다수에게 불필요한 한 단계다.
 *
 * 이 화면은 번역하지 않는다. 언어를 아직 모르는 사람이 보는 화면이라 useT() 를 쓰지 않고,
 * 각 언어의 자기 이름(LOCALE_NAMES)과 영어 단어만 쓴다.
 */
export default function LanguageScreen() {
  const qc = useQueryClient();
  const current = useLocaleStore((s) => s.locale);
  const setLocale = useLocaleStore((s) => s.setLocale);
  const [picked, setPicked] = useState<Locale>(current);

  const go = async () => {
    await setLocale(picked);
    // 서버에서 받아 둔 응답은 이전 언어다. 비워야 다음 화면이 고른 언어로 다시 받는다.
    // 지금은 장소 이름이 모든 언어에서 한국어라 눈에 띄는 변화가 없지만, 다음 계획에서
    // 콘텐츠를 번역하면 이 줄이 빠졌을 때 "언어를 바꿨는데 장소 이름이 그대로"라는
    // 증상이 나오고 원인을 여기서 찾기 어렵다. 눈에 안 보인다고 지우지 말 것.
    await qc.invalidateQueries();
    // 이동하지 않는다. setLocale 이 chosen 을 켜면 루트 레이아웃이 이 화면을 내리고 메인 Stack 을
    // 올리는데, Stack 은 홈 라우트로 알아서 열린다. 여기서 replace 하면 이미 내려간 화면에서
    // 이동하는 꼴이라 네이티브에서 경고가 날 수 있다.
  };

  return (
    <SafeAreaView style={styles.safe}>
      <Text style={styles.title}>벼리</Text>
      <Text style={styles.sub}>Select your language</Text>
      <View style={styles.grid}>
        {LOCALES.map((l) => (
          <Pressable
            key={l}
            style={[styles.card, picked === l && styles.cardOn]}
            accessibilityRole="button"
            accessibilityLabel={LOCALE_NAMES[l]}
            accessibilityState={{ selected: picked === l }}
            onPress={() => setPicked(l)}
          >
            <Text style={styles.flag}>{FLAG[l]}</Text>
            <Text style={[styles.name, picked === l && styles.nameOn]}>{LOCALE_NAMES[l]}</Text>
          </Pressable>
        ))}
      </View>
      <Pressable style={styles.next} onPress={go}>
        <Text style={styles.nextText}>Continue</Text>
        <Ionicons name="chevron-forward" size={16} color={colors.white} />
      </Pressable>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg, padding: space.lg, justifyContent: 'center' },
  title: { fontFamily: fonts.bold, fontSize: 28, color: colors.primary, textAlign: 'center' },
  sub: { fontFamily: fonts.medium, fontSize: 14, color: colors.textSub, textAlign: 'center', marginTop: 6, marginBottom: space.xl },
  grid: { flexDirection: 'row', flexWrap: 'wrap', gap: space.md, justifyContent: 'center' },
  card: {
    width: '45%', aspectRatio: 1.3, borderRadius: radius.lg, backgroundColor: colors.bgCard,
    borderWidth: 2, borderColor: colors.border, alignItems: 'center', justifyContent: 'center', gap: 8,
  },
  cardOn: { borderColor: colors.primary, backgroundColor: colors.primarySoft },
  flag: { fontSize: 34 },
  name: { fontFamily: fonts.bold, fontSize: 15, color: colors.text },
  nameOn: { color: colors.primary },
  next: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 4,
    marginTop: space.xl, paddingVertical: 14, borderRadius: radius.pill, backgroundColor: colors.primary,
  },
  nextText: { fontFamily: fonts.bold, fontSize: 15, color: colors.white },
});
