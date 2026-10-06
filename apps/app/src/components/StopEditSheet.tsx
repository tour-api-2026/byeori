import { Ionicons } from '@expo/vector-icons';
import { Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { ItineraryItem } from '@/lib/api/itineraries';
import { colors, fonts, radius, space } from '@/lib/theme';

/** 09:00 ~ 22:30, 30분 간격. 네이티브 시간 피커를 쓰려면 의존성이 늘어 목록으로 고른다. */
const TIMES = Array.from({ length: 28 }, (_, i) => {
  const m = 9 * 60 + i * 30;
  return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
});

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
  item, dayList, startDate, sameDayIds, pending, onClose, onChange, onMove, onRemove,
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
  /** 한 칸 위/아래. 드래그와 같은 경로(그날 순서 전체 다시 매기기)로 나간다. */
  onMove: (dir: -1 | 1) => void;
  onRemove: () => void;
}) {
  const insets = useSafeAreaInsets();
  if (!item) return null;

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

        {/* 시간 */}
        <Text style={styles.label}>시간</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row}>
          <Pressable
            style={[styles.chip, !item.plannedTime && styles.chipOn]}
            onPress={() => onChange({ plannedTime: null })}>
            <Text style={[styles.chipText, !item.plannedTime && styles.chipTextOn]}>미정</Text>
          </Pressable>
          {TIMES.map((t) => (
            <Pressable
              key={t}
              style={[styles.chip, item.plannedTime === t && styles.chipOn]}
              onPress={() => onChange({ plannedTime: t })}>
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
