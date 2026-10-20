import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import LoginRequired from '@/components/LoginRequired';
import { useCreateReviewMutation } from '@/lib/hooks/queries';
import { useT } from '@/lib/i18n';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, radius, space } from '@/lib/theme';

export default function ReviewWriteScreen() {
  const router = useRouter();
  const t = useT();
  const insets = useSafeAreaInsets();
  const { targetType = 'VENUE', targetId, targetName } = useLocalSearchParams<{ targetType: string; targetId: string; targetName: string }>();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const create = useCreateReviewMutation();
  const [rating, setRating] = useState(5);
  const [content, setContent] = useState('');

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: t('review.title') }} />
        <LoginRequired description={t('review.loginDesc')} />
      </View>
    );
  }

  const submit = () => {
    create.mutate(
      { targetType: String(targetType), targetId: Number(targetId), rating, content },
      { onSuccess: () => router.back(), onError: (e: any) => Alert.alert(t('review.failTitle'), e?.message ?? t('common.error.unknown')) },
    );
  };

  return (
    <View style={[styles.safe, { paddingBottom: space.lg + insets.bottom }]}>
      <Stack.Screen options={{ title: t('review.title') }} />
      {!!targetName && <Text style={styles.target}>{targetName}</Text>}

      <Text style={styles.label}>{t('review.rating')}</Text>
      <View style={styles.stars}>
        {[1, 2, 3, 4, 5].map((n) => (
          <Pressable key={n} onPress={() => setRating(n)} hitSlop={6}>
            <Ionicons name={n <= rating ? 'star' : 'star-outline'} size={36} color={colors.star} />
          </Pressable>
        ))}
      </View>

      <Text style={styles.label}>{t('review.content')}</Text>
      <TextInput
        style={styles.input}
        value={content}
        onChangeText={setContent}
        placeholder={t('review.placeholder')}
        placeholderTextColor={colors.textFaint}
        multiline
      />

      <Pressable style={[styles.submit, create.isPending && styles.disabled]} disabled={create.isPending} onPress={submit}>
        <Text style={styles.submitText}>{create.isPending ? t('review.submitting') : t('review.submit')}</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg, padding: space.lg },
  target: { fontSize: 18, fontWeight: '800', color: colors.text, marginBottom: 16 },
  label: { fontSize: 14, fontWeight: '700', color: colors.text, marginTop: 8, marginBottom: 8 },
  stars: { flexDirection: 'row', gap: 6, marginBottom: 8 },
  input: { backgroundColor: colors.bgSoft, borderRadius: radius.md, padding: 14, height: 120, textAlignVertical: 'top', fontSize: 14, color: colors.text },
  submit: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16, alignItems: 'center', marginTop: 20 },
  disabled: { backgroundColor: colors.textFaint },
  submitText: { color: colors.white, fontSize: 15, fontWeight: '800' },
});
