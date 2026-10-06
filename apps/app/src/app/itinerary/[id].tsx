import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Calendar } from '@/components/Calendar';
import { StopEditSheet } from '@/components/StopEditSheet';
import PlacePicker from '@/components/PlacePicker';
import {
  useCreateItineraryMutation, useItineraryItemMutation, useItineraryQuery,
  useUpdateItineraryMutation,
} from '@/lib/hooks/queries';
import { colors, fonts, radius, shadow, space } from '@/lib/theme';

/** 시작일~종료일의 모든 날짜. 항목이 없는 날도 '일차 옮기기' 후보가 되어야 한다. */
function allDays(start: string, end: string) {
  const out: string[] = [];
  const d = new Date(start), last = new Date(end);
  while (d <= last && out.length < 31) {
    const p = (n: number) => String(n).padStart(2, '0');
    out.push(`${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`);
    d.setDate(d.getDate() + 1);
  }
  return out.length ? out : [start];
}

// 'YYYY-MM-DD' 두 날짜의 일수 차이(일차 계산용)
function dayIndex(start: string, date: string): number {
  const a = new Date(start + 'T00:00:00');
  const b = new Date(date + 'T00:00:00');
  return Math.round((b.getTime() - a.getTime()) / 86400000) + 1;
}

export default function ItineraryEditScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  if (id === 'new') return <CreateForm />;
  return <Editor id={Number(id)} />;
}

function CreateForm() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const create = useCreateItineraryMutation();
  const [title, setTitle] = useState('');
  const [startDate, setStartDate] = useState<string | null>(null);
  const [endDate, setEndDate] = useState<string | null>(null);

  // 달력 탭 동작: 시작/종료를 순서대로 고른다.
  const onSelect = (date: string) => {
    if (!startDate || (startDate && endDate)) {
      setStartDate(date);
      setEndDate(null);
    } else if (date < startDate) {
      setStartDate(date);
    } else {
      setEndDate(date);
    }
  };

  const nights = startDate && endDate ? dayIndex(startDate, endDate) - 1 : 0;
  const canSubmit = !!title && !!startDate && !!endDate && !create.isPending;

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{ title: '루트 만들기' }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 40 + insets.bottom }}>
        <Text style={styles.label}>루트 이름</Text>
        <TextInput style={styles.input} value={title} onChangeText={setTitle} placeholder="예) 서울 1박 2일 한복 나들이" placeholderTextColor={colors.textFaint} />

        <Text style={styles.label}>여행 기간</Text>
        <Text style={styles.rangeHint}>
          {!startDate ? '달력에서 시작일을 선택하세요'
            : !endDate ? '종료일을 선택하세요'
            : `${startDate} ~ ${endDate} · ${nights}박 ${dayIndex(startDate, endDate)}일`}
        </Text>
        <Calendar rangeStart={startDate} rangeEnd={endDate} onSelectDate={onSelect} />

        <Pressable
          style={[styles.cta, !canSubmit && styles.disabled]}
          disabled={!canSubmit}
          onPress={() => create.mutate(
            { title, startDate: startDate!, endDate: endDate!, sourceType: 'CUSTOM' },
            { onSuccess: (d) => router.replace(`/itinerary/${d.id}`) },
          )}>
          <Text style={styles.ctaText}>{create.isPending ? '생성 중...' : '루트 만들기'}</Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

function Editor({ id }: { id: number }) {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { data, isLoading } = useItineraryQuery(id);
  const { add, addPlace, remove, update: updateItem } = useItineraryItemMutation(id);
  // 스톱을 누르면 여는 편집 시트. 어느 항목인지만 들고 있는다.
  const [editingItem, setEditingItem] = useState<number | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const update = useUpdateItineraryMutation(id);
  // 이름·기간 편집. 열려 있는 동안만 임시값을 들고 있다가 저장할 때 한 번에 보낸다.
  const [editing, setEditing] = useState(false);
  const [draftTitle, setDraftTitle] = useState('');
  const [draftRange, setDraftRange] = useState<{ start: string; end: string } | null>(null);
  const [picking, setPicking] = useState<'start' | 'end'>('start');

  if (isLoading || !data) return <View style={styles.center}><ActivityIndicator color={colors.primary} /></View>;

  // 날짜별 그룹
  const byDate: Record<string, typeof data.items> = {};
  data.items.forEach((it) => { (byDate[it.visitDate] ||= []).push(it); });
  const dates = Object.keys(byDate).sort();
  const activeDay = selectedDay ?? data.startDate;
  // 장소 추가 창의 "이 루트 주변" 기준: 그날 마지막으로 담은 곳(없으면 루트 전체의 마지막)
  const dayItems = byDate[activeDay] ?? [];
  const withCoords = (arr: typeof data.items) => [...arr].reverse().find((it) => it.lat != null && it.lng != null);
  const last = withCoords(dayItems) ?? withCoords(data.items);
  const near = last ? { lat: last.lat as number, lng: last.lng as number } : null;

  const openEdit = () => {
    setDraftTitle(data.title);
    setDraftRange({ start: data.startDate, end: data.endDate });
    setPicking('start');
    setEditing(true);
  };

  /**
   * 저장. 기간을 줄이면 그 바깥 날짜의 항목이 서버에 남아도 화면에서 사라진다.
   * 말없이 없어지면 잃어버린 것처럼 보이므로 몇 곳이 빠지는지 먼저 알린다.
   */
  const saveEdit = () => {
    if (!draftRange) return;
    const title = draftTitle.trim() || data.title;
    const { start, end } = draftRange;
    const dropped = data.items.filter((it) => it.visitDate < start || it.visitDate > end);
    const commit = () => {
      update.mutate({ title, startDate: start, endDate: end }, {
        onSuccess: () => {
          setEditing(false);
          if (selectedDay && (selectedDay < start || selectedDay > end)) setSelectedDay(start);
        },
        onError: (e: any) => Alert.alert('저장 실패', e?.message ?? '잠시 후 다시 시도해 주세요.'),
      });
    };
    if (dropped.length) {
      Alert.alert('기간 밖 장소가 있어요',
        `${dropped.length}곳이 새 기간을 벗어나 목록에서 보이지 않게 됩니다.`,
        [{ text: '취소', style: 'cancel' }, { text: '계속', onPress: commit }]);
      return;
    }
    commit();
  };

  return (
    <View style={styles.safe}>
      <Stack.Screen options={{
        title: '루트 만들기',
        headerRight: () => (
          <Pressable style={styles.saveBtn} onPress={() => router.back()}>
            <Text style={styles.saveText}>저장</Text>
          </Pressable>
        ),
      }} />
      <ScrollView contentContainerStyle={{ padding: space.lg, paddingBottom: 28 + insets.bottom }}>
        {/* 이름 */}
        {editing ? (
          <View style={styles.nameBox}>
            <Text style={styles.namePrefix}>이름</Text>
            <TextInput
              style={styles.nameInput}
              value={draftTitle}
              onChangeText={setDraftTitle}
              maxLength={40}
              placeholder="루트 이름"
              placeholderTextColor={colors.textFaint}
              returnKeyType="done"
            />
          </View>
        ) : (
          <Pressable style={styles.nameBox} onPress={openEdit}>
            <Text style={styles.namePrefix}>이름</Text>
            <Text style={styles.nameValue} numberOfLines={1}>{data.title}</Text>
            <Ionicons name="create-outline" size={18} color={colors.textFaint} />
          </Pressable>
        )}

        {/* 여행 기간 달력 */}
        <View style={{ marginTop: 16 }}>
          {editing && draftRange ? (
            <>
              <Text style={styles.editHint}>
                {picking === 'start' ? '시작일을 고르세요' : '마지막 날을 고르세요'}
                {'  '}
                <Text style={styles.editRange}>{draftRange.start} ~ {draftRange.end}</Text>
              </Text>
              <Calendar
                initialMonth={draftRange.start}
                rangeStart={draftRange.start}
                rangeEnd={draftRange.end}
                marked={dates}
                onSelectDate={(d) => {
                  if (picking === 'start') {
                    // 시작일이 기존 종료일을 넘으면 하루짜리로 맞춘다
                    setDraftRange({ start: d, end: d > draftRange.end ? d : draftRange.end });
                    setPicking('end');
                  } else {
                    setDraftRange(d < draftRange.start
                      ? { start: d, end: draftRange.start }
                      : { start: draftRange.start, end: d });
                    setPicking('start');
                  }
                }}
              />
              <View style={styles.editRow}>
                <Pressable style={styles.editCancel} onPress={() => setEditing(false)}>
                  <Text style={styles.editCancelText}>취소</Text>
                </Pressable>
                <Pressable
                  style={[styles.editSave, update.isPending && { opacity: 0.6 }]}
                  disabled={update.isPending}
                  onPress={saveEdit}>
                  <Text style={styles.editSaveText}>{update.isPending ? '저장 중…' : '기간 저장'}</Text>
                </Pressable>
              </View>
            </>
          ) : (
            <Calendar
              min={data.startDate}
              max={data.endDate}
              initialMonth={data.startDate}
              rangeStart={activeDay}
              rangeEnd={activeDay}
              marked={dates}
              onSelectDate={setSelectedDay}
            />
          )}
        </View>

        {/* 선택한 일차 (네이비 바) */}
        <View style={styles.dayBar}>
          <Text style={styles.dayBarText}>
            {dayIndex(data.startDate, activeDay)}일차  ({activeDay})
          </Text>
          <Ionicons name="chevron-down" size={18} color={colors.white} />
        </View>

        {/* 선택한 일차의 스톱 (번호 + 제거) */}
        <View style={styles.stopsCard}>
          {(byDate[activeDay] ?? []).length ? (byDate[activeDay] ?? []).map((it, i, arr) => (
            <Pressable key={it.id} style={styles.stop} onPress={() => setEditingItem(it.id)}>
              <View style={styles.rail}>
                {i > 0 && <View style={[styles.rLine, styles.rTop]} />}
                {i < arr.length - 1 && <View style={[styles.rLine, styles.rBot]} />}
                <View style={styles.stopNum}><Text style={styles.stopNumText}>{i + 1}</Text></View>
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.stopName}>{it.name}</Text>
                <Text style={styles.stopMeta}>{it.targetType === 'PERFORMANCE' ? '행사' : '장소'}{it.plannedTime ? ` · ${it.plannedTime}` : ''}</Text>
                {/* AI 루트는 추천 이유를 메모로 저장한다 */}
                {it.memo ? <Text style={styles.stopMemo}>{it.memo}</Text> : null}
              </View>
              <Ionicons name="chevron-forward" size={16} color={colors.textFaint} />
            </Pressable>
          )) : <Text style={styles.empty}>이 날짜에 담은 장소가 없어요</Text>}
        </View>

        {/* 추가하기 */}
        <Pressable style={styles.addStop} onPress={() => setPickerOpen(true)}>
          <Text style={styles.addStopText}>추가하기</Text>
        </Pressable>
      </ScrollView>

      {/* 스톱 편집: 시간 · 일차 · 순서 · 제거 */}
      <StopEditSheet
        item={data.items.find((x) => x.id === editingItem) ?? null}
        dayList={allDays(data.startDate, data.endDate)}
        startDate={data.startDate}
        sameDayCount={(byDate[data.items.find((x) => x.id === editingItem)?.visitDate ?? ''] ?? []).length}
        pending={updateItem.isPending || remove.isPending}
        onClose={() => setEditingItem(null)}
        onChange={(patch) => updateItem.mutate({ itemId: editingItem as number, ...patch })}
        onRemove={() => {
          const target = editingItem as number;
          setEditingItem(null);
          remove.mutate(target);
        }}
      />

      {/* 장소 선택: 이름 검색 · 이 루트 주변 · 카카오맵에서 직접 찾기 */}
      <PlacePicker
        visible={pickerOpen}
        onClose={() => setPickerOpen(false)}
        near={near}
        excludeIds={data.items.filter((it) => it.targetType === 'VENUE').map((it) => it.targetId)}
        onPickVenue={(venueId) => {
          add.mutate({ targetType: 'VENUE', targetId: venueId, visitDate: activeDay, sortOrder: dayItems.length });
          setPickerOpen(false);
        }}
        onPickPlace={(place) => {
          addPlace.mutate({ place, visitDate: activeDay, sortOrder: dayItems.length });
          setPickerOpen(false);
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg },
  label: { fontSize: 13, fontWeight: '700', color: colors.text, marginTop: 12, marginBottom: 6 },
  input: { backgroundColor: colors.bgSoft, borderRadius: radius.md, paddingHorizontal: 14, paddingVertical: 11, fontSize: 14, color: colors.text },
  cta: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 16, alignItems: 'center', marginTop: 24 },
  disabled: { backgroundColor: colors.textFaint },
  ctaText: { color: colors.white, fontSize: 15, fontWeight: '800' },
  rangeHint: { fontSize: 13, color: colors.textSub, marginBottom: 10, fontWeight: '600' },
  daySelInfo: { fontSize: 12, color: colors.textSub, marginTop: 8, fontWeight: '600' },
  h1: { fontSize: 22, fontWeight: '900', color: colors.text },
  range: { fontSize: 13, color: colors.textFaint, marginTop: 4 },
  empty: { fontSize: 14, color: colors.textFaint, paddingVertical: 20, textAlign: 'center' },
  // 저장 버튼 (헤더)
  saveBtn: { backgroundColor: colors.primary, borderRadius: radius.sm, paddingHorizontal: 14, paddingVertical: 7 },
  saveText: { color: colors.white, fontSize: 13, fontWeight: '800' },
  // 이름
  nameBox: { flexDirection: 'row', alignItems: 'center', gap: 10, backgroundColor: colors.white, borderWidth: 1.5, borderColor: colors.accent, borderRadius: radius.md, paddingHorizontal: 14, paddingVertical: 12 },
  namePrefix: { fontSize: 12, color: colors.textFaint, fontWeight: '600' },
  nameInput: { flex: 1, fontSize: 15, color: colors.text, fontFamily: fonts.bold, fontWeight: '800', padding: 0 },
  editHint: { fontSize: 13, color: colors.textSub, marginBottom: 8 },
  editRange: { color: colors.primary, fontFamily: fonts.bold, fontWeight: '800' },
  editRow: { flexDirection: 'row', gap: 10, marginTop: 12 },
  editCancel: { paddingHorizontal: 18, paddingVertical: 13 },
  editCancelText: { fontSize: 14, color: colors.textFaint, fontFamily: fonts.semibold, fontWeight: '600' },
  editSave: { flex: 1, alignItems: 'center', backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 13 },
  editSaveText: { color: colors.white, fontSize: 14, fontFamily: fonts.bold, fontWeight: '800' },
  nameValue: { flex: 1, fontSize: 15, fontWeight: '700', color: colors.text },
  // 일차 바
  dayBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: colors.primary, borderRadius: radius.md, paddingHorizontal: 16, paddingVertical: 13, marginTop: 18 },
  dayBarText: { color: colors.white, fontSize: 14, fontWeight: '800' },
  // 스톱 카드
  stopsCard: { backgroundColor: colors.bgCard, borderRadius: radius.md, padding: 14, marginTop: 10, ...shadow.card },
  stop: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 10 },
  rail: { width: 26, alignSelf: 'stretch', alignItems: 'center', justifyContent: 'center' },
  rLine: { position: 'absolute', width: 2, left: 12, backgroundColor: colors.border },
  rTop: { top: 0, bottom: '50%' },
  rBot: { top: '50%', bottom: 0 },
  stopNum: { width: 26, height: 26, borderRadius: 13, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  stopNumText: { color: colors.white, fontSize: 12, fontWeight: '800' },
  stopName: { fontSize: 15, fontWeight: '700', color: colors.text },
  stopMeta: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  stopMemo: { fontSize: 12, color: colors.textSub, marginTop: 4, lineHeight: 17 },
  delBtn: { borderWidth: 1, borderColor: colors.danger, borderRadius: radius.sm, paddingHorizontal: 12, paddingVertical: 6 },
  delText: { fontSize: 12, color: colors.danger, fontWeight: '700' },
  // 추가하기
  addStop: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center', marginTop: 20 },
  addStopText: { color: colors.white, fontSize: 15, fontWeight: '800' },
});
