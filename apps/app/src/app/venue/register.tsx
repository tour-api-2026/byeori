import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Chip } from '@/components/Chip';
import { useCreateVenueMutation } from '@/lib/hooks/queries';
import { colors, radius, space } from '@/lib/theme';

const CATEGORIES = ['한복', '메이크업', '체험', '카페', '맛집', '음식점', '문화'];

export default function VenueRegisterScreen() {
  const router = useRouter();
  const create = useCreateVenueMutation();
  const [form, setForm] = useState({
    name: '', address: '', category: '카페', phone: '', homepageUrl: '', operatingHours: '', imageUrl: '', description: '',
  });
  const set = (k: string, v: string) => setForm((f) => ({ ...f, [k]: v }));

  const submit = () => {
    if (!form.name || !form.address) {
      Alert.alert('필수 입력', '장소 이름과 주소는 필수입니다.');
      return;
    }
    create.mutate(
      // 데모: 좌표는 서울 도심 기본값. 실서비스는 주소 → 지오코딩
      { ...form, lat: 37.5759, lng: 126.9769 },
      {
        onSuccess: (v) => router.replace(`/venue/${v.id}`),
        onError: (e: any) => Alert.alert('등록 실패', e?.message ?? '오류'),
      },
    );
  };

  return (
    <ScrollView style={styles.safe} contentContainerStyle={{ padding: space.lg, paddingBottom: 40 }}>
      <Stack.Screen options={{ title: '장소 등록' }} />
      <Field label="장소 이름 *" value={form.name} onChange={(v) => set('name', v)} placeholder="장소 이름을 입력하세요" />
      <Field label="주소 *" value={form.address} onChange={(v) => set('address', v)} placeholder="도로명 주소를 입력하세요" />

      <Text style={styles.label}>카테고리 *</Text>
      <View style={styles.cats}>
        {CATEGORIES.map((c) => <Chip key={c} label={c} selected={c === form.category} onPress={() => set('category', c)} />)}
      </View>

      <Field label="전화번호" value={form.phone} onChange={(v) => set('phone', v)} placeholder="전화번호를 입력하세요" />
      <Field label="웹사이트" value={form.homepageUrl} onChange={(v) => set('homepageUrl', v)} placeholder="https://" />
      <Field label="운영시간" value={form.operatingHours} onChange={(v) => set('operatingHours', v)} placeholder="예) 09:00 ~ 21:00" />
      <Field label="대표 이미지 URL" value={form.imageUrl} onChange={(v) => set('imageUrl', v)} placeholder="https://... (선택)" />
      <Field label="장소 설명" value={form.description} onChange={(v) => set('description', v)} placeholder="장소에 대한 설명을 입력하세요" multiline />

      <Pressable style={[styles.submit, create.isPending && styles.submitDisabled]} disabled={create.isPending} onPress={submit}>
        <Text style={styles.submitText}>{create.isPending ? '등록 중...' : '등록 신청 (관리자 승인 후 게시)'}</Text>
      </Pressable>
    </ScrollView>
  );
}

function Field({ label, value, onChange, placeholder, multiline }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string; multiline?: boolean }) {
  return (
    <View style={{ marginBottom: 14 }}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        style={[styles.input, multiline && styles.inputMulti]}
        value={value}
        onChangeText={onChange}
        placeholder={placeholder}
        placeholderTextColor={colors.textFaint}
        multiline={multiline}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  label: { fontSize: 13, fontWeight: '700', color: colors.text, marginBottom: 6 },
  input: { backgroundColor: colors.bgSoft, borderRadius: radius.md, paddingHorizontal: 14, paddingVertical: 11, fontSize: 14, color: colors.text },
  inputMulti: { height: 88, textAlignVertical: 'top' },
  cats: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 14 },
  submit: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16, alignItems: 'center', marginTop: 8 },
  submitDisabled: { backgroundColor: colors.textFaint },
  submitText: { color: colors.white, fontSize: 15, fontWeight: '800' },
});
