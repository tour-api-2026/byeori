import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radius, space } from '@/lib/theme';

type Provider = { key: string; label: string; bg: string; fg: string; icon: string; border?: boolean };
const PROVIDERS: Provider[] = [
  { key: 'kakao', label: '카카오로 시작하기', bg: '#FEE500', fg: '#191600', icon: 'chatbubble' },
  { key: 'naver', label: '네이버로 시작하기', bg: '#03C75A', fg: '#FFFFFF', icon: 'logo-react' },
  { key: 'google', label: 'Google로 시작하기', bg: '#FFFFFF', fg: '#1A1A1E', icon: 'logo-google', border: true },
];

export default function LoginScreen() {
  const router = useRouter();
  return (
    <View style={styles.safe}>
      <View style={styles.hero}>
        <Text style={styles.logo}>벼리</Text>
        <Text style={styles.slogan}>한국 전통, 하나의 실로 엮다</Text>
      </View>
      <View style={styles.buttons}>
        {PROVIDERS.map((p) => (
          <Pressable
            key={p.key}
            style={[styles.btn, { backgroundColor: p.bg }, p.border && styles.btnBorder]}
            onPress={() => router.replace('/onboarding')}>
            <Ionicons name={p.icon as any} size={18} color={p.fg} />
            <Text style={[styles.btnText, { color: p.fg }]}>{p.label}</Text>
          </Pressable>
        ))}
        <Pressable onPress={() => router.replace('/(tabs)')}>
          <Text style={styles.skip}>둘러보기</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg, justifyContent: 'space-between', paddingBottom: 48 },
  hero: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  logo: { fontSize: 48, fontWeight: '900', color: colors.primary, letterSpacing: -1 },
  slogan: { fontSize: 14, color: colors.textFaint, marginTop: 10 },
  buttons: { paddingHorizontal: space.lg, gap: 10 },
  btn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: radius.md, paddingVertical: 15 },
  btnBorder: { borderWidth: 1, borderColor: colors.border },
  btnText: { fontSize: 15, fontWeight: '700' },
  skip: { textAlign: 'center', color: colors.textFaint, fontSize: 14, marginTop: 8, paddingVertical: 8 },
});
