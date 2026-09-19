import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import LoginRequired from '@/components/LoginRequired';
import { updateProfile } from '@/lib/api/account';
import { uploadImage, type PickedImage } from '@/lib/api/uploads';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, space } from '@/lib/theme';

const NAME_MIN = 2;
const NAME_MAX = 20;

/**
 * 프로필 수정 — 닉네임과 프로필 사진.
 *
 * 이메일은 로그인한 카카오·구글 계정에 묶인 값이라 보여주기만 한다.
 * 오류는 화면 안에 적는다. 웹에서는 Alert 가 아무것도 띄우지 않는다.
 */
export default function ProfileEditScreen() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const setUser = useAuthStore((s) => s.setUser);

  const [name, setName] = useState(user?.name ?? '');
  // 새로 고른 사진(아직 업로드 전). null 이면 현재 사진 유지 또는 삭제 상태를 따른다.
  const [picked, setPicked] = useState<PickedImage | null>(null);
  const [removed, setRemoved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!isLoggedIn || !user) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: '프로필 수정' }} />
        <LoginRequired description="프로필은 로그인 후에 고칠 수 있어요." />
      </View>
    );
  }

  const trimmed = name.trim();
  const nameValid = trimmed.length >= NAME_MIN && trimmed.length <= NAME_MAX;
  const preview = picked?.uri ?? (removed ? null : user.profileImageUrl ?? null);
  const changed = trimmed !== user.name || picked !== null || removed;

  const pickPhoto = async () => {
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      setError('사진을 바꾸려면 갤러리 접근을 허용해 주세요.');
      return;
    }
    const res = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      allowsEditing: true,
      aspect: [1, 1],
      quality: 0.7,
    });
    if (res.canceled || !res.assets?.length) return;
    const a = res.assets[0];
    setPicked({ uri: a.uri, mimeType: a.mimeType, fileName: a.fileName });
    setRemoved(false);
    setError(null);
  };

  const removePhoto = () => {
    setPicked(null);
    setRemoved(true);
  };

  const save = async () => {
    if (!nameValid || saving) return;
    setSaving(true);
    setError(null);
    try {
      const profileImageUrl = picked
        ? await uploadImage(picked)
        : removed ? null : user.profileImageUrl ?? null;
      const updated = await updateProfile({ name: trimmed, profileImageUrl });
      await setUser({ ...user, ...updated, profileImageUrl: updated.profileImageUrl ?? undefined });
      router.back();
    } catch (e: any) {
      setError(e?.message ?? '저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '프로필 수정' }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 40 }} keyboardShouldPersistTaps="handled">
        <View style={styles.photoWrap}>
          <Pressable onPress={pickPhoto} accessibilityLabel="프로필 사진 바꾸기">
            {preview ? (
              <Image source={preview} style={styles.avatar} contentFit="cover" />
            ) : (
              <View style={[styles.avatar, styles.avatarEmpty]}>
                <Ionicons name="person" size={40} color={colors.textFaint} />
              </View>
            )}
            <View style={styles.cameraBadge}>
              <Ionicons name="camera" size={15} color={colors.white} />
            </View>
          </Pressable>
          {preview ? (
            <Pressable hitSlop={8} onPress={removePhoto}>
              <Text style={styles.removeText}>사진 삭제</Text>
            </Pressable>
          ) : null}
        </View>

        <Text style={styles.label}>닉네임</Text>
        <TextInput
          style={[styles.input, !nameValid && trimmed.length > 0 && styles.inputError]}
          value={name}
          onChangeText={setName}
          maxLength={NAME_MAX}
          placeholder="리뷰에 표시될 이름"
          placeholderTextColor={colors.textFaint}
          returnKeyType="done"
          onSubmitEditing={save}
        />
        <Text style={styles.hint}>
          {NAME_MIN}~{NAME_MAX}자 · {trimmed.length}/{NAME_MAX}
        </Text>

        {user.email ? (
          <>
            <Text style={styles.label}>로그인 계정</Text>
            <View style={[styles.input, styles.readonly]}>
              <Text style={styles.readonlyText}>{user.email}</Text>
            </View>
            <Text style={styles.hint}>카카오·구글 계정의 이메일이라 여기서는 바꿀 수 없어요.</Text>
          </>
        ) : null}

        {error ? <Text style={styles.error}>{error}</Text> : null}

        <Pressable
          style={[styles.submit, (!nameValid || !changed || saving) && styles.submitDisabled]}
          disabled={!nameValid || !changed || saving}
          onPress={save}
        >
          {saving ? <ActivityIndicator color={colors.white} /> : <Text style={styles.submitText}>저장</Text>}
        </Pressable>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  photoWrap: { alignItems: 'center', marginTop: 8, marginBottom: 8, gap: 10 },
  avatar: { width: 96, height: 96, borderRadius: 48, backgroundColor: colors.white, borderWidth: 2, borderColor: colors.accent },
  avatarEmpty: { alignItems: 'center', justifyContent: 'center', borderColor: colors.border },
  cameraBadge: {
    position: 'absolute', right: 0, bottom: 0, width: 30, height: 30, borderRadius: 15,
    backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center',
    borderWidth: 2, borderColor: colors.bg,
  },
  removeText: { fontSize: 13, fontFamily: fonts.medium, fontWeight: '500', color: colors.textSub, textDecorationLine: 'underline' },
  label: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 18, marginBottom: 10 },
  input: {
    backgroundColor: colors.white, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md,
    paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: colors.text,
  },
  inputError: { borderColor: colors.danger },
  readonly: { backgroundColor: colors.bgSoft },
  readonlyText: { fontSize: 14, color: colors.textSub },
  hint: { fontSize: 12, color: colors.textFaint, marginTop: 6 },
  error: { fontSize: 13, color: colors.danger, marginTop: 16 },
  submit: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16, alignItems: 'center', marginTop: 28 },
  submitDisabled: { backgroundColor: colors.textFaint },
  submitText: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
});
