import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radius } from '@/lib/theme';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const pad = (n: number) => String(n).padStart(2, '0');
const iso = (y: number, m: number, d: number) => `${y}-${pad(m + 1)}-${pad(d)}`;

export interface CalendarProps {
  /** 'YYYY-MM-DD' 선택 구간 시작(편집 화면에선 선택일과 동일하게 사용) */
  rangeStart?: string | null;
  rangeEnd?: string | null;
  /** 점으로 표시할 날짜들 (일정이 있는 날 등) */
  marked?: string[];
  /** 선택 가능 범위 (이 밖은 비활성) */
  min?: string;
  max?: string;
  /** 처음 보여줄 달 'YYYY-MM' (기본: rangeStart 또는 오늘) */
  initialMonth?: string;
  onSelectDate: (date: string) => void;
}

export function Calendar({ rangeStart, rangeEnd, marked = [], min, max, initialMonth, onSelectDate }: CalendarProps) {
  const seed = initialMonth ?? rangeStart ?? iso(new Date().getFullYear(), new Date().getMonth(), new Date().getDate());
  const [sy, sm] = seed.split('-').map(Number);
  const [cursor, setCursor] = useState({ year: sy, month: sm - 1 }); // month: 0-based

  const first = new Date(cursor.year, cursor.month, 1);
  const startWeekday = first.getDay();
  const daysInMonth = new Date(cursor.year, cursor.month + 1, 0).getDate();
  const markedSet = new Set(marked);

  const cells: (number | null)[] = [];
  for (let i = 0; i < startWeekday; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const move = (delta: number) => {
    const m = cursor.month + delta;
    setCursor({ year: cursor.year + Math.floor(m / 12), month: ((m % 12) + 12) % 12 });
  };

  const inRange = (date: string) =>
    rangeStart && rangeEnd && date >= rangeStart && date <= rangeEnd;
  const isEndpoint = (date: string) => date === rangeStart || date === rangeEnd;
  const disabled = (date: string) => (min && date < min) || (max && date > max);

  return (
    <View style={styles.wrap}>
      <View style={styles.header}>
        <Pressable hitSlop={10} onPress={() => move(-1)}><Ionicons name="chevron-back" size={20} color={colors.text} /></Pressable>
        <Text style={styles.title}>{cursor.year}년 {cursor.month + 1}월</Text>
        <Pressable hitSlop={10} onPress={() => move(1)}><Ionicons name="chevron-forward" size={20} color={colors.text} /></Pressable>
      </View>
      <View style={styles.weekRow}>
        {WEEKDAYS.map((w, i) => (
          <Text key={w} style={[styles.weekday, i === 0 && { color: colors.hanbok }, i === 6 && { color: colors.primary }]}>{w}</Text>
        ))}
      </View>
      <View style={styles.grid}>
        {cells.map((d, i) => {
          if (d == null) return <View key={`e${i}`} style={styles.cell} />;
          const date = iso(cursor.year, cursor.month, d);
          const end = isEndpoint(date);
          const within = inRange(date) && !end;
          const off = !!disabled(date);
          return (
            <Pressable
              key={date}
              style={styles.cell}
              disabled={off}
              onPress={() => onSelectDate(date)}>
              <View style={[styles.day, within && styles.dayWithin, end && styles.dayEnd]}>
                <Text style={[styles.dayText, end && styles.dayTextEnd, off && styles.dayTextOff]}>{d}</Text>
              </View>
              {markedSet.has(date) && !end && <View style={styles.dot} />}
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 12, borderWidth: 1, borderColor: colors.border },
  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 6, marginBottom: 10 },
  title: { fontSize: 15, fontWeight: '800', color: colors.text },
  weekRow: { flexDirection: 'row' },
  weekday: { flex: 1, textAlign: 'center', fontSize: 12, fontWeight: '700', color: colors.textSub, marginBottom: 4 },
  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  cell: { width: `${100 / 7}%`, aspectRatio: 1, alignItems: 'center', justifyContent: 'center' },
  day: { width: 34, height: 34, borderRadius: 17, alignItems: 'center', justifyContent: 'center' },
  dayWithin: { backgroundColor: colors.primarySoft, borderRadius: 8 },
  dayEnd: { backgroundColor: colors.primary },
  dayText: { fontSize: 14, color: colors.text, fontWeight: '600' },
  dayTextEnd: { color: colors.white, fontWeight: '800' },
  dayTextOff: { color: colors.textFaint, opacity: 0.4 },
  dot: { position: 'absolute', bottom: 4, width: 5, height: 5, borderRadius: 3, backgroundColor: colors.hanbok },
});
