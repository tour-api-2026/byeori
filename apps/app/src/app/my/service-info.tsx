import { Ionicons } from '@expo/vector-icons';
import Constants from 'expo-constants';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
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

export default function ServiceInfoScreen() {
  const version = Constants.expoConfig?.version ?? '1.0.0';

  return (
    <SafeAreaView style={styles.safe} edges={['bottom']}>
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 40 }}>
        <View style={styles.hero}>
          <Text style={styles.appName}>벼리</Text>
          <Text style={styles.version}>버전 {version}</Text>
          <Text style={styles.tagline}>전통이 살아있는 여행을 잇다</Text>
        </View>

        <Text style={styles.sectionLabel}>약관 및 정책</Text>
        <View style={styles.card}>
          <LinkRow icon="shield-checkmark" label="개인정보처리방침" onPress={() => Linking.openURL(PRIVACY_URL)} />
          <View style={styles.divider} />
          <LinkRow icon="trash" label="계정 삭제 안내" onPress={() => Linking.openURL(DELETION_URL)} />
        </View>

        <Text style={styles.sectionLabel}>문의</Text>
        <View style={styles.card}>
          <LinkRow
            icon="mail"
            label={CONTACT_EMAIL}
            onPress={() => Linking.openURL(`mailto:${CONTACT_EMAIL}?subject=[벼리] 문의`)}
          />
        </View>

        <Text style={styles.sectionLabel}>데이터 출처</Text>
        <View style={styles.card}>
          <View style={styles.sourceBox}>
            <Text style={styles.sourceText}>출처: ⓒ한국관광공사</Text>
            <Text style={styles.sourceText}>출처: ⓒ공연예술통합전산망(KOPIS)</Text>
            <Text style={styles.sourceText}>출처: ⓒ서울특별시 서울열린데이터광장</Text>
            <Text style={styles.sourceNote}>
              본 서비스는 위 기관이 제공하는 공공데이터를 활용해 제작되었으며, 해당 기관이 직접 운영하는
              서비스가 아닙니다.
            </Text>
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
