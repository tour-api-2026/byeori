import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts, radius, space } from '@/lib/theme';

export default function LoginScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.safe, { paddingTop: insets.top + 20, paddingBottom: 32 + insets.bottom }]}>
      {/* 로고 */}
      <View style={styles.hero}>
        <View style={styles.logoBox}><Text style={styles.logoText}>벼리</Text></View>
      </View>

      {/* 로그인 */}
      <View style={styles.bottom}>
        <Text style={styles.heading}>로그인</Text>

        <Pressable style={[styles.btn, { backgroundColor: colors.kakao }]} onPress={() => router.replace('/onboarding')}>
          <Ionicons name="chatbubble" size={18} color={colors.kakaoText} />
          <Text style={[styles.btnText, { color: colors.kakaoText }]}>Kakao로 시작하기</Text>
        </Pressable>

        <Pressable style={[styles.btn, styles.btnBorder, { backgroundColor: colors.white }]} onPress={() => router.replace('/onboarding')}>
          <Ionicons name="logo-google" size={18} color="#EA4335" />
          <Text style={[styles.btnText, { color: colors.text }]}>Google로 시작하기</Text>
        </Pressable>

        <Pressable onPress={() => router.replace('/(tabs)')}>
          <Text style={styles.skip}>둘러보기</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg, justifyContent: 'space-between' },
  hero: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  logoBox: { width: 160, height: 160, borderRadius: 28, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center', ...shadowLogo() },
  logoText: { fontSize: 40, fontFamily: fonts.bold, fontWeight: '800', color: colors.primary, letterSpacing: 1 },
  bottom: { paddingHorizontal: space.lg, gap: 10 },
  heading: { textAlign: 'center', fontSize: 18, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginBottom: 8 },
  btn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: radius.md, paddingVertical: 16 },
  btnBorder: { borderWidth: 1, borderColor: colors.border },
  btnText: { fontSize: 15, fontFamily: fonts.semibold, fontWeight: '600' },
  skip: { textAlign: 'center', color: colors.textFaint, fontSize: 14, marginTop: 8, paddingVertical: 8 },
});

function shadowLogo() {
  return { boxShadow: '0px 8px 24px rgba(31,38,76,0.10)', elevation: 3 } as const;
}
