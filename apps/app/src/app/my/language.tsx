import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { Pressable, ScrollView, StyleSheet, Text } from 'react-native';
import { LOCALES, LOCALE_NAMES, useT } from '@/lib/i18n';
import { useLocaleStore } from '@/lib/store/localeStore';
import { colors, fonts, radius, space } from '@/lib/theme';

/**
 * 언어 바꾸기.
 *
 * 줄마다 그 언어의 자기 이름을 쓴다 — 지금 언어를 못 읽는 사람이 찾아오는 화면이라
 * 번역된 이름("일본어")으로 적으면 정작 일본인이 못 찾는다.
 */
export default function LanguageSettingsScreen() {
  const t = useT();
  const qc = useQueryClient();
  const locale = useLocaleStore((s) => s.locale);
  const setLocale = useLocaleStore((s) => s.setLocale);

  const pick = async (l: typeof locale) => {
    if (l === locale) return;
    await setLocale(l);
    // 받아 둔 응답은 이전 언어다. 비워야 장소 이름이 새 언어로 다시 온다.
    await qc.invalidateQueries();
  };

  return (
    <ScrollView contentContainerStyle={styles.wrap}>
      <Text style={styles.hint}>{t('language.hint')}</Text>
      {LOCALES.map((l) => (
        <Pressable key={l} style={styles.row} onPress={() => pick(l)}>
          <Text style={styles.name}>{LOCALE_NAMES[l]}</Text>
          {l === locale && <Ionicons name="checkmark" size={18} color={colors.primary} />}
        </Pressable>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: { padding: space.lg, gap: 2 },
  hint: { fontFamily: fonts.regular, fontSize: 12, color: colors.textFaint, marginBottom: space.sm },
  row: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    backgroundColor: colors.bgCard, borderRadius: radius.md, paddingHorizontal: 16, paddingVertical: 14,
  },
  name: { fontFamily: fonts.medium, fontSize: 15, color: colors.text },
});
