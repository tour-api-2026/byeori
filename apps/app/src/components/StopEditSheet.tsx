import { Ionicons } from '@expo/vector-icons';
import { useEffect, useState } from 'react';
import { Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { ItineraryItem } from '@/lib/api/itineraries';
import { colors, fonts, radius, space } from '@/lib/theme';

/** 09:00 ~ 22:30, 30분 간격. 네이티브 시간 피커를 쓰려면 의존성이 늘어 목록으로 고른다. */
const TIMES = Array.from({ length: 28 }, (_, i) => {
  const m = 9 * 60 + i * 30;
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
});

/**
 * 사람이 친 글자를 'HH:MM' 으로 만든다. 못 만들면 null.
 *
 * '930' · '0930' · '9:30' · '09:30' 을 모두 받는다. 숫자만 치는 사람이 많은데
 * 콜론을 안 넣었다고 안 되면 왜 안 되는지 알기 어렵다.
 */
export function parseTime(raw: string): string | null {
  const d = raw.replace(/[^0-9]/g, '');
  if (d.length !== 3 && d.length !== 4) return null;
  const h = Number(d.slice(0, d.length - 2));
  const m = Number(d.slice(-2));
  if (h > 23 || m > 59) return null;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

/** 'YYYY-MM-DD' 두 날짜의 일수 차이 + 1 = 며칠째. */
function dayNo(start: string, date: string) {
  return Math.round((Date.parse(date) - Date.parse(start)) / 86400000) + 1;
}

/**
 * 루트의 한 스톱을 고치는 시트.
 *
 * 줄마다 버튼을 늘어놓으면 좁은 폭에서 금세 복잡해져, 누르면 열리는 한 곳에 모았다.
 * 서버는 처음부터 PATCH 로 방문일·시간·순서·메모를 받고 있었고 화면만 없었다.
 */
export function StopEditSheet({
  item, dayList, startDate, sameDayIds, pending, onClose, onChange, onMove, onReplacePlace, onRemove,
}: {
  item: ItineraryItem | null;
  /** 이 루트의 전체 날짜. 하루짜리면 '일차 옮기기'를 숨긴다. */
  dayList: string[];
  startDate: string;
  /** 같은 날 장소의 id, 보이는 차례대로. 끝에서 더 못 움직이게 막는 데 쓴다. */
  sameDayIds: number[];
  pending: boolean;
  onClose: () => void;
  onChange: (patch: { visitDate?: string; plannedTime?: string | null }) => void;
  /** 이 자리의 장소를 다른 곳으로 바꾼다. 장소 고르는 창은 부모가 연다. */
  onReplacePlace: () => void;
  /** 한 칸 위/아래. 드래그와 같은 경로(그날 순서 전체 다시 매기기)로 나간다. */
  onMove: (dir: -1 | 1) => void;
  onRemove: () => void;
}) {
  const insets = useSafeAreaInsets();
  // 입력 중에는 아직 시각이 아닌 글자('9')도 들고 있어야 한다. 다 치면 그때 보낸다.
  const [timeText, setTimeText] = useState('');
  useEffect(() => { setTimeText(item?.plannedTime ?? ''); }, [item?.id, item?.plannedTime]);
  if (!item) return null;

  const typed = parseTime(timeText);
  const timeBad = timeText.trim().length > 0 && typed === null;

  // sortOrder 값을 믿지 않는다 — 보이는 차례가 기준이다.
  const at = sameDayIds.indexOf(item.id);
  const first = at <= 0;
  const last = at < 0 || at >= sameDayIds.length - 1;

  return (
    <Modal visible transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.backdrop} onPress={onClose} />
      <View style={[styles.sheet, { paddingBottom: 16 + insets.bottom }]}>
        <View style={styles.handle} />

        <View style={styles.head}>
          <Text style={styles.name} numberOfLines={1}>{item.name}</Text>
          <Pressable hitSlop={8} onPress={onClose}>
            <Ionicons name="close" size={22} color={colors.textFaint} />
          </Pressable>
        </View>

        {/* 장소 바꾸기 */}
        <Pressable style={styles.swap} onPress={onReplacePlace} disabled={pending}>
          <Ionicons name="swap-horizontal" size={16} color={colors.primary} />
          <Text style={styles.swapText}>다른 장소로 바꾸기</Text>
          <Ionicons name="chevron-forward" size={16} color={colors.textFaint} />
        </Pressable>

        {/* 시간 — 직접 치거나 아래에서 고른다 */}
        <Text style={styles.label}>시간</Text>
        <View style={styles.timeRow}>
          <TextInput
            style={[styles.timeInput, timeBad && styles.timeInputBad]}
            value={timeText}
            onChangeText={setTimeText}
            onBlur={() => { if (typed && typed !== item.plannedTime) onChange({ plannedTime: typed }); }}
            onSubmitEditing={() => { if (typed && typed !== item.plannedTime) onChange({ plannedTime: typed }); }}
            placeholder="예) 0930"
            placeholderTextColor={colors.textFaint}
            keyboardType="numbers-and-punctuation"
            returnKeyType="done"
            maxLength={5}
          />
          <Pressable
            style={[styles.timeApply, (!typed || typed === item.plannedTime) && styles.timeApplyOff]}
            disabled={!typed || typed === item.plannedTime || pending}
            onPress={() => onChange({ plannedTime: typed })}>
            <Text style={styles.timeApplyText}>적용</Text>
          </Pressable>
        </View>
        <Text style={[styles.hint, timeBad && styles.hintBad]}>
          {timeBad ? '0930 · 9:30 처럼 적어 주세요 (00:00~23:59)' : '숫자만 쳐도 됩니다. 예) 930 → 09:30'}
        </Text>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.row, { marginTop: 8 }]}>
          <Pressable
            style={[styles.chip, !item.plannedTime && styles.chipOn]}
            onPress={() => { setTimeText(''); onChange({ plannedTime: null }); }}>
            <Text style={[styles.chipText, !item.plannedTime && styles.chipTextOn]}>미정</Text>
          </Pressable>
          {TIMES.map((t) => (
            <Pressable
              key={t}
              style={[styles.chip, item.plannedTime === t && styles.chipOn]}
              onPress={() => { setTimeText(t); onChange({ plannedTime: t }); }}>
              <Text style={[styles.chipText, item.plannedTime === t && styles.chipTextOn]}>{t}</Text>
            </Pressable>
          ))}
        </ScrollView>

        {/* 일차 — 하루짜리 루트에서는 옮길 곳이 없다 */}
        {dayList.length > 1 && (
          <>
            <Text style={styles.label}>일차</Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
              {dayList.map((d) => (
                <Pressable
                  key={d}
                  style={[styles.chip, item.visitDate === d && styles.chipOn]}
                  onPress={() => onChange({ visitDate: d })}>
                  <Text style={[styles.chipText, item.visitDate === d && styles.chipTextOn]}>
                    {dayNo(startDate, d)}일차
                  </Text>
                </Pressable>
              ))}
            </ScrollView>
          </>
        )}

        {/* 순서 — 목록에서 손잡이를 끌어도 되고, 여기서 한 칸씩 움직여도 된다 */}
        <Text style={styles.label}>순서</Text>
        <View style={styles.row}>
          <Pressable
            style={[styles.moveBtn, first && styles.moveOff]}
            disabled={first || pending}
            onPress={() => onMove(-1)}>
            <Ionicons name="arrow-up" size={16} color={first ? colors.border : colors.text} />
            <Text style={[styles.moveText, first && { color: colors.border }]}>위로</Text>
          </Pressable>
          <Pressable
            style={[styles.moveBtn, last && styles.moveOff]}
            disabled={last || pending}
            onPress={() => onMove(1)}>
            <Ionicons name="arrow-down" size={16} color={last ? colors.border : colors.text} />
            <Text style={[styles.moveText, last && { color: colors.border }]}>아래로</Text>
          </Pressable>
        </View>

        <Pressable style={styles.removeBtn} disabled={pending} onPress={onRemove}>
          <Ionicons name="trash-outline" size={16} color={colors.danger} />
          <Text style={styles.removeText}>이 장소 제거</Text>
        </Pressable>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: { flex: 1, backgroundColor: 'rgba(26,26,31,0.35)' },
  sheet: {
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    paddingHorizontal: space.lg,
    paddingTop: 10,
  },
  handle: { alignSelf: 'center', width: 44, height: 4, borderRadius: 2, backgroundColor: colors.border, marginBottom: 12 },
  head: { flexDirection: 'row', alignItems: 'center', gap: 12, marginBottom: 6 },
  name: { flex: 1, fontSize: 16, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  label: { fontSize: 13, color: colors.textFaint, marginTop: 16, marginBottom: 8 },
  swap: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 14, paddingVertical: 12, paddingHorizontal: 14,
          borderWidth: 1, borderColor: colors.border, borderRadius: radius.md },
  swapText: { flex: 1, fontSize: 14, color: colors.text, fontFamily: fonts.semibold, fontWeight: '600' },
  timeRow: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  timeInput: { flex: 1, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md,
               paddingHorizontal: 14, paddingVertical: 11, fontSize: 15, color: colors.text },
  timeInputBad: { borderColor: colors.danger },
  timeApply: { backgroundColor: colors.primary, borderRadius: radius.md, paddingHorizontal: 16, paddingVertical: 12 },
  timeApplyOff: { backgroundColor: colors.border },
  timeApplyText: { color: colors.white, fontSize: 14, fontFamily: fonts.bold, fontWeight: '800' },
  hint: { fontSize: 12, color: colors.textFaint, marginTop: 6, marginBottom: 2 },
  hintBad: { color: colors.danger },
  row: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  chip: { borderWidth: 1, borderColor: colors.border, borderRadius: radius.pill, paddingHorizontal: 14, paddingVertical: 8 },
  chipOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { fontSize: 13, color: colors.textSub, fontFamily: fonts.medium, fontWeight: '500' },
  chipTextOn: { color: colors.white, fontFamily: fonts.bold, fontWeight: '800' },
  moveBtn: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, borderWidth: 1, borderColor: colors.border, borderRadius: radius.md, paddingVertical: 12 },
  moveOff: { backgroundColor: colors.bgSoft },
  moveText: { fontSize: 14, color: colors.text, fontFamily: fonts.semibold, fontWeight: '600' },
  removeBtn: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, marginTop: 22, paddingVertical: 13 },
  removeText: { fontSize: 14, color: colors.danger, fontFamily: fonts.semibold, fontWeight: '600' },
});
