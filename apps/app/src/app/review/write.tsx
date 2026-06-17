import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useCreateReviewMutation } from '@/lib/hooks/queries';
import { colors, radius, space } from '@/lib/theme';

export default function ReviewWriteScreen() {
  const router = useRouter();
  const { targetType = 'VENUE', targetId, targetName } = useLocalSearchParams<{ targetType: string; targetId: string; targetName: string }>();
  const create = useCreateReviewMutation();
  const [rating, setRating] = useState(5);
  const [content, setContent] = useState('');

  const submit = () => {
    create.mutate(
      { targetType: String(targetType), targetId: Number(targetId), rating, content },
      { onSuccess: () => router.back(), onError: (e: any) => Alert.alert('실패', e?.message ?? '오류') },
    );
  };

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '리뷰 작성' }} />
      {!!targetName && <Text style={styles.target}>{targetName}</Text>}

      <Text style={styles.label}>별점</Text>
      <View style={styles.stars}>
        {[1, 2, 3, 4, 5].map((n) => (
          <Pressable key={n} onPress={() => setRating(n)} hitSlop={6}>
            <Ionicons name={n <= rating ? 'star' : 'star-outline'} size={36} color={colors.star} />
          </Pressable>
        ))}
      </View>

      <Text style={styles.label}>내용</Text>
      <TextInput
        style={styles.input}
        value={content}
        onChangeText={setContent}
        placeholder="방문 경험을 남겨주세요"
        placeholderTextColor={colors.textFaint}
        multiline
      />

      <Pressable style={[styles.submit, create.isPending && styles.disabled]} disabled={create.isPending} onPress={submit}>
        <Text style={styles.submitText}>{create.isPending ? '등록 중...' : '리뷰 등록'}</Text>
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
