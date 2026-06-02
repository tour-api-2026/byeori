import { Ionicons } from '@expo/vector-icons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useMemo, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Switch, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Chip } from '@/components/Chip';
import { Rating } from '@/components/Rating';
import { Venue } from '@/lib/api/types';
import { useVenuesQuery } from '@/lib/hooks/queries';
import { colors, radius, shadow, space } from '@/lib/theme';

const CATS = ['전체', '한복', '카페', '체험', '문화', '맛집'];

// 데모: 실제 지도(react-native-maps)는 네이티브 빌드 필요. 웹 데모는 좌표 정규화 핀 오버레이로 대체.
export default function MapScreen() {
  const router = useRouter();
  const [cat, setCat] = useState('전체');
  const [hanbokOnly, setHanbokOnly] = useState(false);
  const [selected, setSelected] = useState<Venue | null>(null);

  const { data } = useVenuesQuery({
    category: cat === '전체' ? undefined : cat,
    hanbokDiscount: hanbokOnly || undefined,
    size: 50,
  });
  const venues = data?.content ?? [];

  const bounds = useMemo(() => {
    if (!venues.length) return null;
    const lats = venues.map((v) => Number(v.lat));
    const lngs = venues.map((v) => Number(v.lng));
    return { minLat: Math.min(...lats), maxLat: Math.max(...lats), minLng: Math.min(...lngs), maxLng: Math.max(...lngs) };
  }, [venues]);

  const pos = (v: Venue) => {
    if (!bounds) return { left: '50%', top: '50%' } as const;
    const x = bounds.maxLng === bounds.minLng ? 0.5 : (Number(v.lng) - bounds.minLng) / (bounds.maxLng - bounds.minLng);
    const y = bounds.maxLat === bounds.minLat ? 0.5 : (bounds.maxLat - Number(v.lat)) / (bounds.maxLat - bounds.minLat);
    return { left: `${8 + x * 84}%`, top: `${10 + y * 78}%` } as const;
  };

  return (
    <SafeAreaView style={styles.safe} edges={['top']}>
      {/* 한복 필터 토글 바 */}
      <View style={styles.topBar}>
        <View style={styles.toggleWrap}>
          <Text style={styles.toggleLabel}>👘 한복 입고 방문 시 할인되는 곳</Text>
          <Switch
            value={hanbokOnly}
            onValueChange={setHanbokOnly}
            trackColor={{ true: colors.hanbok, false: '#D1D5DB' }}
            thumbColor={colors.white}
          />
        </View>
      </View>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipsScroll} contentContainerStyle={styles.chips}>
        {CATS.map((c) => <Chip key={c} label={c} selected={c === cat} onPress={() => setCat(c)} />)}
      </ScrollView>

      {/* 지도 영역 (핀 오버레이) */}
      <View style={styles.map}>
        <View style={styles.grid} pointerEvents="none" />
        <Text style={styles.mapHint}>서울 도심 · {venues.length}곳</Text>
        {venues.map((v) => (
          <Pressable key={v.id} style={[styles.pin, pos(v), v.hanbokDiscount && styles.pinHanbok, selected?.id === v.id && styles.pinSelected]} onPress={() => setSelected(v)}>
            <Text style={[styles.pinText, v.hanbokDiscount && styles.pinTextHanbok]}>{Number(v.avgRating).toFixed(1)}</Text>
          </Pressable>
        ))}
      </View>

      {/* 선택된 장소 미니 카드 */}
      {selected && (
        <Pressable style={styles.miniCard} onPress={() => router.push(`/venue/${selected.id}`)}>
          <Image source={selected.imageUrl} style={styles.miniImg} contentFit="cover" />
          <View style={{ flex: 1 }}>
            <Text style={styles.miniName}>{selected.name}</Text>
            <Text style={styles.miniAddr} numberOfLines={1}>{selected.address}</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 3 }}>
              <Rating value={selected.avgRating} count={selected.reviewCount} />
              {selected.hanbokDiscount && <Text style={styles.miniHanbok}>한복할인</Text>}
            </View>
          </View>
          <Ionicons name="chevron-forward" size={20} color={colors.textFaint} />
        </Pressable>
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  topBar: { paddingHorizontal: space.lg, paddingTop: 6 },
  toggleWrap: {
    flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between',
    backgroundColor: '#FDECEC', borderRadius: radius.md, paddingHorizontal: 14, paddingVertical: 8,
  },
  toggleLabel: { fontSize: 13, fontWeight: '700', color: colors.hanbok },
  chipsScroll: { flexGrow: 0, flexShrink: 0 },
  chips: { gap: 8, paddingHorizontal: space.lg, paddingVertical: 10, alignItems: 'center' },
  map: { flex: 1, margin: space.lg, marginTop: 0, borderRadius: radius.lg, backgroundColor: '#EAF0E6', overflow: 'hidden' },
  grid: { position: 'absolute', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: '#E7EEE2' },
  mapHint: { position: 'absolute', top: 10, left: 12, fontSize: 11, color: '#7C8B72', fontWeight: '700' },
  pin: {
    position: 'absolute', minWidth: 34, height: 24, paddingHorizontal: 6, borderRadius: radius.pill,
    backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center', ...shadow.card,
    transform: [{ translateX: -17 }, { translateY: -12 }],
  },
  pinHanbok: { backgroundColor: colors.hanbok },
  pinSelected: { borderWidth: 2, borderColor: colors.white, transform: [{ translateX: -17 }, { translateY: -12 }, { scale: 1.15 }] },
  pinText: { color: colors.white, fontSize: 11, fontWeight: '800' },
  pinTextHanbok: { color: colors.white },
  miniCard: {
    flexDirection: 'row', alignItems: 'center', gap: 12, position: 'absolute', left: space.lg, right: space.lg, bottom: 16,
    backgroundColor: colors.white, borderRadius: radius.lg, padding: 12, ...shadow.card,
  },
  miniImg: { width: 60, height: 60, borderRadius: radius.md, backgroundColor: colors.bgSoft },
  miniName: { fontSize: 15, fontWeight: '700', color: colors.text },
  miniAddr: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  miniHanbok: { fontSize: 10, fontWeight: '800', color: colors.hanbok, backgroundColor: '#FDECEC', borderRadius: 4, paddingHorizontal: 5, paddingVertical: 1 },
});
