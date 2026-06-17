import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { isCancelled, loginGoogle, loginKakao } from '@/lib/auth/oauth';
import { colors, fonts, radius, space } from '@/lib/theme';

// OAuth 리다이렉트 후 브라우저 세션이 깔끔히 닫히도록 보장
WebBrowser.maybeCompleteAuthSession();

type Provider = 'kakao' | 'google' | null;

export default function LoginScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [busy, setBusy] = useState<Provider>(null);

  const run = async (provider: Exclude<Provider, null>, fn: () => Promise<unknown>) => {
    if (busy) return;
    setBusy(provider);
    try {
      await fn();
      router.replace('/(tabs)');
    } catch (e: any) {
      if (!isCancelled(e)) {
        Alert.alert('로그인 실패', e?.message ?? '잠시 후 다시 시도해주세요.');
      }
    } finally {
      setBusy(null);
    }
  };

  return (
    <View style={[styles.safe, { paddingTop: insets.top + 20, paddingBottom: 32 + insets.bottom }]}>
      {/* 로고 */}
      <View style={styles.hero}>
        <View style={styles.logoBox}>
          <Image source={require('../../assets/images/logo-hat.png')} style={styles.logoImg} contentFit="contain" />
        </View>
        <Text style={styles.brand}>벼리</Text>
        <Text style={styles.brandSub}>한국 전통의 멋, 벼리에서</Text>
      </View>

      {/* 로그인 */}
      <View style={styles.bottom}>
        <Text style={styles.heading}>로그인</Text>

        <Pressable
          style={[styles.btn, { backgroundColor: colors.kakao }, !!busy && styles.btnDisabled]}
          disabled={!!busy}
          onPress={() => run('kakao', loginKakao)}>
          {busy === 'kakao' ? (
            <ActivityIndicator color={colors.kakaoText} />
          ) : (
            <>
              <Ionicons name="chatbubble" size={18} color={colors.kakaoText} />
              <Text style={[styles.btnText, { color: colors.kakaoText }]}>Kakao로 시작하기</Text>
            </>
          )}
        </Pressable>

        <Pressable
          style={[styles.btn, styles.btnBorder, { backgroundColor: colors.white }, !!busy && styles.btnDisabled]}
          disabled={!!busy}
          onPress={() => run('google', loginGoogle)}>
          {busy === 'google' ? (
            <ActivityIndicator color={colors.text} />
          ) : (
            <>
              <Ionicons name="logo-google" size={18} color="#EA4335" />
              <Text style={[styles.btnText, { color: colors.text }]}>Google로 시작하기</Text>
            </>
          )}
        </Pressable>

        <Pressable disabled={!!busy} onPress={() => router.replace('/(tabs)')}>
          <Text style={styles.skip}>둘러보기</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg, justifyContent: 'space-between' },
  hero: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 4 },
  logoBox: { width: 160, height: 160, borderRadius: 36, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center', ...shadowLogo() },
  logoImg: { width: 132, height: 132 },
  brand: { fontSize: 30, fontFamily: fonts.bold, fontWeight: '800', color: colors.primary, letterSpacing: 2, marginTop: 18 },
  brandSub: { fontSize: 13, fontFamily: fonts.medium, fontWeight: '500', color: colors.textFaint, marginTop: 2 },
  bottom: { paddingHorizontal: space.lg, gap: 10 },
  heading: { textAlign: 'center', fontSize: 18, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginBottom: 8 },
  btn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, borderRadius: radius.md, paddingVertical: 16, minHeight: 54 },
  btnBorder: { borderWidth: 1, borderColor: colors.border },
  btnDisabled: { opacity: 0.6 },
  btnText: { fontSize: 15, fontFamily: fonts.semibold, fontWeight: '600' },
  skip: { textAlign: 'center', color: colors.textFaint, fontSize: 14, marginTop: 8, paddingVertical: 8 },
});

function shadowLogo() {
  return { boxShadow: '0px 8px 24px rgba(31,38,76,0.10)', elevation: 3 } as const;
}
