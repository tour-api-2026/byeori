import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import LoginRequired from '@/components/LoginRequired';
import { uploadImage, type PickedImage } from '@/lib/api/uploads';
import { useCreateVenueMutation } from '@/lib/hooks/queries';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, space } from '@/lib/theme';

const MAX_PHOTOS = 3;

const CATEGORIES = ['관람', '체험', '식사', '카페', '한복', '공예', '문화'];
const REGIONS = ['서울', '제주', '부산', '대전', '대구', '경주', '전주', '강릉', '속초', '통영'];
const DAYS = ['월', '화', '수', '목', '금', '토', '일'];

export default function VenueRegisterScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const create = useCreateVenueMutation();

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: '장소등록' }} />
        <LoginRequired description="장소 등록은 로그인 후에 이용할 수 있어요." />
      </View>
    );
  }

  const [name, setName] = useState('');
  const [category, setCategory] = useState('관람');
  const [region, setRegion] = useState('서울');
  const [address, setAddress] = useState('');
  const [addressDetail, setAddressDetail] = useState('');
  const [days, setDays] = useState<string[]>([]);
  const [openTime, setOpenTime] = useState('');
  const [closeTime, setCloseTime] = useState('');
  const [phone, setPhone] = useState('');
  const [description, setDescription] = useState('');
  const [photos, setPhotos] = useState<PickedImage[]>([]);
  const [uploading, setUploading] = useState(false);

  const toggleDay = (d: string) => setDays((s) => (s.includes(d) ? s.filter((x) => x !== d) : [...s, d]));

  const pickPhoto = async () => {
    if (photos.length >= MAX_PHOTOS) return;
    // 네이티브는 갤러리 권한 필요(웹은 자동 허용)
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      Alert.alert('권한 필요', '사진을 첨부하려면 갤러리 접근을 허용해주세요.');
      return;
    }
    const res = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.7,
    });
    if (res.canceled || !res.assets?.length) return;
    const a = res.assets[0];
    setPhotos((p) => [...p, { uri: a.uri, mimeType: a.mimeType, fileName: a.fileName }].slice(0, MAX_PHOTOS));
  };
  const removePhoto = (i: number) => setPhotos((p) => p.filter((_, idx) => idx !== i));

  const submit = async () => {
    if (create.isPending || uploading) return;
    if (!name || !address) {
      Alert.alert('필수 입력', '장소명과 주소는 필수입니다.');
      return;
    }
    // 사진이 있으면 먼저 업로드해 첫 장을 대표 이미지로 사용
    let imageUrl = '';
    if (photos.length) {
      setUploading(true);
      try {
        const urls = await Promise.all(photos.map(uploadImage));
        imageUrl = urls[0] ?? '';
      } catch (e: any) {
        setUploading(false);
        Alert.alert('사진 업로드 실패', e?.message ?? '사진을 올리지 못했습니다.');
        return;
      }
      setUploading(false);
    }
    const operatingHours = [days.join('·'), openTime && closeTime ? `${openTime}~${closeTime}` : '']
      .filter(Boolean).join(' ');
    create.mutate(
      {
        name,
        category,
        address: [region, address, addressDetail].filter(Boolean).join(' '),
        phone,
        operatingHours,
        description,
        homepageUrl: '',
        imageUrl,
        lat: 37.5759,
        lng: 126.9769,
      },
      {
        onSuccess: (v) => router.replace(`/venue/${v.id}`),
        onError: (e: any) => Alert.alert('등록 실패', e?.message ?? '오류'),
      },
    );
  };

  const busy = create.isPending || uploading;

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '장소등록' }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 28 + insets.bottom }} showsVerticalScrollIndicator={false}>
        {/* 안내 배너 */}
        <View style={styles.banner}>
          <Ionicons name="information-circle" size={18} color={colors.accent} />
          <Text style={styles.bannerText}>등록하신 장소는 관리자 확인 후 승인됩니다. 승인된 장소는 다른 사용자에게 노출되니 신중하게 작성해주세요.</Text>
        </View>

        <Label text="장소명" required />
        <TextInput style={styles.input} value={name} onChangeText={setName} placeholder="장소 이름을 입력하세요" placeholderTextColor={colors.textFaint} />

        <Label text="카테고리" required />
        <View style={styles.chips}>
          {CATEGORIES.map((c) => <SelChip key={c} label={c} on={c === category} onPress={() => setCategory(c)} />)}
        </View>

        <Label text="지역" required />
        <View style={styles.chips}>
          {REGIONS.map((r) => <SelChip key={r} label={r} on={r === region} onPress={() => setRegion(r)} />)}
        </View>

        <Label text="주소" required />
        <TextInput style={styles.input} value={address} onChangeText={setAddress} placeholder="주소를 입력하세요" placeholderTextColor={colors.textFaint} />
        <TextInput style={[styles.input, { marginTop: 8 }]} value={addressDetail} onChangeText={setAddressDetail} placeholder="상세 주소 (선택)" placeholderTextColor={colors.textFaint} />

        <Label text="운영시간" />
        <View style={styles.dayRow}>
          {DAYS.map((d) => (
            <Pressable key={d} style={[styles.day, days.includes(d) && styles.dayOn]} onPress={() => toggleDay(d)}>
              <Text style={[styles.dayText, days.includes(d) && styles.dayTextOn]}>{d}</Text>
            </Pressable>
          ))}
        </View>
        <View style={styles.timeRow}>
          <TextInput style={[styles.input, styles.timeInput]} value={openTime} onChangeText={setOpenTime} placeholder="09:00" placeholderTextColor={colors.textFaint} />
          <Text style={styles.timeDash}>~</Text>
          <TextInput style={[styles.input, styles.timeInput]} value={closeTime} onChangeText={setCloseTime} placeholder="21:00" placeholderTextColor={colors.textFaint} />
        </View>

        <Label text="가게 연락처" />
        <TextInput style={styles.input} value={phone} onChangeText={setPhone} placeholder="02-1234-1234" placeholderTextColor={colors.textFaint} keyboardType="phone-pad" />

        <Label text="설명" />
        <View>
          <TextInput
            style={[styles.input, styles.textarea]} value={description} onChangeText={(t) => t.length <= 500 && setDescription(t)}
            placeholder="장소에 대한 설명을 자유롭게 적어주세요" placeholderTextColor={colors.textFaint} multiline maxLength={500}
          />
          <Text style={styles.counter}>{description.length}/500</Text>
        </View>

        <Label text="사진등록" />
        <View style={styles.photoRow}>
          {photos.map((p, i) => (
            <View key={p.uri} style={styles.thumbWrap}>
              <Image source={{ uri: p.uri }} style={styles.thumb} contentFit="cover" />
              <Pressable style={styles.thumbX} hitSlop={6} onPress={() => removePhoto(i)}>
                <Ionicons name="close" size={14} color={colors.white} />
              </Pressable>
            </View>
          ))}
          {photos.length < MAX_PHOTOS && (
            <Pressable style={styles.photoBox} onPress={pickPhoto} disabled={busy}>
              <Ionicons name="add" size={26} color={colors.textFaint} />
              <Text style={styles.photoText}>{photos.length}/{MAX_PHOTOS}</Text>
            </Pressable>
          )}
        </View>
        <Text style={styles.photoHint}>최대 {MAX_PHOTOS}장까지 등록할 수 있어요 (첫 번째 사진이 대표 이미지)</Text>

        <Pressable style={[styles.submit, busy && styles.submitDisabled]} disabled={busy} onPress={submit}>
          <Text style={styles.submitText}>{uploading ? '사진 올리는 중...' : create.isPending ? '등록 중...' : '등록 신청하기'}</Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

function Label({ text, required }: { text: string; required?: boolean }) {
  return <Text style={styles.label}>{text}{required && <Text style={styles.req}> *</Text>}</Text>;
}

function SelChip({ label, on, onPress }: { label: string; on: boolean; onPress: () => void }) {
  return (
    <Pressable style={[styles.chip, on && styles.chipOn]} onPress={onPress}>
      <Text style={[styles.chipText, on && styles.chipTextOn]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  banner: { flexDirection: 'row', gap: 8, backgroundColor: colors.accentSoft, borderRadius: radius.md, padding: 12, marginBottom: 20 },
  bannerText: { flex: 1, fontSize: 12, color: colors.accent, lineHeight: 18, fontFamily: fonts.medium, fontWeight: '500' },
  label: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 18, marginBottom: 10 },
  req: { color: colors.danger },
  input: { backgroundColor: colors.white, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: colors.text },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: { paddingHorizontal: 16, paddingVertical: 8, borderRadius: radius.pill, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.white },
  chipOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { fontSize: 13, color: colors.textSub, fontFamily: fonts.medium, fontWeight: '500' },
  chipTextOn: { color: colors.white, fontFamily: fonts.semibold, fontWeight: '600' },
  dayRow: { flexDirection: 'row', gap: 6 },
  day: { flex: 1, aspectRatio: 1, maxHeight: 40, borderRadius: radius.sm, borderWidth: 1, borderColor: colors.border, backgroundColor: colors.white, alignItems: 'center', justifyContent: 'center' },
  dayOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  dayText: { fontSize: 13, color: colors.textSub, fontFamily: fonts.semibold, fontWeight: '600' },
  dayTextOn: { color: colors.white },
  timeRow: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 10 },
  timeInput: { flex: 1, textAlign: 'center' },
  timeDash: { fontSize: 16, color: colors.textFaint },
  textarea: { height: 100, textAlignVertical: 'top', paddingTop: 12 },
  counter: { position: 'absolute', right: 12, bottom: 10, fontSize: 11, color: colors.textFaint },
  photoRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 10 },
  photoBox: { width: 96, height: 96, borderRadius: radius.md, borderWidth: 1.5, borderColor: colors.border, borderStyle: 'dashed', alignItems: 'center', justifyContent: 'center', backgroundColor: colors.white },
  photoText: { fontSize: 11, color: colors.textFaint, marginTop: 2 },
  thumbWrap: { width: 96, height: 96 },
  thumb: { width: 96, height: 96, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  thumbX: { position: 'absolute', top: 4, right: 4, width: 22, height: 22, borderRadius: 11, backgroundColor: 'rgba(0,0,0,0.55)', alignItems: 'center', justifyContent: 'center' },
  photoHint: { fontSize: 12, color: colors.textFaint, marginTop: 8 },
  submit: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16, alignItems: 'center', marginTop: 28 },
  submitDisabled: { backgroundColor: colors.textFaint },
  submitText: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
});
