import { Ionicons } from '@expo/vector-icons';
import { useQuery } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { searchKakaoPlaces, type KakaoPlace } from '@/lib/api/itineraries';
import type { Venue } from '@/lib/api/types';
import { useLiveSearchQuery, useNearbyVenuesQuery, useVenuesQuery } from '@/lib/hooks/queries';
import { sized } from '@/lib/img';
import { colors, fonts, radius, space } from '@/lib/theme';

const CATEGORIES = ['전체', '문화', '맛집', '카페', '전통시장', '체험', '공예', '한옥스테이'] as const;
const NEAR_RADIUS = 2000;

type Props = {
  visible: boolean;
  onClose: () => void;
  /** "이 루트 주변"의 기준점(보통 마지막 방문지). 없으면 추천 장소를 보여준다. */
  near: { lat: number; lng: number } | null;
  /** 이미 루트에 담은 장소 id. 목록에서 뺀다. */
  excludeIds?: number[];
  onPickVenue: (venueId: number) => void;
  onPickPlace: (place: KakaoPlace) => void;
};

/** 입력이 멈춘 뒤에만 검색한다(글자마다 API를 부르지 않게). */
function useDebounced<T>(value: T, ms = 300) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

/**
 * 루트에 넣을 장소 고르기.
 *
 * 전에는 전국 상위 30곳이 목록으로만 나와 원하는 곳을 찾을 수 없었다. 이름으로 찾고,
 * 검색 전에는 루트의 마지막 장소 주변을 보여준다. 벼리 DB에 없는 식당·카페는
 * 카카오맵에서 찾아 넣는다(서버가 나만 보는 장소로 저장한다).
 */
export default function PlacePicker({ visible, onClose, near, excludeIds = [], onPickVenue, onPickPlace }: Props) {
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState<string>('전체');
  const [mode, setMode] = useState<'byeori' | 'kakao'>('byeori');
  const q = useDebounced(keyword.trim());
  const cat = category === '전체' ? undefined : category;

  // 창을 닫으면 처음 상태로 돌린다
  useEffect(() => {
    if (!visible) {
      setKeyword('');
      setCategory('전체');
      setMode('byeori');
    }
  }, [visible]);

  const searching = mode === 'byeori' && q.length > 0;
  const live = useLiveSearchQuery(searching ? { keyword: q, category: cat } : null);
  const nearby = useNearbyVenuesQuery(!searching && near && visible
    ? { lat: Number(near.lat.toFixed(4)), lng: Number(near.lng.toFixed(4)), radius: NEAR_RADIUS, category: cat }
    : null);
  const popular = useVenuesQuery({ category: cat, size: 30 });
  const kakao = useQuery({
    queryKey: ['kakao-places', q, near?.lat, near?.lng],
    queryFn: () => searchKakaoPlaces(q, near),
    enabled: mode === 'kakao' && q.length > 0,
    staleTime: 5 * 60 * 1000,
  });

  const venues: Venue[] = searching
    ? live.data ?? []
    : near ? nearby.data ?? [] : popular.data?.content ?? [];
  const loading = searching ? live.isLoading : near ? nearby.isLoading : popular.isLoading;
  const listLabel = searching ? `'${q}' 검색 결과` : near ? '이 루트 주변' : '추천 장소';

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.bg}>
        <View style={styles.sheet}>
          <View style={styles.head}>
            <Text style={styles.title}>{mode === 'kakao' ? '카카오맵에서 찾기' : '장소 추가'}</Text>
            <Pressable onPress={onClose} hitSlop={8}><Ionicons name="close" size={22} color={colors.text} /></Pressable>
          </View>

          <View style={styles.searchBox}>
            <Ionicons name="search" size={16} color={colors.textFaint} />
            <TextInput
              style={styles.input}
              value={keyword}
              onChangeText={setKeyword}
              placeholder={mode === 'kakao' ? '식당·카페 이름 (예: 명동교자)' : '장소 이름 검색'}
              placeholderTextColor={colors.textFaint}
              returnKeyType="search"
              autoCorrect={false}
            />
            {keyword ? (
              <Pressable onPress={() => setKeyword('')} hitSlop={8}>
                <Ionicons name="close-circle" size={16} color={colors.textFaint} />
              </Pressable>
            ) : null}
          </View>

          {mode === 'byeori' ? (
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.chipRow}
              contentContainerStyle={{ gap: 6 }}>
              {CATEGORIES.map((c) => (
                <Pressable key={c} style={[styles.chip, category === c && styles.chipOn]} onPress={() => setCategory(c)}>
                  <Text style={[styles.chipText, category === c && styles.chipTextOn]}>{c}</Text>
                </Pressable>
              ))}
            </ScrollView>
          ) : (
            <Pressable style={styles.backLink} onPress={() => setMode('byeori')}>
              <Ionicons name="chevron-back" size={14} color={colors.accent} />
              <Text style={styles.backLinkText}>벼리 장소로 돌아가기</Text>
            </Pressable>
          )}

          <ScrollView keyboardShouldPersistTaps="handled" style={{ flexGrow: 0 }}>
            {mode === 'byeori' ? (
              <>
                <Text style={styles.section}>{listLabel}</Text>
                {loading ? <ActivityIndicator color={colors.primary} style={{ marginVertical: 20 }} /> : null}
                {!loading && venues.length === 0 ? (
                  <Text style={styles.empty}>
                    {searching ? '벼리에 등록된 장소 중에는 없어요.' : '주변에 등록된 장소가 없어요. 이름으로 찾아보세요.'}
                  </Text>
                ) : null}
                {venues.filter((v) => v.id != null && !excludeIds.includes(v.id)).map((v) => (
                  <Pressable key={v.id} style={styles.row} onPress={() => onPickVenue(v.id)}>
                    {v.imageUrl ? (
                      <Image source={sized(v.imageUrl, 120, 120)} style={styles.img} contentFit="cover" />
                    ) : (
                      <View style={[styles.img, styles.imgEmpty]}><Ionicons name="image-outline" size={16} color={colors.textFaint} /></View>
                    )}
                    <View style={{ flex: 1 }}>
                      <Text style={styles.name} numberOfLines={1}>{v.name}</Text>
                      <Text style={styles.meta} numberOfLines={1}>{[v.category, v.address].filter(Boolean).join(' · ')}</Text>
                    </View>
                    <Ionicons name="add-circle" size={22} color={colors.primary} />
                  </Pressable>
                ))}

                {/* 벼리 DB 밖의 식당·카페 */}
                <Pressable
                  style={styles.kakaoCta}
                  onPress={() => setMode('kakao')}
                >
                  <Ionicons name="storefront-outline" size={16} color={colors.primary} />
                  <Text style={styles.kakaoCtaText}>찾는 곳이 없나요? 카카오맵에서 찾기</Text>
                  <Ionicons name="chevron-forward" size={16} color={colors.primary} />
                </Pressable>
              </>
            ) : (
              <>
                {q.length === 0 ? (
                  <Text style={styles.empty}>가고 싶은 식당·카페 이름을 입력해 주세요.{'\n'}고른 곳은 나만 보는 장소로 루트에 들어가요.</Text>
                ) : kakao.isLoading ? (
                  <ActivityIndicator color={colors.primary} style={{ marginVertical: 20 }} />
                ) : (kakao.data ?? []).length === 0 ? (
                  <Text style={styles.empty}>검색 결과가 없어요.</Text>
                ) : (
                  (kakao.data ?? []).map((p) => (
                    <Pressable key={p.kakaoPlaceId} style={styles.row} onPress={() => onPickPlace(p)}>
                      <View style={[styles.img, styles.imgEmpty]}>
                        <Ionicons name={p.category === '맛집' ? 'restaurant-outline' : p.category === '카페' ? 'cafe-outline' : 'location-outline'}
                          size={18} color={colors.textSub} />
                      </View>
                      <View style={{ flex: 1 }}>
                        <Text style={styles.name} numberOfLines={1}>{p.name}</Text>
                        <Text style={styles.meta} numberOfLines={1}>{[p.categoryName, p.address].filter(Boolean).join(' · ')}</Text>
                      </View>
                      <Ionicons name="add-circle" size={22} color={colors.primary} />
                    </Pressable>
                  ))
                )}
                <Text style={styles.source}>장소 검색: 카카오</Text>
              </>
            )}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  bg: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: colors.bg, borderTopLeftRadius: radius.xl, borderTopRightRadius: radius.xl,
    padding: space.lg, maxHeight: '80%',
  },
  head: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  title: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  searchBox: {
    flexShrink: 0, flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: colors.white,
    borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingHorizontal: 12, height: 42,
  },
  input: { flex: 1, fontSize: 14, color: colors.text, outlineStyle: 'none' } as any,
  // 아래 목록이 길면 칩 줄이 눌려 글자가 사라졌다. 높이를 줄이지 않게 고정한다.
  chipRow: { flexGrow: 0, flexShrink: 0, marginTop: 10 },
  chip: {
    paddingHorizontal: 11, paddingVertical: 6, borderRadius: radius.pill,
    backgroundColor: colors.white, borderWidth: 1, borderColor: colors.border,
  },
  chipOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { fontSize: 12, fontFamily: fonts.semibold, fontWeight: '600', color: colors.textSub },
  chipTextOn: { color: colors.white },
  backLink: { flexDirection: 'row', alignItems: 'center', gap: 2, marginTop: 10 },
  backLinkText: { fontSize: 13, fontWeight: '700', color: colors.accent },
  section: { fontSize: 12, fontWeight: '700', color: colors.textFaint, marginTop: 14, marginBottom: 4 },
  empty: { fontSize: 13, color: colors.textFaint, textAlign: 'center', marginVertical: 18, lineHeight: 20 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 9 },
  img: { width: 44, height: 44, borderRadius: radius.sm, backgroundColor: colors.bgSoft },
  imgEmpty: { alignItems: 'center', justifyContent: 'center' },
  name: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '700', color: colors.text },
  meta: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  kakaoCta: {
    flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 12, marginBottom: 4,
    backgroundColor: colors.primarySoft, borderRadius: radius.md, paddingHorizontal: 12, paddingVertical: 12,
  },
  kakaoCtaText: { flex: 1, fontSize: 13, fontWeight: '700', color: colors.primary },
  source: { fontSize: 11, color: colors.textFaint, textAlign: 'right', marginTop: 10 },
});
