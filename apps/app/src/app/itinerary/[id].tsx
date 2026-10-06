import { Ionicons } from '@expo/vector-icons';
import { Stack, useLocalSearchParams, useRouter } from 'expo-router';
import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Calendar } from '@/components/Calendar';
import { DraggableStops } from '@/components/DraggableStops';
import { HeaderRight } from '@/components/HeaderRight';
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
  const { add, addPlace, remove, reorder, replacePlace, update: updateItem } = useItineraryItemMutation(id);
  // 스톱을 누르면 여는 편집 시트. 어느 항목인지만 들고 있는다.
  const [editingItem, setEditingItem] = useState<number | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  /**
   * 장소 고르는 창을 무엇 때문에 열었는지.
   *
   * null 이면 새로 담는 중이고, 숫자가 들어 있으면 그 항목의 장소를 바꾸는 중이다.
   * 창을 둘로 나누면 검색·주변·카카오 탭을 그대로 복사하게 된다.
   */
  const [replacingItem, setReplacingItem] = useState<number | null>(null);
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
  // 편집 중인 스톱과, 그 스톱이 속한 날의 id 목록(보이는 차례대로).
  const editingStop = data.items.find((x) => x.id === editingItem) ?? null;
  const editingDayIds = (byDate[editingStop?.visitDate ?? ''] ?? []).map((x) => x.id);
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
          <HeaderRight>
            {/* 편집은 저장 왼쪽에 둔다. 이름 줄의 연필은 이쪽으로 옮겼다. */}
            {!editing && (
              <Pressable style={styles.editBtn} hitSlop={8} onPress={openEdit}>
                <Ionicons name="create-outline" size={18} color={colors.text} />
                <Text style={styles.editBtnText}>편집</Text>
              </Pressable>
            )}
            <Pressable style={styles.saveBtn} onPress={() => router.back()}>
              <Text style={styles.saveText}>저장</Text>
            </Pressable>
          </HeaderRight>
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

        {/* 선택한 일차의 스톱 — 손잡이(≡)를 끌어 순서를 바꾼다 */}
        <View style={styles.stopsCard}>
          {(byDate[activeDay] ?? []).length ? (
            <DraggableStops
              items={byDate[activeDay] ?? []}
              pending={reorder.isPending}
              onPressItem={setEditingItem}
              onReorder={(itemIds) => reorder.mutate({ visitDate: activeDay, itemIds }, {
                onError: (e: any) => Alert.alert('순서를 바꾸지 못했어요', e?.message ?? '잠시 후 다시 시도해 주세요.'),
              })}
            />
          ) : <Text style={styles.empty}>이 날짜에 담은 장소가 없어요</Text>}
        </View>

        {/* 추가하기 */}
        <Pressable style={styles.addStop} onPress={() => setPickerOpen(true)}>
          <Text style={styles.addStopText}>추가하기</Text>
        </Pressable>
      </ScrollView>

      {/* 스톱 편집: 시간 · 일차 · 순서 · 제거 */}
      <StopEditSheet
        item={editingStop}
        dayList={allDays(data.startDate, data.endDate)}
        startDate={data.startDate}
        sameDayIds={editingDayIds}
        pending={updateItem.isPending || remove.isPending || reorder.isPending || replacePlace.isPending}
        onClose={() => setEditingItem(null)}
        onChange={(patch) => updateItem.mutate({ itemId: editingItem as number, ...patch })}
        onReplacePlace={() => {
          // 시트를 닫고 장소 창을 연다. 둘이 겹쳐 뜨면 뒤엣것을 못 누른다.
          setReplacingItem(editingItem);
          setEditingItem(null);
          setPickerOpen(true);
        }}
        onMove={(dir) => {
          // 한 칸 움직이는 것도 '그날 순서 전체'로 보낸다. 한 항목의 sortOrder 만 고치면
          // 같은 번호가 둘이 되어 어느 쪽이 위인지 서버가 정하지 못한다.
          const ids = [...editingDayIds];
          const at = ids.indexOf(editingItem as number);
          const to = at + dir;
          if (at < 0 || to < 0 || to >= ids.length) return;
          [ids[at], ids[to]] = [ids[to], ids[at]];
          reorder.mutate({ visitDate: editingStop?.visitDate as string, itemIds: ids }, {
            onError: (e: any) => Alert.alert('순서를 바꾸지 못했어요', e?.message ?? '잠시 후 다시 시도해 주세요.'),
          });
        }}
        onRemove={() => {
          const target = editingItem as number;
          setEditingItem(null);
          remove.mutate(target);
        }}
      />

      {/* 장소 선택: 이름 검색 · 이 루트 주변 · 카카오맵에서 직접 찾기 */}
      <PlacePicker
        visible={pickerOpen}
        onClose={() => { setPickerOpen(false); setReplacingItem(null); }}
        near={near}
        // 바꾸는 중에는 지금 이 자리의 장소도 후보에 둬야 한다 — 그래야 "역시 그대로" 가 된다.
        excludeIds={data.items
          .filter((it) => it.targetType === 'VENUE' && it.id !== replacingItem)
          .map((it) => it.targetId)}
        onPickVenue={(venueId) => {
          if (replacingItem != null) {
            updateItem.mutate({ itemId: replacingItem, targetType: 'VENUE', targetId: venueId }, {
              onError: (e: any) => Alert.alert('장소를 바꾸지 못했어요', e?.message ?? '잠시 후 다시 시도해 주세요.'),
            });
          } else {
            add.mutate({ targetType: 'VENUE', targetId: venueId, visitDate: activeDay, sortOrder: dayItems.length });
          }
          setPickerOpen(false);
          setReplacingItem(null);
        }}
        onPickPlace={(place) => {
          if (replacingItem != null) {
            replacePlace.mutate({ itemId: replacingItem, place }, {
              onError: (e: any) => Alert.alert('장소를 바꾸지 못했어요', e?.message ?? '잠시 후 다시 시도해 주세요.'),
            });
          } else {
            addPlace.mutate({ place, visitDate: activeDay, sortOrder: dayItems.length });
          }
          setPickerOpen(false);
          setReplacingItem(null);
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
  editBtn: { flexDirection: 'row', alignItems: 'center', gap: 4, paddingHorizontal: 6, paddingVertical: 6 },
  editBtnText: { color: colors.text, fontSize: 13, fontWeight: '700' },
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
  delBtn: { borderWidth: 1, borderColor: colors.danger, borderRadius: radius.sm, paddingHorizontal: 12, paddingVertical: 6 },
  delText: { fontSize: 12, color: colors.danger, fontWeight: '700' },
  // 추가하기
  addStop: { backgroundColor: colors.primary, borderRadius: radius.md, paddingVertical: 15, alignItems: 'center', marginTop: 20 },
  addStopText: { color: colors.white, fontSize: 15, fontWeight: '800' },
});
