import { Ionicons } from '@expo/vector-icons';
import { Image } from '@/components/Image';
import * as ImagePicker from 'expo-image-picker';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import LoginRequired from '@/components/LoginRequired';
import { uploadImage, type PickedImage } from '@/lib/api/uploads';
import { useCreateVenueMutation, useUpdateVenueMutation, useVenueDetailQuery } from '@/lib/hooks/queries';
import { useT } from '@/lib/i18n';
import { categoryLabel, regionLabel } from '@/lib/i18n/labels';
import { DAY_PATTERN, VENUE_CATEGORIES as CATEGORIES, VENUE_DAYS as DAYS, VENUE_REGIONS as REGIONS } from '@/lib/venueForm';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, space } from '@/lib/theme';

const MAX_PHOTOS = 3;

export default function VenueRegisterScreen() {
  const router = useRouter();
  const t = useT();
  const insets = useSafeAreaInsets();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const { editId } = useLocalSearchParams<{ editId?: string }>();
  const isEdit = !!editId;
  const create = useCreateVenueMutation();
  const update = useUpdateVenueMutation();

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: isEdit ? t('venueRegister.title.edit') : t('venueRegister.title.new') }} />
        <LoginRequired description={t('venueRegister.loginDesc')} />
      </View>
    );
  }

  const [name, setName] = useState('');
  const [category, setCategory] = useState(CATEGORIES[0]);
  const [region, setRegion] = useState(REGIONS[0]);
  const [address, setAddress] = useState('');
  const [addressDetail, setAddressDetail] = useState('');
  const [days, setDays] = useState<string[]>([]);
  const [openTime, setOpenTime] = useState('');
  const [closeTime, setCloseTime] = useState('');
  const [phone, setPhone] = useState('');
  const [description, setDescription] = useState('');
  const [photos, setPhotos] = useState<PickedImage[]>([]);
  const [uploading, setUploading] = useState(false);

  // 편집 모드: 기존 장소 값을 폼에 채운다(등록 폼과 동일한 조합 규칙을 역파싱).
  const detailQ = useVenueDetailQuery(isEdit ? Number(editId) : 0);
  const prefilled = useRef(false);
  useEffect(() => {
    const d = detailQ.data;
    if (!isEdit || !d || prefilled.current) return;
    prefilled.current = true;
    setName(d.name ?? '');
    if (d.category && CATEGORIES.includes(d.category)) setCategory(d.category);
    const parts = (d.address ?? '').split(' ');
    if (parts[0] && REGIONS.includes(parts[0])) { setRegion(parts[0]); setAddress(parts.slice(1).join(' ')); }
    else { setRegion(''); setAddress(d.address ?? ''); }
    setPhone(d.phone ?? '');
    setDescription(d.description ?? '');
    const oh = d.operatingHours ?? '';
    const hm = oh.match(/(\d{1,2}:\d{2})\s*~\s*(\d{1,2}:\d{2})/);
    if (hm) { setOpenTime(hm[1]); setCloseTime(hm[2]); }
    const dayTok = oh.split(' ')[0] ?? '';
    if (DAY_PATTERN.test(dayTok)) setDays(dayTok.split('·').filter((x) => DAYS.includes(x)));
  }, [detailQ.data, isEdit]);

  const toggleDay = (d: string) => setDays((s) => (s.includes(d) ? s.filter((x) => x !== d) : [...s, d]));

  const pickPhoto = async () => {
    if (photos.length >= MAX_PHOTOS) return;
    // 네이티브는 갤러리 권한 필요(웹은 자동 허용)
    const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!perm.granted) {
      Alert.alert(t('venueRegister.error.permTitle'), t('venueRegister.error.permBody'));
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
    if (create.isPending || update.isPending || uploading) return;
    if (!name || !address) {
      Alert.alert(t('venueRegister.error.requiredTitle'), t('venueRegister.error.requiredBody'));
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
        Alert.alert(t('venueRegister.error.uploadTitle'), e?.message ?? t('venueRegister.error.uploadBody'));
        return;
      }
      setUploading(false);
    }
    const operatingHours = [days.join('·'), openTime && closeTime ? `${openTime}~${closeTime}` : '']
      .filter(Boolean).join(' ');
    const common = {
      name,
      category,
      address: [region, address, addressDetail].filter(Boolean).join(' '),
      phone,
      operatingHours,
      description,
      homepageUrl: '',
    };

    if (isEdit) {
      // 수정: 좌표는 기존 유지(생략), 대표 이미지는 새 사진 있을 때만 교체.
      update.mutate(
        { id: Number(editId), body: { ...common, ...(imageUrl ? { imageUrl } : {}) } },
        {
          onSuccess: () => router.replace(`/venue/${editId}`),
          onError: (e: any) => Alert.alert(t('venueRegister.error.updateTitle'), e?.message ?? t('common.error.unknown')),
        },
      );
      return;
    }

    create.mutate(
      { ...common, imageUrl, lat: 37.5759, lng: 126.9769 },
      {
        onSuccess: (v) => router.replace(`/venue/${v.id}`),
        onError: (e: any) => Alert.alert(t('venueRegister.error.createTitle'), e?.message ?? t('common.error.unknown')),
      },
    );
  };

  const busy = create.isPending || update.isPending || uploading;

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: isEdit ? t('venueRegister.title.edit') : t('venueRegister.title.new') }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 28 + insets.bottom }} showsVerticalScrollIndicator={false}>
        {/* 안내 배너 */}
        <View style={styles.banner}>
          <Ionicons name="information-circle" size={18} color={colors.accent} />
          <Text style={styles.bannerText}>{t('venueRegister.banner')}</Text>
        </View>

        <Label text={t('venueRegister.label.name')} required />
        <TextInput style={styles.input} value={name} onChangeText={setName} placeholder={t('venueRegister.ph.name')} placeholderTextColor={colors.textFaint} />

        <Label text={t('venueRegister.label.category')} required />
        <View style={styles.chips}>
          {CATEGORIES.map((c) => <SelChip key={c} label={categoryLabel(t, c)} on={c === category} onPress={() => setCategory(c)} />)}
        </View>

        <Label text={t('venueRegister.label.region')} required />
        <View style={styles.chips}>
          {REGIONS.map((r) => <SelChip key={r} label={regionLabel(t, r)} on={r === region} onPress={() => setRegion(r)} />)}
        </View>

        <Label text={t('venueRegister.label.address')} required />
        <TextInput style={styles.input} value={address} onChangeText={setAddress} placeholder={t('venueRegister.ph.address')} placeholderTextColor={colors.textFaint} />
        <TextInput style={[styles.input, { marginTop: 8 }]} value={addressDetail} onChangeText={setAddressDetail} placeholder={t('venueRegister.ph.addressDetail')} placeholderTextColor={colors.textFaint} />

        <Label text={t('venueRegister.label.hours')} />
        <View style={styles.dayRow}>
          {DAYS.map((d) => (
            <Pressable key={d} style={[styles.day, days.includes(d) && styles.dayOn]} onPress={() => toggleDay(d)}>
              <Text style={[styles.dayText, days.includes(d) && styles.dayTextOn]}>{t(`venueRegister.day.${d}`)}</Text>
            </Pressable>
          ))}
        </View>
        <View style={styles.timeRow}>
          <TextInput style={[styles.input, styles.timeInput]} value={openTime} onChangeText={setOpenTime} placeholder="09:00" placeholderTextColor={colors.textFaint} />
          <Text style={styles.timeDash}>~</Text>
          <TextInput style={[styles.input, styles.timeInput]} value={closeTime} onChangeText={setCloseTime} placeholder="21:00" placeholderTextColor={colors.textFaint} />
        </View>

        <Label text={t('venueRegister.label.phone')} />
        <TextInput style={styles.input} value={phone} onChangeText={setPhone} placeholder="02-1234-1234" placeholderTextColor={colors.textFaint} keyboardType="phone-pad" />

        <Label text={t('venueRegister.label.desc')} />
        <View>
          <TextInput
            style={[styles.input, styles.textarea]} value={description} onChangeText={(v) => v.length <= 500 && setDescription(v)}
            placeholder={t('venueRegister.ph.desc')} placeholderTextColor={colors.textFaint} multiline maxLength={500}
          />
          <Text style={styles.counter}>{description.length}/500</Text>
        </View>

        <Label text={t('venueRegister.label.photos')} />
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
        <Text style={styles.photoHint}>{t('venueRegister.photoHint', { n: MAX_PHOTOS })}</Text>

        <Pressable style={[styles.submit, busy && styles.submitDisabled]} disabled={busy} onPress={submit}>
          <Text style={styles.submitText}>
            {uploading ? t('venueRegister.submit.uploading')
              : isEdit ? (update.isPending ? t('venueRegister.submit.updating') : t('venueRegister.submit.update'))
              : create.isPending ? t('venueRegister.submit.creating') : t('venueRegister.submit.create')}
          </Text>
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
