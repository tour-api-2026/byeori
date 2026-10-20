import { Ionicons } from '@expo/vector-icons';
import { useRouter } from 'expo-router';
import { useRef, useState } from 'react';
import {
  ActivityIndicator, Alert, KeyboardAvoidingView, Platform, Pressable,
  StyleSheet, Text, TextInput, View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { loginAdmin } from '@/lib/auth/oauth';
import { useT } from '@/lib/i18n';
import { colors, fonts, radius, space } from '@/lib/theme';

export default function EmailLoginScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const t = useT();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [busy, setBusy] = useState(false);
  const pwRef = useRef<TextInput>(null);

  const submit = async () => {
    if (busy) return;
    if (!id.trim() || !pw) {
      Alert.alert(t('login.email.checkTitle'), t('login.email.checkBody'));
      return;
    }
    setBusy(true);
    try {
      await loginAdmin(id, pw);
      router.replace('/(tabs)');
    } catch (e: any) {
      Alert.alert(t('login.failTitle'), e?.message ?? t('login.email.wrong'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.safe}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      {/* 헤더 */}
      <View style={[styles.header, { paddingTop: insets.top + 8 }]}>
        <Pressable hitSlop={10} onPress={() => router.back()}>
          <Ionicons name="chevron-back" size={26} color={colors.text} />
        </Pressable>
        <Text style={styles.headerTitle}>{t('login.email.title')}</Text>
        <View style={{ width: 26 }} />
      </View>

      <View style={styles.body}>
        <Text style={styles.label}>{t('login.email.id')}</Text>
        <TextInput
          style={styles.input}
          value={id}
          onChangeText={setId}
          placeholder={t('login.email.idPlaceholder')}
          placeholderTextColor={colors.textFaint}
          autoCapitalize="none"
          autoCorrect={false}
          autoFocus
          editable={!busy}
          returnKeyType="next"
          onSubmitEditing={() => pwRef.current?.focus()}
        />

        <Text style={[styles.label, { marginTop: 16 }]}>{t('login.email.pw')}</Text>
        <TextInput
          ref={pwRef}
          style={styles.input}
          value={pw}
          onChangeText={setPw}
          placeholder={t('login.email.pwPlaceholder')}
          placeholderTextColor={colors.textFaint}
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
          editable={!busy}
          returnKeyType="go"
          onSubmitEditing={submit}
        />

        <Pressable
          style={[styles.btn, busy && styles.btnDisabled]}
          disabled={busy}
          onPress={submit}>
          {busy
            ? <ActivityIndicator color={colors.white} />
            : <Text style={styles.btnText}>{t('login.email.submit')}</Text>}
        </Pressable>
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  header: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    paddingHorizontal: space.lg, paddingBottom: 12,
  },
  headerTitle: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  body: { paddingHorizontal: space.lg, paddingTop: 16 },
  label: { fontSize: 13, fontFamily: fonts.semibold, fontWeight: '600', color: colors.text, marginBottom: 8 },
  input: {
    borderWidth: 1, borderColor: colors.border, borderRadius: radius.md,
    paddingHorizontal: 14, paddingVertical: 13, minHeight: 50,
    fontSize: 15, fontFamily: fonts.medium, color: colors.text, backgroundColor: colors.white,
  },
  btn: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
    borderRadius: radius.md, paddingVertical: 16, minHeight: 54, marginTop: 28,
    backgroundColor: colors.primary,
  },
  btnDisabled: { opacity: 0.6 },
  btnText: { fontSize: 15, fontFamily: fonts.semibold, fontWeight: '600', color: colors.white },
});
