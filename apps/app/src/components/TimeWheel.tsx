import { useEffect, useRef } from 'react';
import { Platform, ScrollView, StyleSheet, Text, View } from 'react-native';
import { colors, fonts, radius } from '@/lib/theme';

const ITEM_H = 40;
const VISIBLE = 3;                       // 가운데 한 칸 + 위아래로 한 칸씩 엿보기
const PAD = ITEM_H * ((VISIBLE - 1) / 2); // 첫 칸·끝 칸도 가운데로 올 수 있게

const HOURS = Array.from({ length: 24 }, (_, i) => i);
const MINUTE_STEP = 5;

/**
 * 분 후보. 5분 간격이 기본이고, 지금 값이 그 사이에 있으면(직접 쳐 넣은 14:07 같은 값)
 * 그 숫자를 끼워 넣는다. 안 끼우면 휠이 가진 적 없는 값을 가리키게 되고, 손대지 않았는데
 * 값이 바뀐 것처럼 보인다.
 */
function minuteList(current: number | null) {
  const base = Array.from({ length: 60 / MINUTE_STEP }, (_, i) => i * MINUTE_STEP);
  if (current == null || base.includes(current)) return base;
  return [...base, current].sort((a, b) => a - b);
}

const pad2 = (n: number) => String(n).padStart(2, '0');

/**
 * 시·분을 굴려서 고르는 휠.
 *
 * 전에는 30분 간격 칩 28개가 가로로 늘어서 있었다. 14:30 하나를 고르려고 옆으로 한참
 * 긁어야 했고 14:05 는 아예 고를 수 없었다. 휠은 자리를 덜 쓰면서 모든 분을 다룬다.
 * 아는 시각을 넣을 때는 옆의 입력칸이 더 빠르므로 그쪽은 남겨 둔다.
 *
 * 시간 피커 라이브러리를 쓰지 않는다. 패키지가 늘면 package.json 이 바뀌어 fingerprint 가
 * 달라지고, 그러면 이미 깔린 앱에 OTA 가 닿지 않는다(CONTRIBUTING).
 *
 * 스냅을 플랫폼 기능에 맡기지 않는다. RN-web 은 snapToInterval 을 무시하고 pagingEnabled
 * 만 CSS 스크롤 스냅으로 바꾸는데, 그건 '칸의 위쪽'을 기준으로 붙어서 가운데 정렬과
 * 어긋난다. 그래서 멈춘 뒤 직접 제자리로 보낸다 — 두 플랫폼이 같은 코드로 돈다.
 */
export function TimeWheel({
  value, onChange,
}: {
  /** 'HH:MM' 또는 null(미정). */
  value: string | null;
  onChange: (next: string) => void;
}) {
  const [h, m] = value && /^\d{2}:\d{2}$/.test(value)
    ? [Number(value.slice(0, 2)), Number(value.slice(3))]
    : [9, 0];
  const minutes = minuteList(value ? m : null);

  return (
    <View style={styles.wrap}>
      {/* 가운데 칸 표시. 휠 뒤에 깔아 두고 터치는 통과시킨다. */}
      <View pointerEvents="none" style={styles.marker} />
      <Column values={HOURS} index={HOURS.indexOf(h)} onPick={(i) => onChange(`${pad2(HOURS[i])}:${pad2(m)}`)} />
      <Text style={styles.colon}>:</Text>
      <Column values={minutes} index={Math.max(0, minutes.indexOf(m))} onPick={(i) => onChange(`${pad2(h)}:${pad2(minutes[i])}`)} />
    </View>
  );
}

function Column({
  values, index, onPick,
}: {
  values: number[];
  index: number;
  onPick: (index: number) => void;
}) {
  const ref = useRef<ScrollView>(null);
  const settle = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 밖에서 값이 바뀌어 우리가 스스로 굴릴 때는 onChange 를 부르지 않는다(되먹임 방지).
  const silent = useRef(false);

  useEffect(() => {
    silent.current = true;
    ref.current?.scrollTo({ y: index * ITEM_H, animated: false });
    const t = setTimeout(() => { silent.current = false; }, 120);
    return () => clearTimeout(t);
  }, [index]);

  return (
    <ScrollView
      ref={ref}
      style={styles.col}
      showsVerticalScrollIndicator={false}
      scrollEventThrottle={16}
      // 네이티브에서는 관성이 칸에 붙어 손맛이 산다. 웹은 아래 settle 이 맡는다.
      {...(Platform.OS === 'web' ? {} : { snapToInterval: ITEM_H, decelerationRate: 'fast' as const })}
      contentContainerStyle={{ paddingVertical: PAD }}
      onScroll={(e) => {
        const y = e.nativeEvent.contentOffset.y;
        if (settle.current) clearTimeout(settle.current);
        // 멈춘 뒤에 제자리로 붙이고 값을 알린다. 구르는 내내 알리면 지나가는 값마다 저장된다.
        settle.current = setTimeout(() => {
          const i = Math.max(0, Math.min(values.length - 1, Math.round(y / ITEM_H)));
          ref.current?.scrollTo({ y: i * ITEM_H, animated: true });
          if (!silent.current && i !== index) onPick(i);
        }, 220);
      }}>
      {values.map((v, i) => (
        <View key={v} style={styles.item}>
          <Text style={[styles.itemText, i === index && styles.itemTextOn]}>{pad2(v)}</Text>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  wrap: {
    height: ITEM_H * VISIBLE,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 4,
    // 내용만큼만 차지하게 한다. 그래야 가운데 띠가 휠을 감싼다.
    alignSelf: 'center',
    // 열에 높이를 주지 않으면 alignItems:'center' 탓에 늘어나지 않고 내용만큼(24칸) 커져서
    // 시트 전체로 흘러넘친다. 바깥에서도 한 번 더 잘라 둔다.
    overflow: 'hidden',
  },
  marker: {
    position: 'absolute',
    left: -12, right: -12,
    top: ITEM_H, height: ITEM_H,
    backgroundColor: colors.bgSoft,
    borderRadius: radius.sm,
  },
  // 높이를 명시해야 ScrollView 가 그 안에서 구른다.
  // flexGrow/Shrink 를 끄지 않으면 가로 줄에서 늘어나 두 칸이 양끝으로 벌어진다.
  col: { width: 64, height: ITEM_H * VISIBLE, flexGrow: 0, flexShrink: 0 },
  item: { height: ITEM_H, alignItems: 'center', justifyContent: 'center' },
  itemText: { fontSize: 17, color: colors.textFaint, fontFamily: fonts.medium, fontWeight: '500' },
  itemTextOn: { fontSize: 19, color: colors.text, fontFamily: fonts.bold, fontWeight: '800' },
  colon: { fontSize: 18, color: colors.textFaint, fontFamily: fonts.bold, fontWeight: '800', marginBottom: 2 },
});
