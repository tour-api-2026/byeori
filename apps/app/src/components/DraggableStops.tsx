import { Ionicons } from '@expo/vector-icons';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Animated, PanResponder, Pressable, StyleSheet, Text, View } from 'react-native';
import type { ItineraryItem } from '@/lib/api/itineraries';
import { colors, fonts, radius } from '@/lib/theme';

/**
 * 줄 높이. 끌어서 몇 칸 움직였는지 dy / ROW_H 로 센다.
 *
 * 줄마다 높이를 재서 계산할 수도 있지만, 메모가 있는 줄과 없는 줄이 섞이면 경계가
 * 들쭉날쭉해져 "한 칸만 내렸는데 두 칸 내려가는" 일이 생긴다. 높이를 고정하고 글자를
 * 한 줄로 자르는 쪽이 끌 때 손에 붙는다.
 */
const ROW_H = 72;

/** 행이 제스처를 알려 오는 통로. 부모가 늘 최신 것으로 갈아 끼운다. */
type DragControl = {
  grant: (id: number) => void;
  move: (dy: number) => void;
  release: () => void;
  cancel: () => void;
};

/**
 * 끌어서 순서를 바꾸는 하루치 장소 목록.
 *
 * 제스처 라이브러리를 쓰지 않는다. 새 패키지를 넣으면 package.json 이 바뀌어
 * fingerprint 가 달라지고, 그러면 **이미 깔린 앱에 OTA 가 닿지 않는다**(CONTRIBUTING).
 * RN 내장 PanResponder 로 충분하다 — 같은 이유로 BottomSheet 도 이렇게 만들었다.
 *
 * 손잡이(≡)에서만 끌린다. 줄 전체가 끌리면 누르려다 조금만 움직여도 끌기로 잡혀
 * 편집 시트가 안 열린다.
 */
export function DraggableStops({
  items, pending, onPressItem, onReorder,
}: {
  /** 그날의 장소. 서버가 준 순서 그대로. */
  items: ItineraryItem[];
  pending: boolean;
  onPressItem: (itemId: number) => void;
  /** 놓았을 때 새 순서의 id 목록. 자리가 바뀌지 않았으면 부르지 않는다. */
  onReorder: (itemIds: number[]) => void;
}) {
  // 끌 때 바로 따라오도록 화면이 들고 있는 순서. 서버 응답이 오면 그쪽으로 맞춘다.
  const [list, setList] = useState(items);
  useEffect(() => { setList(items); }, [items]);

  const [dragId, setDragId] = useState<number | null>(null);
  const [toIndex, setToIndex] = useState(0);
  const dragY = useRef(new Animated.Value(0)).current;

  /**
   * 끌기 중에 바뀌는 값은 전부 ref 다.
   *
   * 제스처가 시작되면 responder 는 그때 붙잡은 콜백을 끝까지 쓴다. 중간에 리렌더가
   * 일어나도 그 콜백 안에서 보이는 state 는 시작할 때 값 그대로다. 놓는 순간의 판단을
   * state 로 하면 늘 "제자리"로 읽혀 아무 일도 일어나지 않는다.
   */
  const fromRef = useRef(0);
  const toRef = useRef(0);
  const listRef = useRef(list);
  listRef.current = list;

  const ctl = useRef<DragControl>({ grant: () => {}, move: () => {}, release: () => {}, cancel: () => {} });
  ctl.current = {
    grant: (id) => {
      fromRef.current = listRef.current.findIndex((x) => x.id === id);
      toRef.current = fromRef.current;
      setToIndex(fromRef.current);
      setDragId(id);
      dragY.setValue(0);
    },
    move: (dy) => {
      dragY.setValue(dy);
      const last = listRef.current.length - 1;
      const next = Math.max(0, Math.min(last, fromRef.current + Math.round(dy / ROW_H)));
      toRef.current = next;
      setToIndex(next);
    },
    release: () => {
      const from = fromRef.current;
      const to = toRef.current;
      setDragId(null);
      dragY.setValue(0);
      if (from === to) return;
      const next = [...listRef.current];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      setList(next);
      onReorder(next.map((x) => x.id));
    },
    // 전화가 오는 등으로 제스처를 뺏기면 끌던 걸 제자리에 둔다.
    cancel: () => { setDragId(null); dragY.setValue(0); },
  };

  /** 끌고 있는 줄이 비켜 간 만큼 다른 줄들이 밀린다. */
  const shiftOf = (i: number) => {
    if (dragId === null) return 0;
    const from = fromRef.current;
    if (i > from && i <= toIndex) return -ROW_H;
    if (i < from && i >= toIndex) return ROW_H;
    return 0;
  };

  return (
    <View>
      {list.map((it, i) => (
        <StopRow
          key={it.id}
          item={it}
          index={i}
          total={list.length}
          dragging={it.id === dragId}
          shift={shiftOf(i)}
          dragY={dragY}
          ctl={ctl}
          onPress={() => onPressItem(it.id)}
        />
      ))}
      {pending ? <Text style={styles.saving}>순서 저장 중…</Text> : null}
    </View>
  );
}

/**
 * 한 줄. PanResponder 를 **useMemo 로 딱 한 번** 만든다.
 *
 * 렌더마다 PanResponder.create 를 부르면 끌고 있는 도중에 새 인스턴스로 갈려서
 * 누적 거리(dy)가 0부터 다시 시작한다. 실제로 그랬다 — 두 칸을 끌어도 dy 가
 * 12→24→36→12 로 되감겨 36px 를 넘지 못했고, 놓아도 제자리로 읽혔다.
 *
 * key 가 item.id 라 이 인스턴스는 한 장소에 고정된다. 그래서 처음 받은 id 를
 * 그대로 써도 어긋나지 않는다.
 */
function StopRow({
  item, index, total, dragging, shift, dragY, ctl, onPress,
}: {
  item: ItineraryItem;
  index: number;
  total: number;
  dragging: boolean;
  shift: number;
  dragY: Animated.Value;
  ctl: React.MutableRefObject<DragControl>;
  onPress: () => void;
}) {
  const pan = useMemo(
    () =>
      PanResponder.create({
        // react-native-web 은 onStartShouldSet 없이 move 만으로는 responder 를 넘기지 않는다.
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: (_, g) => Math.abs(g.dy) > 2,
        onPanResponderGrant: () => ctl.current.grant(item.id),
        onPanResponderMove: (_, g) => ctl.current.move(g.dy),
        onPanResponderRelease: () => ctl.current.release(),
        onPanResponderTerminate: () => ctl.current.cancel(),
      }),
    [], // eslint-disable-line react-hooks/exhaustive-deps
  );

  return (
    <Animated.View
      style={[
        styles.row,
        dragging
          ? { transform: [{ translateY: dragY }], zIndex: 2, elevation: 2, opacity: 0.95 }
          : { transform: [{ translateY: shift }] },
      ]}>
      <Pressable style={styles.press} disabled={dragging} onPress={onPress}>
        <View style={styles.rail}>
          {index > 0 && <View style={[styles.rLine, styles.rTop]} />}
          {index < total - 1 && <View style={[styles.rLine, styles.rBot]} />}
          <View style={styles.num}><Text style={styles.numText}>{index + 1}</Text></View>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.name} numberOfLines={1}>{item.name}</Text>
          <Text style={styles.meta} numberOfLines={1}>
            {item.targetType === 'PERFORMANCE' ? '행사' : '장소'}{item.plannedTime ? ` · ${item.plannedTime}` : ''}
          </Text>
          {/* AI 루트는 추천 이유를 메모로 저장한다 */}
          {item.memo ? <Text style={styles.memo} numberOfLines={1}>{item.memo}</Text> : null}
        </View>
      </Pressable>
      {/* 손잡이 — 여기서만 끌린다 */}
      <View style={styles.handle} {...pan.panHandlers}>
        <Ionicons name="reorder-three" size={22} color={dragging ? colors.primary : colors.textFaint} />
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  row: { height: ROW_H, flexDirection: 'row', alignItems: 'center', backgroundColor: colors.white },
  press: { flex: 1, flexDirection: 'row', alignItems: 'center', gap: 12, alignSelf: 'stretch' },
  rail: { width: 26, alignItems: 'center', alignSelf: 'stretch', justifyContent: 'center' },
  rLine: { position: 'absolute', width: 2, backgroundColor: colors.border, left: 12 },
  rTop: { top: 0, height: '50%' },
  rBot: { bottom: 0, height: '50%' },
  num: { width: 26, height: 26, borderRadius: 13, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  numText: { color: colors.white, fontSize: 12, fontFamily: fonts.bold, fontWeight: '800' },
  name: { fontSize: 15, fontFamily: fonts.semibold, fontWeight: '700', color: colors.text },
  meta: { fontSize: 12, color: colors.textFaint, marginTop: 2 },
  memo: { fontSize: 12, color: colors.textSub, marginTop: 2 },
  // 손잡이는 작으면 못 잡는다. 줄 높이만큼 세로로 넓게 둔다.
  handle: { width: 44, alignSelf: 'stretch', alignItems: 'center', justifyContent: 'center', borderRadius: radius.sm },
  saving: { fontSize: 12, color: colors.textFaint, textAlign: 'center', paddingVertical: 8 },
});
