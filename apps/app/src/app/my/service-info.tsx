import { Ionicons } from '@expo/vector-icons';
import Constants from 'expo-constants';
import * as Updates from 'expo-updates';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useT } from '@/lib/i18n';
import type { TFn } from '@/lib/i18n/labels';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

const PRIVACY_URL = 'https://byeori.seoulride.site/privacy';
const DELETION_URL = 'https://byeori.seoulride.site/account-deletion';
const CONTACT_EMAIL = 'alstjq1012@gmail.com';

function LinkRow({ icon, label, onPress }: { icon: string; label: string; onPress: () => void }) {
  return (
    <Pressable style={styles.row} onPress={onPress}>
      <Ionicons name={icon as any} size={18} color={colors.textSub} />
      <Text style={styles.rowLabel}>{label}</Text>
      <Ionicons name="chevron-forward" size={18} color={colors.textFaint} style={{ marginLeft: 'auto' }} />
    </Pressable>
  );
}

/**
 * 어느 번들로 돌고 있는지 알려주는 줄.
 *
 * 전에는 버전이 '1.0.0' 하나뿐이라, 7월 빌드든 최신 OTA든 화면이 똑같았다. 그래서
 * "앱이 웹과 다르다"를 푸는 데 한참 걸렸다 — 설치본을 특정할 방법이 없었다.
 *
 * versionCode 대신 runtimeVersion 을 쓴다. expo-application 은 직접 의존성이 아니라
 * 끌어 쓰면 지문이 흔들릴 수 있고, 무엇보다 runtimeVersion 이 더 쓸모 있다 —
 * OTA 가 그 설치본에 닿는지를 결정하는 값이 바로 이것이다.
 */
function buildLine(t: TFn): string | null {
  if (!Updates.isEnabled) return null;        // 개발 중에는 의미 없는 값이라 숨긴다
  const rt = Updates.runtimeVersion?.slice(0, 8);
  const src = Updates.isEmbeddedLaunch ? t('service.build.embedded') : 'OTA';
  const id = Updates.updateId?.slice(0, 8);
  const at = Updates.createdAt
    ? t('service.build.date', { m: Updates.createdAt.getMonth() + 1, d: Updates.createdAt.getDate() })
    : null;
  return [rt && t('service.build.runtime', { rt }), `${src}${id ? ` ${id}` : ''}`, at].filter(Boolean).join(' · ');
}

export default function ServiceInfoScreen() {
  const version = Constants.expoConfig?.version ?? '1.0.0';
  const t = useT();
  const build = buildLine(t);

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 40 }}>
        <View style={styles.hero}>
          <Text style={styles.appName}>{t('app.name')}</Text>
          <Text style={styles.version}>{t('service.version', { v: version })}</Text>
          {!!build && <Text style={styles.build}>{build}</Text>}
          <Text style={styles.tagline}>{t('service.tagline')}</Text>
        </View>

        <Text style={styles.sectionLabel}>{t('service.terms')}</Text>
        <View style={styles.card}>
          <LinkRow icon="shield-checkmark" label={t('service.privacy')} onPress={() => Linking.openURL(PRIVACY_URL)} />
          <View style={styles.divider} />
          <LinkRow icon="trash" label={t('service.deletion')} onPress={() => Linking.openURL(DELETION_URL)} />
        </View>

        <Text style={styles.sectionLabel}>{t('service.contact')}</Text>
        <View style={styles.card}>
          <LinkRow
            icon="mail"
            label={CONTACT_EMAIL}
            onPress={() => Linking.openURL(`mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(t('service.contactSubject'))}`)}
          />
        </View>

        <Text style={styles.sectionLabel}>{t('service.sources')}</Text>
        <View style={styles.card}>
          <View style={styles.sourceBox}>
            <Text style={styles.sourceText}>{t('service.src.tour')}</Text>
            <Text style={styles.sourceText}>{t('service.src.kopis')}</Text>
            <Text style={styles.sourceText}>{t('service.src.seoul')}</Text>
            <Text style={styles.sourceNote}>{t('service.sourceNote')}</Text>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  hero: { alignItems: 'center', paddingVertical: 24 },
  appName: { fontSize: 26, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, letterSpacing: 2 },
  version: { fontSize: 13, color: colors.textSub, marginTop: 6 },
  build: { fontSize: 11, color: colors.textFaint, marginTop: 3 },
  tagline: { fontSize: 13, color: colors.textFaint, marginTop: 10 },
  sectionLabel: { fontSize: 15, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 22, marginBottom: 10 },
  card: { backgroundColor: colors.bgCard, borderRadius: radius.lg, ...shadow.card, overflow: 'hidden' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 14, paddingVertical: 15 },
  rowLabel: { fontSize: 15, fontFamily: fonts.medium, fontWeight: '500', color: colors.text },
  divider: { height: 1, backgroundColor: colors.border, marginLeft: 44 },
  sourceBox: { padding: 16, gap: 6 },
  sourceText: { fontSize: 13, fontFamily: fonts.medium, color: colors.textSub },
  sourceNote: { fontSize: 12, color: colors.textFaint, lineHeight: 18, marginTop: 8 },
});
