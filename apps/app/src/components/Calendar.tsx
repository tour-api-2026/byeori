import { Ionicons } from '@expo/vector-icons';
import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, fonts, radius } from '@/lib/theme';

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);
const pad = (n: number) => String(n).padStart(2, '0');
const iso = (y: number, m: number, d: number) => `${y}-${pad(m + 1)}-${pad(d)}`;

/** 동그라미 지름. 띠 높이도 이 값을 따라가야 둘이 한 줄로 이어져 보인다. */
const DAY = 34;

export interface CalendarProps {
  /** 'YYYY-MM-DD' 선택 구간 시작(편집 화면에선 선택일과 동일하게 사용) */
  rangeStart?: string | null;
  rangeEnd?: string | null;
  /** 점으로 표시할 날짜들 (일정이 있는 날 등) */
  marked?: string[];
  /** 점이 무슨 뜻인지 아래에 적는다. marked 가 있을 때만 보인다. */
  markedLabel?: string;
  /** 선택 가능 범위 (이 밖은 비활성) */
  min?: string;
  max?: string;
  /** 처음 보여줄 달 'YYYY-MM' (기본: rangeStart 또는 오늘) */
  initialMonth?: string;
  onSelectDate: (date: string) => void;
}

export function Calendar({
  rangeStart, rangeEnd, marked = [], markedLabel = '일정이 있는 날',
  min, max, initialMonth, onSelectDate,
}: CalendarProps) {
  const today = new Date();
  const todayIso = iso(today.getFullYear(), today.getMonth(), today.getDate());
  const seed = initialMonth ?? rangeStart ?? todayIso;
  const [sy, sm] = seed.split('-').map(Number);
  const [cursor, setCursor] = useState({ year: sy, month: sm - 1 }); // month: 0-based
  // 달을 한 칸씩 넘기는 것 말고, 제목을 눌러 멀리 건너뛰는 길도 둔다.
  const [picking, setPicking] = useState(false);

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

  const hasRange = !!(rangeStart && rangeEnd && rangeStart !== rangeEnd);
  const inRange = (date: string) => hasRange && date >= rangeStart! && date <= rangeEnd!;
  const isEndpoint = (date: string) => date === rangeStart || date === rangeEnd;
  const disabled = (date: string) => (min && date < min) || (max && date > max);

  return (
    <View style={styles.wrap}>
      {/* 제목은 왼쪽, 넘기는 화살표는 오른쪽에 모은다 */}
      <View style={styles.header}>
        <Pressable style={styles.titleBtn} hitSlop={8} onPress={() => setPicking((v) => !v)}>
          <Text style={styles.title}>{cursor.year}년 {cursor.month + 1}월</Text>
          <Ionicons name={picking ? 'chevron-up' : 'chevron-down'} size={14} color={colors.textSub} />
        </Pressable>
        <View style={styles.navs}>
          <Pressable style={styles.nav} hitSlop={8} onPress={() => (picking ? setCursor((c) => ({ ...c, year: c.year - 1 })) : move(-1))}>
            <Ionicons name="chevron-back" size={18} color={colors.textSub} />
          </Pressable>
          <Pressable style={styles.nav} hitSlop={8} onPress={() => (picking ? setCursor((c) => ({ ...c, year: c.year + 1 })) : move(1))}>
            <Ionicons name="chevron-forward" size={18} color={colors.textSub} />
          </Pressable>
        </View>
      </View>

      {picking ? (
        // 제목을 누르면 달 고르기로 바뀐다. 화살표는 이때 연도를 넘긴다.
        <View style={styles.months}>
          {MONTHS.map((m) => {
            const on = m - 1 === cursor.month;
            return (
              <Pressable
                key={m}
                style={[styles.month, on && styles.monthOn]}
                onPress={() => { setCursor((c) => ({ ...c, month: m - 1 })); setPicking(false); }}>
                <Text style={[styles.monthText, on && styles.monthTextOn]}>{m}월</Text>
              </Pressable>
            );
          })}
        </View>
      ) : (
        <>
          <View style={styles.weekRow}>
            {WEEKDAYS.map((w) => <Text key={w} style={styles.weekday}>{w}</Text>)}
          </View>
          <View style={styles.grid}>
            {cells.map((d, i) => {
              if (d == null) return <View key={`e${i}`} style={styles.cell} />;
              const date = iso(cursor.year, cursor.month, d);
              const end = isEndpoint(date);
              const within = inRange(date);
              const off = !!disabled(date);
              const isToday = date === todayIso;
              // 띠는 칸 경계를 넘어 이어져야 한다. 시작일은 오른쪽 절반만, 마지막 날은 왼쪽 절반만 깐다.
              const band = within
                ? date === rangeStart ? styles.bandFromMid
                  : date === rangeEnd ? styles.bandToMid
                  : styles.bandFull
                : null;
              return (
                <Pressable key={date} style={styles.cell} disabled={off} onPress={() => onSelectDate(date)}>
                  {band && <View style={[styles.band, band]} />}
                  <View style={[
                    styles.day,
                    end && styles.dayOn,
                    isToday && !end && styles.dayToday,
                  ]}>
                    <Text style={[
                      styles.dayText,
                      end && styles.dayTextOn,
                      within && !end && styles.dayTextWithin,
                      off && styles.dayTextOff,
                    ]}>{d}</Text>
                  </View>
                  {markedSet.has(date) && !end && <View style={styles.dot} />}
                </Pressable>
              );
            })}
          </View>
          {marked.length > 0 && (
            <View style={styles.legend}>
              <View style={styles.legendDot} />
              <Text style={styles.legendText}>{markedLabel}</Text>
            </View>
          )}
        </>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { backgroundColor: colors.bgCard, borderRadius: radius.lg, padding: 14, borderWidth: 1, borderColor: colors.border },

  header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 },
  titleBtn: { flexDirection: 'row', alignItems: 'center', gap: 5 },
  title: { fontSize: 15, fontFamily: fonts.bold, fontWeight: '800', color: colors.text },
  navs: { flexDirection: 'row', alignItems: 'center', gap: 2 },
  nav: { width: 30, height: 30, alignItems: 'center', justifyContent: 'center' },

  weekRow: { flexDirection: 'row', marginBottom: 2 },
  // 요일은 한 가지 색으로 둔다. 숫자가 주인공이고 머리글이 알록달록하면 시선을 뺏는다.
  weekday: { flex: 1, textAlign: 'center', fontSize: 12, fontFamily: fonts.medium, fontWeight: '500', color: colors.textFaint },

  grid: { flexDirection: 'row', flexWrap: 'wrap' },
  cell: { width: `${100 / 7}%`, aspectRatio: 1, alignItems: 'center', justifyContent: 'center' },

  // 구간 표시. 칸을 가로질러 깔리므로 날짜 사이가 끊기지 않는다.
  band: { position: 'absolute', top: '50%', marginTop: -DAY / 2, height: DAY, backgroundColor: colors.primarySoft },
  bandFull: { left: 0, right: 0 },
  /**
   * 띠의 안쪽 끝은 **깎지 않는다.**
   *
   * 끝을 둥글게(반지름 17) 두면 동그라미 곡선과 어긋나 위아래 모서리에 흰 틈이 생기고,
   * 선이 동그라미에서 뻗어 나오는 게 아니라 가운데에 꽂힌 것처럼 보인다. 이 끝은 동그라미
   * 밑에 깔려 보이지 않는 자리라 깎을 이유도 없다. 네모로 두면 띠 높이(34)와 동그라미
   * 지름(34)이 같아 옆구리에서 딱 맞물린다.
   *
   * 구간 양 끝의 둥근 맛은 동그라미가 내는 것이지 띠가 내는 게 아니다.
   */
  bandFromMid: { left: '50%', right: 0 },
  bandToMid: { left: 0, right: '50%' },

  day: { width: DAY, height: DAY, borderRadius: DAY / 2, alignItems: 'center', justifyContent: 'center' },
  dayOn: { backgroundColor: colors.primary },
  // 오늘은 채우지 않고 테두리만 — 고른 날과 헷갈리면 안 된다.
  dayToday: { borderWidth: 1.5, borderColor: colors.primary },
  dayText: { fontSize: 14, fontFamily: fonts.medium, fontWeight: '500', color: colors.text },
  dayTextOn: { color: colors.white, fontFamily: fonts.bold, fontWeight: '800' },
  dayTextWithin: { color: colors.primary, fontFamily: fonts.semibold, fontWeight: '600' },
  dayTextOff: { color: colors.textFaint, opacity: 0.45 },

  dot: { position: 'absolute', bottom: 6, width: 4, height: 4, borderRadius: 2, backgroundColor: colors.accent },

  legend: { flexDirection: 'row', alignItems: 'center', gap: 6, marginTop: 10, paddingTop: 10, borderTopWidth: 1, borderTopColor: colors.border },
  legendDot: { width: 6, height: 6, borderRadius: 3, backgroundColor: colors.accent },
  legendText: { fontSize: 12, color: colors.textFaint },

  months: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, paddingVertical: 4 },
  month: { width: '22%', paddingVertical: 10, borderRadius: radius.md, borderWidth: 1, borderColor: colors.border, alignItems: 'center' },
  monthOn: { backgroundColor: colors.primary, borderColor: colors.primary },
  monthText: { fontSize: 13, color: colors.textSub, fontFamily: fonts.medium, fontWeight: '500' },
  monthTextOn: { color: colors.white, fontFamily: fonts.bold, fontWeight: '800' },
});
