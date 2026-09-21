import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import * as Location from 'expo-location';
import { Stack, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Calendar } from '@/components/Calendar';
import LoginRequired from '@/components/LoginRequired';
import { AI_THEMES, generateAiRoute, saveAiRoute, type AiRoutePreview } from '@/lib/api/ai';
import { searchKakaoPlaces, type KakaoPlace } from '@/lib/api/itineraries';
import { sized } from '@/lib/img';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts, radius, space } from '@/lib/theme';

/**
 * AI 루트 생성 지역. 서버는 중심에서 반경 3km 안의 장소를 후보로 쓰므로,
 * 도시 전체가 아니라 걸어서 도는 권역(구도심·한옥마을 등)의 중심을 잡는다.
 */
const AREAS = [
  { name: '서울 종로구', lat: 37.573, lng: 126.9794 },
  { name: '서울 중구', lat: 37.5636, lng: 126.9976 },
  { name: '서울 용산구', lat: 37.5326, lng: 126.9906 },
  { name: '전주 한옥마을', lat: 35.815, lng: 127.153 },
  { name: '경주 황리단길', lat: 35.8364, lng: 129.2106 },
  { name: '부산 중구', lat: 35.098, lng: 129.032 },
  { name: '대구 중구', lat: 35.869, lng: 128.594 },
  { name: '제주시', lat: 33.5, lng: 126.531 },
] as const;

const MY_LOCATION = '내 위치 주변';
const DEFAULT_THEMES = ['문화', '맛집'];
/** 서버와 같은 상한. 반경 안에서 채울 수 있는 방문지가 이 정도가 한계다. */
const MAX_DAYS = 3;

/** YYYY-MM-DD, 기기 시간대 기준. */
function isoDate(offsetDays: number) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

const DATES = [
  { label: '오늘', value: isoDate(0) },
  { label: '내일', value: isoDate(1) },
  { label: '모레', value: isoDate(2) },
];
/** 서버가 받는 범위와 같다(오늘부터 90일). */
const DATE_MIN = isoDate(0);
const DATE_MAX = isoDate(90);

/** 'YYYY-MM-DD' → '9/24 (수)' */
function dateLabel(iso: string) {
  const [y, m, d] = iso.split('-').map(Number);
  const w = ['일', '월', '화', '수', '목', '금', '토'][new Date(y, m - 1, d).getDay()];
  return `${m}/${d} (${w})`;
}

/** 고른 지역. 칩·내 위치·검색으로 찾은 곳을 한 가지로 다룬다. */
type Area = { name: string; lat?: number; lng?: number; my?: boolean };

/** 시작일부터 종료일까지의 날짜들(최대 MAX_DAYS). */
function datesBetween(start: string, end: string) {
  const out: string[] = [];
  const d = new Date(start);
  const last = new Date(end);
  while (d <= last && out.length < MAX_DAYS) {
    const p = (n: number) => String(n).padStart(2, '0');
    out.push(`${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`);
    d.setDate(d.getDate() + 1);
  }
  return out.length ? out : [start];
}

/** 'YYYY-MM-DD' → '9/24' */
const shortDate = (iso: string) => iso.slice(5).replace('-', '/');

/**
 * AI 루트 만들기.
 *
 * 지역·테마·날짜를 칩으로 고르면 서버가 우리 DB에서 후보를 뽑고, AI가 그 안에서
 * 하루 코스를 짠다. 미리보기를 보고 저장하면 평범한 일정이 되어 기존 일정 화면
 * (지도·이동 경로)을 그대로 쓴다. 오류는 화면 안에 적는다 — 웹에서는 Alert 가 뜨지 않는다.
 */
export default function AiRouteScreen() {
  const router = useRouter();
  const qc = useQueryClient();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);

  const [range, setRange] = useState({ start: DATES[0].value, end: DATES[0].value });
  const dates = datesBetween(range.start, range.end);
  // 날짜마다 지역을 따로 고른다. 늘어난 날은 앞날과 같은 지역으로 채운다.
  const [areas, setAreas] = useState<Area[]>([{ ...AREAS[0] }]);
  const [editingDay, setEditingDay] = useState(0);
  const [areaSearchOpen, setAreaSearchOpen] = useState(false);
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [picking, setPicking] = useState<'start' | 'end'>('start');
  const [themes, setThemes] = useState<string[]>(DEFAULT_THEMES);

  useEffect(() => {
    setAreas((cur) => {
      if (cur.length === dates.length) return cur;
      const next = cur.slice(0, dates.length);
      while (next.length < dates.length) next.push({ ...next[next.length - 1] });
      return next;
    });
    setEditingDay((d) => Math.min(d, dates.length - 1));
  }, [dates.length]);
  const [note, setNote] = useState('');          // 칩으로 못 고르는 요청
  const [tweak, setTweak] = useState('');        // 결과를 보고 고쳐 달라는 요청
  const [preview, setPreview] = useState<AiRoutePreview | null>(null);
  const [busy, setBusy] = useState<'generate' | 'tweak' | 'save' | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!isLoggedIn) {
    return (
      <View style={styles.safe}>
        <Stack.Screen options={{ title: 'AI 루트 만들기' }} />
        <LoginRequired description="AI 루트 만들기는 로그인 후에 이용할 수 있어요." />
      </View>
    );
  }

  const dayArea = areas[editingDay] ?? areas[0] ?? { ...AREAS[0] };
  const customArea = dayArea.lat != null && !AREAS.some((a) => a.name === dayArea.name);

  const toggleTheme = (t: string) =>
    setThemes((cur) => (cur.includes(t) ? cur.filter((x) => x !== t) : [...cur, t]));

  const setDayArea = (a: Area) => setAreas((cur) => cur.map((x, i) => (i === editingDay ? a : x)));

  /** 내 위치는 고를 때가 아니라 만들 때 실제 좌표를 읽는다(권한 요청을 한 번만 하려고). */
  const resolveArea = async (a: Area): Promise<{ lat: number; lng: number; name: string }> => {
    if (a.lat != null && a.lng != null) return { lat: a.lat, lng: a.lng, name: a.name };
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') throw new Error('내 위치를 쓰려면 위치 권한을 허용해 주세요.');
    const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced });
    return { lat: pos.coords.latitude, lng: pos.coords.longitude, name: MY_LOCATION };
  };

  /**
   * 코스 만들기.
   * - tweakWith 가 있으면 "이렇게 바꿔 주세요"(다듬기)다. 지금 코스를 함께 보내 고쳐 받는다.
   * - 아니면 새로 만든다(regenerate=true 면 같은 조건이라도 캐시를 쓰지 않는다).
   */
  const generate = async (regenerate: boolean, tweakWith?: string) => {
    if (!themes.length || busy) return;
    setBusy(tweakWith ? 'tweak' : 'generate');
    setError(null);
    try {
      const days = [];
      for (let i = 0; i < dates.length; i += 1) {
        const c = await resolveArea(areas[i] ?? areas[0]);
        days.push({ date: dates[i], lat: c.lat, lng: c.lng, areaName: c.name });
      }
      const p = await generateAiRoute({
        days, categories: themes, regenerate,
        note: tweakWith ?? (note.trim() || undefined),
        previous: tweakWith
          ? preview?.stops.map((s) => ({ slot: s.slot, targetType: s.targetType, targetId: s.targetId, reason: s.reason }))
          : undefined,
      });
      setPreview(p);
      if (tweakWith) setTweak('');
    } catch (e: any) {
      setError(e?.message ?? '루트를 만들지 못했어요. 잠시 후 다시 시도해 주세요.');
    } finally {
      setBusy(null);
    }
  };

  const save = async () => {
    if (!preview || busy) return;
    setBusy('save');
    setError(null);
    try {
      await saveAiRoute(preview);
      await qc.invalidateQueries({ queryKey: ['itineraries'] });
      // 추천 코스 담기와 같이 내 루트 탭으로 보낸다(오늘·가까운 날짜라 대표 카드로 뜬다)
      router.dismissTo('/routes');
    } catch (e: any) {
      setError(e?.message ?? '저장하지 못했어요. 잠시 후 다시 시도해 주세요.');
      setBusy(null);
    }
  };

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: 'AI 루트 만들기' }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 48 }}>
        <Text style={styles.lead}>
          고른 조건에 맞춰 AI가 벼리의 장소·행사 중에서{'\n'}여행 코스를 짜 드려요. 최대 {MAX_DAYS}일까지 됩니다.
        </Text>

        <Text style={styles.label}>
          어디로 갈까요?
          {dates.length > 1 ? <Text style={styles.sub}> (날짜마다 따로 고를 수 있어요)</Text> : null}
        </Text>
        {dates.length > 1 ? (
          <View style={[styles.chips, { marginBottom: 10 }]}>
            {dates.map((d, i) => (
              <Chip key={d} label={`${i + 1}일차 ${shortDate(d)}`} on={editingDay === i}
                onPress={() => setEditingDay(i)} />
            ))}
          </View>
        ) : null}
        <View style={styles.chips}>
          {AREAS.map((a) => (
            <Chip key={a.name} label={a.name} on={dayArea.name === a.name} onPress={() => setDayArea({ ...a })} />
          ))}
          <Chip label={MY_LOCATION} icon="navigate" on={!!dayArea.my}
            onPress={() => setDayArea({ name: MY_LOCATION, my: true })} />
          {/* 칩에 없는 곳은 검색해서 그 좌표를 중심으로 쓴다 */}
          <Chip
            label={customArea ? dayArea.name : '다른 지역 찾기'}
            icon="search"
            on={customArea}
            onPress={() => setAreaSearchOpen(true)}
          />
        </View>
        {dates.length > 1 ? (
          <Text style={styles.areaSummary}>
            {areas.map((a, i) => `${i + 1}일차 ${a.name}`).join(' · ')}
          </Text>
        ) : null}

        <Text style={styles.label}>무엇을 하고 싶나요? <Text style={styles.sub}>(여러 개 선택)</Text></Text>
        <View style={styles.chips}>
          {AI_THEMES.map((t) => (
            <Chip key={t} label={t} on={themes.includes(t)} onPress={() => toggleTheme(t)} />
          ))}
        </View>

        <Text style={styles.label}>언제 가나요?</Text>
        <View style={styles.chips}>
          {DATES.map((d) => (
            <Chip key={d.value} label={`${d.label} ${shortDate(d.value)}`}
              on={range.start === d.value && range.end === d.value}
              onPress={() => setRange({ start: d.value, end: d.value })} />
          ))}
          <Chip
            label={dates.length > 1 ? `${shortDate(range.start)} ~ ${shortDate(range.end)} · ${dates.length}일` : '기간 선택'}
            icon="calendar-outline"
            on={dates.length > 1}
            onPress={() => {
              setPicking('start');
              setCalendarOpen(true);
            }}
          />
        </View>

        <Text style={styles.label}>더 알려주실 게 있나요? <Text style={styles.sub}>(선택)</Text></Text>
        <TextInput
          style={styles.noteInput}
          value={note}
          onChangeText={setNote}
          maxLength={100}
          placeholder="예: 아이와 함께, 많이 걷지 않게"
          placeholderTextColor={colors.textFaint}
          returnKeyType="done"
        />

        <Pressable
          style={[styles.primary, (!themes.length || !!busy) && styles.disabled]}
          disabled={!themes.length || !!busy}
          onPress={() => generate(!!preview)}
        >
          {busy === 'generate' ? (
            <View style={styles.row}>
              <ActivityIndicator color={colors.white} />
              <Text style={styles.primaryText}>AI가 코스를 짜는 중…</Text>
            </View>
          ) : (
            <View style={styles.row}>
              <Ionicons name="sparkles" size={16} color={colors.white} />
              <Text style={styles.primaryText}>{preview ? '다시 만들기' : 'AI로 루트 만들기'}</Text>
            </View>
          )}
        </Pressable>

        {error ? <Text style={styles.error}>{error}</Text> : null}

        {preview ? (
          <View style={styles.result}>
            <View style={styles.aiBadge}>
              <Ionicons name="sparkles" size={12} color={colors.accent} />
              <Text style={styles.aiBadgeText}>
                AI 추천 · {preview.startDate}
                {preview.endDate !== preview.startDate ? ` ~ ${shortDate(preview.endDate)}` : ''}
              </Text>
            </View>
            <Text style={styles.title}>{preview.title}</Text>
            {preview.summary ? <Text style={styles.summary}>{preview.summary}</Text> : null}

            {Array.from(new Set(preview.stops.map((s) => s.day))).map((day) => {
              const dayStops = preview.stops.filter((s) => s.day === day);
              return (
                <View key={day}>
                  {/* 하루짜리면 날짜 머리글이 군더더기라 여러 날일 때만 보여준다 */}
                  {preview.endDate !== preview.startDate ? (
                    <Text style={styles.dayHead}>{day}일차 · {dateLabel(dayStops[0].date)}</Text>
                  ) : null}
                  {dayStops.map((s, i) => (
                    <Pressable
                      key={`${s.targetType}-${s.targetId}`}
                      style={styles.stop}
                      onPress={() => router.push(s.targetType === 'VENUE' ? `/venue/${s.targetId}` : `/performances/${s.targetId}`)}
                    >
                      <View style={styles.stepCol}>
                        <View style={styles.step}><Text style={styles.stepText}>{i + 1}</Text></View>
                        {i < dayStops.length - 1 ? <View style={styles.stepLine} /> : null}
                      </View>
                      {s.imageUrl ? (
                        <Image source={sized(s.imageUrl, 160, 160)} style={styles.thumb} contentFit="cover" />
                      ) : (
                        <View style={[styles.thumb, styles.thumbEmpty]}>
                          <Ionicons name="image-outline" size={18} color={colors.textFaint} />
                        </View>
                      )}
                      <View style={{ flex: 1 }}>
                        <Text style={styles.meta}>{[s.time, s.category].filter(Boolean).join(' · ')}</Text>
                        <Text style={styles.name} numberOfLines={1}>{s.name}</Text>
                        {s.reason ? <Text style={styles.reason} numberOfLines={2}>{s.reason}</Text> : null}
                      </View>
                    </Pressable>
                  ))}
                </View>
              );
            })}

            <View style={styles.tweakBox}>
              <TextInput
                style={styles.tweakInput}
                value={tweak}
                onChangeText={setTweak}
                maxLength={100}
                placeholder="이렇게 바꿔 주세요 (예: 2일차 점심을 바꿔줘)"
                placeholderTextColor={colors.textFaint}
                returnKeyType="send"
                onSubmitEditing={() => tweak.trim() && generate(true, tweak.trim())}
              />
              <Pressable
                style={[styles.tweakBtn, (!tweak.trim() || !!busy) && styles.disabled]}
                disabled={!tweak.trim() || !!busy}
                onPress={() => generate(true, tweak.trim())}
              >
                {busy === 'tweak' ? <ActivityIndicator color={colors.white} size="small" />
                  : <Ionicons name="arrow-up" size={16} color={colors.white} />}
              </Pressable>
            </View>

            <Pressable style={[styles.primary, !!busy && styles.disabled]} disabled={!!busy} onPress={save}>
              {busy === 'save' ? <ActivityIndicator color={colors.white} /> : <Text style={styles.primaryText}>내 일정으로 저장</Text>}
            </Pressable>
            <Text style={styles.note}>
              오늘 {preview.remainingToday}번 더 만들 수 있어요 · 장소 정보 출처: ⓒ한국관광공사{'\n'}
              AI 추천은 참고용이에요. 운영시간은 장소 상세에서 확인해 주세요.
            </Text>
          </View>
        ) : null}
      </ScrollView>

      <AreaSearchModal
        visible={areaSearchOpen}
        onClose={() => setAreaSearchOpen(false)}
        onPick={(p) => {
          setDayArea({ name: p.name, lat: p.lat, lng: p.lng });
          setAreaSearchOpen(false);
        }}
      />

      <Modal visible={calendarOpen} animationType="slide" transparent onRequestClose={() => setCalendarOpen(false)}>
        <View style={styles.modalBg}>
          <View style={styles.modalSheet}>
            <View style={styles.modalHead}>
              <Text style={styles.modalTitle}>
                {picking === 'start' ? '출발일을 고르세요' : '마지막 날을 고르세요'}
              </Text>
              <Pressable onPress={() => setCalendarOpen(false)} hitSlop={8}>
                <Ionicons name="close" size={22} color={colors.text} />
              </Pressable>
            </View>
            <Calendar
              rangeStart={range.start}
              rangeEnd={picking === 'start' ? range.start : range.end}
              min={DATE_MIN}
              max={DATE_MAX}
              initialMonth={range.start.slice(0, 7)}
              onSelectDate={(d) => {
                if (picking === 'start' || d < range.start) {
                  // 첫 번째 탭은 출발일. 하루만 갈 수도 있어 종료일도 같이 잡아 둔다.
                  setRange({ start: d, end: d });
                  setPicking('end');
                  return;
                }
                const capped = datesBetween(range.start, d);
                setRange({ start: range.start, end: capped[capped.length - 1] });
                setPicking('start');
                setCalendarOpen(false);
              }}
            />
            <Text style={styles.modalNote}>
              {picking === 'start'
                ? '출발일을 고르세요. 오늘부터 90일 안에서 고를 수 있어요.'
                : `마지막 날을 고르세요. 최대 ${MAX_DAYS}일까지 만들 수 있어요.`}
            </Text>
          </View>
        </View>
      </Modal>
    </View>
  );
}

/** 칩에 없는 지역을 찾는다. 고른 곳의 좌표가 코스의 중심이 된다. */
function AreaSearchModal({ visible, onClose, onPick }: {
  visible: boolean; onClose: () => void; onPick: (p: KakaoPlace) => void;
}) {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<KakaoPlace[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!visible) {
      setKeyword('');
      setResults([]);
      return;
    }
    const q = keyword.trim();
    if (!q) {
      setResults([]);
      return;
    }
    let live = true;
    const t = setTimeout(async () => {
      setLoading(true);
      try {
        const r = await searchKakaoPlaces(q);
        if (live) setResults(r);
      } catch {
        if (live) setResults([]);
      } finally {
        if (live) setLoading(false);
      }
    }, 350);
    return () => {
      live = false;
      clearTimeout(t);
    };
  }, [keyword, visible]);

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.modalBg}>
        <View style={styles.modalSheet}>
          <View style={styles.modalHead}>
            <Text style={styles.modalTitle}>다른 지역 찾기</Text>
            <Pressable onPress={onClose} hitSlop={8}><Ionicons name="close" size={22} color={colors.text} /></Pressable>
          </View>
          <View style={styles.searchBox}>
            <Ionicons name="search" size={16} color={colors.textFaint} />
            <TextInput
              style={styles.searchInput}
              value={keyword}
              onChangeText={setKeyword}
              placeholder="지역·역·명소 이름 (예: 강릉역, 해운대)"
              placeholderTextColor={colors.textFaint}
              returnKeyType="search"
              autoCorrect={false}
            />
          </View>
          <ScrollView keyboardShouldPersistTaps="handled" style={{ flexGrow: 0 }}>
            {loading ? <ActivityIndicator color={colors.primary} style={{ marginVertical: 18 }} /> : null}
            {!loading && keyword.trim() && results.length === 0 ? (
              <Text style={styles.modalNote}>검색 결과가 없어요.</Text>
            ) : null}
            {results.map((p) => (
              <Pressable key={p.kakaoPlaceId} style={styles.areaRow} onPress={() => onPick(p)}>
                <Ionicons name="location-outline" size={16} color={colors.textSub} />
                <View style={{ flex: 1 }}>
                  <Text style={styles.areaName} numberOfLines={1}>{p.name}</Text>
                  <Text style={styles.areaAddr} numberOfLines={1}>{p.address}</Text>
                </View>
              </Pressable>
            ))}
            <Text style={styles.modalNote}>
              고른 곳 주변 2km 안에서 코스를 짜요. 장소가 드문 곳이면 가까운 역이나 번화가로 찾아 주세요.
            </Text>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

function Chip({ label, on, onPress, icon }: {
  label: string; on: boolean; onPress: () => void; icon?: keyof typeof Ionicons.glyphMap;
}) {
  return (
    <Pressable style={[styles.chip, on && styles.chipOn]} onPress={onPress}>
      {icon ? <Ionicons name={icon} size={12} color={on ? colors.white : colors.textSub} /> : null}
      <Text style={[styles.chipText, on && styles.chipTextOn]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  lead: { fontSize: 14, fontFamily: fonts.medium, fontWeight: '500', color: colors.textSub, lineHeight: 21 },
  label: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 22, marginBottom: 10 },
  sub: { fontSize: 12, fontWeight: '500', color: colors.textFaint },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 13, paddingVertical: 8,
    borderRadius: radius.pill, backgroundColor: colors.white, borderWidth: 1, borderColor: colors.border,
  },
  chipOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { fontSize: 13, fontFamily: fonts.semibold, fontWeight: '600', color: colors.textSub },
  chipTextOn: { color: colors.white },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  primary: {
    backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 15,
    alignItems: 'center', marginTop: 26,
  },
  disabled: { backgroundColor: colors.textFaint },
  primaryText: { color: colors.white, fontSize: 15, fontFamily: fonts.bold, fontWeight: '800' },
  modalBg: { flex: 1, backgroundColor: 'rgba(0,0,0,0.4)', justifyContent: 'flex-end' },
  modalSheet: {
    backgroundColor: colors.bg, borderTopLeftRadius: radius.xl, borderTopRightRadius: radius.xl,
    padding: space.lg, maxHeight: '80%',
  },
  modalHead: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  modalTitle: { fontSize: 17, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  modalNote: { fontSize: 12, color: colors.textFaint, textAlign: 'center', marginTop: 12, lineHeight: 18 },
  searchBox: {
    flexDirection: 'row', alignItems: 'center', gap: 8, backgroundColor: colors.white,
    borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingHorizontal: 12, height: 42,
  },
  searchInput: { flex: 1, fontSize: 14, color: colors.text, outlineStyle: 'none' } as any,
  areaRow: { flexDirection: 'row', alignItems: 'center', gap: 10, paddingVertical: 11 },
  areaName: { fontSize: 14, fontFamily: fonts.bold, fontWeight: '700', color: colors.text },
  areaAddr: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  noteInput: {
    backgroundColor: colors.white, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md,
    paddingHorizontal: 14, paddingVertical: 12, fontSize: 14, color: colors.text,
  },
  tweakBox: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 18 },
  tweakInput: {
    flex: 1, backgroundColor: colors.bg, borderWidth: 1, borderColor: colors.border, borderRadius: radius.pill,
    paddingHorizontal: 14, paddingVertical: 11, fontSize: 13, color: colors.text,
  },
  tweakBtn: {
    width: 38, height: 38, borderRadius: 19, backgroundColor: colors.primary,
    alignItems: 'center', justifyContent: 'center',
  },
  error: { fontSize: 13, color: colors.danger, marginTop: 14, lineHeight: 19 },
  result: { marginTop: 28, backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 16 },
  aiBadge: {
    flexDirection: 'row', alignItems: 'center', gap: 4, alignSelf: 'flex-start',
    backgroundColor: colors.accentSoft, borderRadius: radius.pill, paddingHorizontal: 9, paddingVertical: 4,
  },
  aiBadgeText: { fontSize: 11, fontFamily: fonts.bold, fontWeight: '700', color: colors.accent },
  title: { fontSize: 19, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 10 },
  areaSummary: { fontSize: 12, color: colors.textFaint, marginTop: 8, lineHeight: 18 },
  dayHead: {
    fontSize: 13, fontFamily: fonts.bold, fontWeight: '800', color: colors.primary,
    marginTop: 18, marginBottom: 2,
  },
  summary: { fontSize: 13, color: colors.textSub, lineHeight: 20, marginTop: 6, marginBottom: 6 },
  stop: { flexDirection: 'row', gap: 10, paddingTop: 14 },
  stepCol: { alignItems: 'center', width: 22 },
  step: {
    width: 22, height: 22, borderRadius: 11, backgroundColor: colors.primary,
    alignItems: 'center', justifyContent: 'center',
  },
  stepText: { color: colors.white, fontSize: 11, fontWeight: '800' },
  stepLine: { flex: 1, width: 2, backgroundColor: colors.border, marginTop: 4 },
  thumb: { width: 60, height: 60, borderRadius: radius.sm, backgroundColor: colors.bgSoft },
  thumbEmpty: { alignItems: 'center', justifyContent: 'center' },
  meta: { fontSize: 11, color: colors.textFaint, fontWeight: '600' },
  name: { fontSize: 15, fontFamily: fonts.bold, fontWeight: '800', color: colors.text, marginTop: 2 },
  reason: { fontSize: 12, color: colors.textSub, lineHeight: 18, marginTop: 3 },
  note: { fontSize: 11, color: colors.textFaint, lineHeight: 17, marginTop: 12, textAlign: 'center' },
});
