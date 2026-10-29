import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { Image } from '@/components/Image';
import * as Location from 'expo-location';
import { Stack, useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { AreaMapPicker } from '@/components/AreaMapPicker';
import { Calendar } from '@/components/Calendar';
import LoginRequired from '@/components/LoginRequired';
import { AREAS, DEFAULT_THEMES, MAP_PICKED, MY_LOCATION } from '@/lib/aiRouteAreas';
import { AI_THEMES, generateAiRoute, saveAiRoute, type AiRoutePreview } from '@/lib/api/ai';
import { searchKakaoPlaces, type KakaoPlace } from '@/lib/api/itineraries';
import { useT } from '@/lib/i18n';
import { areaLabel, categoryLabel, type TFn } from '@/lib/i18n/labels';
import { sized } from '@/lib/img';
import { useAuthStore } from '@/lib/store/authStore';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts, radius, space } from '@/lib/theme';

/** 서버와 같은 상한. 반경 안에서 채울 수 있는 방문지가 이 정도가 한계다. */
const MAX_DAYS = 3;

/** YYYY-MM-DD, 기기 시간대 기준. */
function isoDate(offsetDays: number) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

// label 은 사전 키다 — 화면에서 t() 를 거친다. value 는 서버로 가는 날짜.
const DATES = [
  { label: 'aiRoute.date.today', value: isoDate(0) },
  { label: 'aiRoute.date.tomorrow', value: isoDate(1) },
  { label: 'aiRoute.date.dayAfter', value: isoDate(2) },
];
/** 서버가 받는 범위와 같다(오늘부터 90일). */
const DATE_MIN = isoDate(0);
const DATE_MAX = isoDate(90);

/** 'YYYY-MM-DD' → '9/24 (수)' */
function dateLabel(t: TFn, iso: string) {
  const [y, m, d] = iso.split('-').map(Number);
  const w = t(`common.weekday.${new Date(y, m - 1, d).getDay()}`);
  return t('common.dateShort', { m, d, w });
}

/**
 * 묻는 순서. 날짜 → 지역 → 테마 → 메모 순으로 한 화면에 쌓는다. 날짜가 맨 앞인 이유는
 * 여러 날 코스에서 날짜마다 지역을 따로 고르기 때문이다 — 날짜를 모르면 지역을 물을 수 없다.
 *
 * 제목은 aiRoute.step{N}.title 을 그대로 쓴다(단계는 없어졌지만 묻는 내용은 같다).
 */

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
  const insets = useSafeAreaInsets();   // 안드로이드는 edge-to-edge 라 시스템 바 아래까지 그린다
  const router = useRouter();
  const t = useT();
  const qc = useQueryClient();
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);

  const [range, setRange] = useState({ start: DATES[0].value, end: DATES[0].value });
  const dates = datesBetween(range.start, range.end);
  const rangeText = t('aiRoute.rangeDays', { start: shortDate(range.start), end: shortDate(range.end), n: dates.length });
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
        <Stack.Screen options={{ title: t('aiRoute.title') }} />
        <LoginRequired description={t('aiRoute.login.desc')} />
      </View>
    );
  }

  const dayArea = areas[editingDay] ?? areas[0] ?? { ...AREAS[0] };
  const customArea = dayArea.lat != null && !AREAS.some((a) => a.name === dayArea.name);

  const toggleTheme = (th: string) =>
    setThemes((cur) => (cur.includes(th) ? cur.filter((x) => x !== th) : [...cur, th]));

  const setDayArea = (a: Area) => setAreas((cur) => cur.map((x, i) => (i === editingDay ? a : x)));

  /**
   * 지도를 움직여 고른 중심. 칩으로 고른 이름은 버린다 — '서울 종로구' 칩을 누른 뒤
   * 지도를 부산으로 옮겼는데 이름이 '서울 종로구'로 남아 있으면 거짓말이 된다.
   *
   * 지명을 붙이려면 역지오코딩이 필요한데 호출 비용이 들고, AI 루트는 좌표만 있으면
   * 돌아간다. 그래서 '지도에서 고른 곳'으로 적는다.
   */
  const onMapPick = (c: { lat: number; lng: number }) =>
    setAreas((cur) => cur.map((x, i) => {
      if (i !== editingDay) return x;
      // 거의 같은 자리(약 55m)면 그대로 둔다 — 지도는 멎을 때마다 좌표를 보내므로
      // 이름이 붙은 칩 선택이 미세한 흔들림에 지워질 수 있다. 코드로 옮긴 메아리는
      // AreaMapPicker 가 이미 걸러 내므로, 여기 걸리는 것은 손떨림 정도다.
      if (x.lat != null && x.lng != null
          && Math.abs(x.lat - c.lat) < 0.0005 && Math.abs(x.lng - c.lng) < 0.0005) return x;
      return { name: MAP_PICKED, lat: c.lat, lng: c.lng };
    }));

  /** 내 위치는 고를 때가 아니라 만들 때 실제 좌표를 읽는다(권한 요청을 한 번만 하려고). */
  const resolveArea = async (a: Area): Promise<{ lat: number; lng: number; name: string }> => {
    if (a.lat != null && a.lng != null) return { lat: a.lat, lng: a.lng, name: a.name };
    const { status } = await Location.requestForegroundPermissionsAsync();
    if (status !== 'granted') throw new Error(t('aiRoute.error.location'));
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
      setError(e?.message ?? t('aiRoute.error.generate'));
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
      setError(e?.message ?? t('aiRoute.error.save'));
      setBusy(null);
    }
  };

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: t('aiRoute.title') }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 48 + insets.bottom }}>
        {!preview ? (
          <>
            {/* 한 화면에 전부 띄운다. 전에는 5단계로 나누고 마지막에 '확인' 화면을 뒀는데,
                그 확인 화면이 필요했던 이유가 "앞에서 뭘 골랐는지 잊는다"는 단계형의 약점
                때문이었다. 한눈에 보이면 그 문제도 확인 화면도 사라진다. */}

            {/* 언제 — 달력에서 출발일·도착일을 고른다 */}
            <Text style={styles.sectionLabel}>{t('aiRoute.step0.title')}</Text>
            <View style={styles.dateRow}>
              <Pressable style={styles.dateBox} onPress={() => { setPicking('start'); setCalendarOpen(true); }}>
                <Text style={styles.dateCaption}>{t('aiRoute.date.start')}</Text>
                <Text style={styles.dateValue}>{dateLabel(t, range.start)}</Text>
              </Pressable>
              <Ionicons name="arrow-forward" size={15} color={colors.textFaint} />
              <Pressable style={styles.dateBox} onPress={() => { setPicking('end'); setCalendarOpen(true); }}>
                <Text style={styles.dateCaption}>{t('aiRoute.date.end')}</Text>
                <Text style={styles.dateValue}>{dateLabel(t, range.end)}</Text>
              </Pressable>
            </View>
            <Text style={styles.sectionHint}>{t('aiRoute.step0.hint', { n: MAX_DAYS })}</Text>

            {/* 어디로 — 지도를 움직여 중심을 고른다. 빠른 선택 칩은 그대로 둔다. */}
            <Text style={styles.sectionLabel}>{t('aiRoute.step1.title')}</Text>
            {dates.length > 1 ? (
              <View style={[styles.chips, { marginBottom: 8 }]}>
                {dates.map((d, i) => (
                  <Chip key={d} label={t('aiRoute.dayItem', { n: i + 1, text: shortDate(d) })} on={editingDay === i}
                    onPress={() => setEditingDay(i)} />
                ))}
              </View>
            ) : null}
            {/* 내 위치는 만들 때 좌표를 읽으므로(resolveArea) 지도로 보여줄 것이 없다.
                그때는 지도를 숨기고 무엇을 고른 상태인지 글로 알린다. */}
            {dayArea.my ? (
              <View style={styles.myLocationBox}>
                <Ionicons name="navigate" size={16} color={colors.primary} />
                <Text style={styles.myLocationText}>{t('aiRoute.myLocation')}</Text>
              </View>
            ) : (
              <AreaMapPicker
                lat={dayArea.lat ?? AREAS[0].lat}
                lng={dayArea.lng ?? AREAS[0].lng}
                onPick={onMapPick}
              />
            )}
            <Text style={styles.pickedArea} numberOfLines={1}>{areaLabel(t, dayArea)}</Text>
            <View style={styles.chips}>
              {AREAS.map((a) => (
                <Chip key={a.name} label={areaLabel(t, a)} on={dayArea.name === a.name} onPress={() => setDayArea({ ...a })} />
              ))}
              <Chip label={t('aiRoute.myLocation')} icon="navigate" on={!!dayArea.my}
                onPress={() => setDayArea({ name: MY_LOCATION, my: true })} />
              <Chip label={t('aiRoute.otherArea')} icon="search" on={false}
                onPress={() => setAreaSearchOpen(true)} />
            </View>
            {dates.length > 1 ? (
              <Text style={styles.areaSummary}>
                {areas.map((a, i) => t('aiRoute.dayItem', { n: i + 1, text: areaLabel(t, a) })).join(' · ')}
              </Text>
            ) : null}

            {/* 무엇을 */}
            <Text style={styles.sectionLabel}>{t('aiRoute.step2.title')}</Text>
            <View style={styles.chips}>
              {AI_THEMES.map((th) => (
                <Chip key={th} label={categoryLabel(t, th)} on={themes.includes(th)} onPress={() => toggleTheme(th)} />
              ))}
            </View>

            {/* 메모 — 칩으로 못 고르는 요청 */}
            <Text style={styles.sectionLabel}>{t('aiRoute.step3.title')}</Text>
            <TextInput
              style={styles.noteInput}
              value={note}
              onChangeText={setNote}
              maxLength={100}
              placeholder={t('aiRoute.note.placeholder')}
              placeholderTextColor={colors.textFaint}
              returnKeyType="done"
            />

            <Pressable
              style={[styles.primary, { marginTop: 20 }, (!themes.length || !!busy) && styles.disabled]}
              disabled={!themes.length || !!busy}
              onPress={() => generate(false)}
            >
              {busy === 'generate' ? (
                <View style={styles.row}>
                  <ActivityIndicator color={colors.white} />
                  <Text style={styles.primaryText}>{t('aiRoute.generating')}</Text>
                </View>
              ) : (
                <View style={styles.row}>
                  <Ionicons name="sparkles" size={16} color={colors.white} />
                  <Text style={styles.primaryText}>{t('aiRoute.generate')}</Text>
                </View>
              )}
            </Pressable>
          </>
        ) : (
          /* 결과를 본 뒤 조건을 고치고 싶을 때. 요약 단계로 돌아간다. */
          <Pressable style={styles.secondary} onPress={() => { setPreview(null); }}>
            <Ionicons name="options-outline" size={16} color={colors.primary} />
            <Text style={styles.secondaryText}>{t('aiRoute.changeConditions')}</Text>
          </Pressable>
        )}

        {error ? <Text style={styles.error}>{error}</Text> : null}

        {preview ? (
          <View style={styles.result}>
            <View style={styles.aiBadge}>
              <Ionicons name="sparkles" size={12} color={colors.accent} />
              <Text style={styles.aiBadgeText}>
                {t('aiRoute.badge', {
                  range: preview.startDate + (preview.endDate !== preview.startDate ? ` ~ ${shortDate(preview.endDate)}` : ''),
                })}
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
                    <Text style={styles.dayHead}>{t('aiRoute.dayHead', { n: day, date: dateLabel(t, dayStops[0].date) })}</Text>
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
                        <Text style={styles.meta}>{[s.time, s.category ? categoryLabel(t, s.category) : s.category].filter(Boolean).join(' · ')}</Text>
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
                placeholder={t('aiRoute.tweak.placeholder')}
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
              {busy === 'save' ? <ActivityIndicator color={colors.white} /> : <Text style={styles.primaryText}>{t('aiRoute.save')}</Text>}
            </Pressable>
            <Text style={styles.note}>
              {t('aiRoute.footer.remaining', { n: preview.remainingToday })}{'\n'}
              {t('aiRoute.footer.disclaimer')}
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
                {picking === 'start' ? t('aiRoute.calendar.pickStart') : t('aiRoute.calendar.pickEnd')}
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
                ? t('aiRoute.calendar.noteStart')
                : t('aiRoute.calendar.noteEnd', { n: MAX_DAYS })}
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
  const t = useT();

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
    const timer = setTimeout(async () => {
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
      clearTimeout(timer);
    };
  }, [keyword, visible]);

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={onClose}>
      <View style={styles.modalBg}>
        <View style={styles.modalSheet}>
          <View style={styles.modalHead}>
            <Text style={styles.modalTitle}>{t('aiRoute.otherArea')}</Text>
            <Pressable onPress={onClose} hitSlop={8}><Ionicons name="close" size={22} color={colors.text} /></Pressable>
          </View>
          <View style={styles.searchBox}>
            <Ionicons name="search" size={16} color={colors.textFaint} />
            <TextInput
              style={styles.searchInput}
              value={keyword}
              onChangeText={setKeyword}
              placeholder={t('aiRoute.search.placeholder')}
              placeholderTextColor={colors.textFaint}
              returnKeyType="search"
              autoCorrect={false}
            />
          </View>
          <ScrollView keyboardShouldPersistTaps="handled" style={{ flexGrow: 0 }}>
            {loading ? <ActivityIndicator color={colors.primary} style={{ marginVertical: 18 }} /> : null}
            {!loading && keyword.trim() && results.length === 0 ? (
              <Text style={styles.modalNote}>{t('aiRoute.search.empty')}</Text>
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
              {t('aiRoute.search.note')}
            </Text>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}

/** 요약 한 줄. 누르면 그 단계로 돌아간다. */
function ReviewRow({ label, value, onPress, last }: {
  label: string; value: string; onPress: () => void; last?: boolean;
}) {
  return (
    <Pressable style={[styles.reviewRow, last && { borderBottomWidth: 0 }]} onPress={onPress}>
      <Text style={styles.reviewLabel}>{label}</Text>
      <Text style={styles.reviewValue} numberOfLines={2}>{value}</Text>
      <Ionicons name="chevron-forward" size={16} color={colors.textFaint} />
    </Pressable>
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
  // 한 화면에 여러 묶음이 쌓이므로 제목으로 구분한다. 단계형에서는 한 번에 하나만 보여
  // 제목이 하나뿐이었다.
  sectionLabel: {
    fontSize: 15, fontFamily: fonts.bold, fontWeight: '800', color: colors.text,
    marginTop: 22, marginBottom: 10,
  },
  sectionHint: { fontSize: 12, color: colors.textFaint, marginTop: 8 },
  dateRow: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  dateBox: {
    flex: 1, backgroundColor: colors.bgCard, borderRadius: radius.md,
    borderWidth: 1, borderColor: colors.border, paddingHorizontal: 14, paddingVertical: 11,
  },
  dateCaption: { fontSize: 11, color: colors.textFaint, marginBottom: 3 },
  dateValue: { fontSize: 15, fontFamily: fonts.bold, fontWeight: '700', color: colors.text },
  // 지도 아래에 지금 고른 곳을 적는다. 지도만으로는 '어디를 고른 것인지' 글로 확인할 길이 없다.
  pickedArea: {
    fontSize: 13, fontFamily: fonts.medium, fontWeight: '600', color: colors.text,
    marginTop: 8, marginBottom: 10,
  },
  myLocationBox: {
    flexDirection: 'row', alignItems: 'center', gap: 8,
    height: 220, borderRadius: radius.md, borderWidth: 1, borderColor: colors.border,
    backgroundColor: colors.bgSoft, justifyContent: 'center',
  },
  myLocationText: { fontSize: 14, fontFamily: fonts.medium, fontWeight: '600', color: colors.text },

  safe: { flex: 1, backgroundColor: colors.bg },
  wizardHead: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 20 },
  progressTrack: { flex: 1, height: 4, borderRadius: 2, backgroundColor: colors.border, overflow: 'hidden' },
  progressFill: { height: 4, borderRadius: 2, backgroundColor: colors.primary },
  progressText: { fontSize: 12, color: colors.textFaint, fontFamily: fonts.semibold, fontWeight: '600' },
  stepTitle: { fontSize: 20, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  stepHint: { fontSize: 13, color: colors.textFaint, marginTop: 6, marginBottom: 18, lineHeight: 19 },
  navRow: { flexDirection: 'row', alignItems: 'center', gap: 10, marginTop: 28 },
  backBtn: { flexDirection: 'row', alignItems: 'center', gap: 2, paddingRight: 14, paddingVertical: 15 },
  backText: { fontSize: 14, color: colors.textSub, fontFamily: fonts.semibold, fontWeight: '600' },
  skipBtn: { paddingHorizontal: 18, paddingVertical: 15 },
  skipText: { fontSize: 14, color: colors.textFaint, fontFamily: fonts.semibold, fontWeight: '600' },
  reviewCard: { backgroundColor: colors.bgSoft, borderRadius: radius.md, paddingHorizontal: 16 },
  reviewRow: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: colors.border },
  reviewLabel: { fontSize: 13, color: colors.textFaint, width: 52 },
  reviewValue: { flex: 1, fontSize: 14, color: colors.text, fontFamily: fonts.medium, fontWeight: '500' },
  secondary: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, borderWidth: 1.5, borderColor: colors.primary, borderRadius: radius.md, paddingVertical: 13, marginBottom: 20 },
  secondaryText: { fontSize: 14, color: colors.primary, fontFamily: fonts.bold, fontWeight: '800' },
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
