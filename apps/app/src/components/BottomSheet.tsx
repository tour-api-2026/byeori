import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated, Dimensions, PanResponder, Platform, StyleSheet, View, type ViewStyle,
} from 'react-native';
import { colors, radius, shadow } from '@/lib/theme';

export type SheetSnap = 'peek' | 'full';

/**
 * 아래에서 올라오는 시트. 손잡이를 끌어 '접힘 ↔ 펼침' 두 단계로 움직인다.
 *
 * 라이브러리(@gorhom/bottom-sheet)를 쓰지 않은 이유: 그쪽이 요구하는 reanimated 4.7 은
 * react-native 0.86+ 를 전제하는데 이 앱은 0.81.5(Expo SDK 54)다. peer 를 강제로 무시하면
 * 릴리스 빌드에서만 터지는 종류의 문제가 생긴다 — 이 저장소엔 이미 그런 상처가 있다
 * (app.config.js 의 kotlinVersion 주석).
 *
 * 그래서 RN 내장 Animated + PanResponder 로 만든다. 제스처가 JS 스레드에서 돌지만
 * 스냅이 둘뿐이고 목록도 수십 건이라 체감 차이는 작다. 나중에 SDK 를 올리면 이 파일만
 * 갈아끼우면 되도록 바깥 인터페이스를 좁게 뒀다.
 */
export function BottomSheet({
  visible,
  snap,
  onSnapChange,
  peekHeight,
  topInset = 0,
  children,
  style,
}: {
  visible: boolean;
  snap: SheetSnap;
  onSnapChange: (s: SheetSnap) => void;
  /** 접혔을 때 보이는 높이. */
  peekHeight: number;
  /** 펼쳤을 때 위쪽으로 남겨둘 여백(상태바·검색창). */
  topInset?: number;
  children: React.ReactNode;
  style?: ViewStyle;
}) {
  const [screenH, setScreenH] = useState(() => Dimensions.get('window').height);
  useEffect(() => {
    const sub = Dimensions.addEventListener('change', ({ window }) => setScreenH(window.height));
    return () => sub.remove();
  }, []);

  const fullHeight = Math.max(peekHeight, screenH - topInset);
  // translateY: 0 이면 펼침, (fullHeight - peekHeight) 면 접힘.
  const collapsed = fullHeight - peekHeight;
  const y = useRef(new Animated.Value(collapsed)).current;
  const current = useRef(collapsed);

  useEffect(() => {
    const id = y.addListener(({ value }) => { current.current = value; });
    return () => y.removeListener(id);
  }, [y]);

  const slideTo = (to: SheetSnap) => {
    Animated.spring(y, {
      toValue: to === 'full' ? 0 : collapsed,
      useNativeDriver: true,   // transform 이라 UI 스레드에서 돈다
      bounciness: 0,
      speed: 14,
    }).start();
  };

  useEffect(() => { slideTo(snap); }, [snap, collapsed]);  // eslint-disable-line react-hooks/exhaustive-deps

  const pan = useMemo(
    () =>
      PanResponder.create({
        // 손잡이는 끌기 전용이라 닿는 순간 잡는다.
        // react-native-web 은 onStartShouldSet 없이 move 만으로는 responder 를 넘기지 않는다.
        onStartShouldSetPanResponder: () => true,
        onMoveShouldSetPanResponder: (_, g) => Math.abs(g.dy) > 2,
        onPanResponderMove: (_, g) => {
          const next = Math.min(collapsed, Math.max(0, current.current + g.dy));
          y.setValue(next);
        },
        onPanResponderRelease: (_, g) => {
          // 속도가 붙었으면 그 방향으로, 아니면 가까운 쪽으로 붙인다.
          const fast = Math.abs(g.vy) > 0.5;
          const next: SheetSnap = fast
            ? (g.vy < 0 ? 'full' : 'peek')
            : (current.current < collapsed / 2 ? 'full' : 'peek');
          onSnapChange(next);
          slideTo(next);
        },
      }),
    [collapsed], // eslint-disable-line react-hooks/exhaustive-deps
  );

  if (!visible) return null;

  return (
    <Animated.View
      style={[
        styles.sheet,
        { height: fullHeight, transform: [{ translateY: y }] },
        style,
      ]}>
      {/* 손잡이 — 여기서만 끌린다. 목록은 세로 스크롤을 그대로 쓴다. */}
      <View style={styles.handleArea} {...pan.panHandlers}>
        <View style={styles.handle} />
      </View>
      <View style={{ flex: 1 }}>{children}</View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: colors.white,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    ...(Platform.OS === 'web' ? { boxShadow: '0 -4px 20px rgba(26,26,31,0.12)' } : shadow.card),
  },
  handleArea: { alignItems: 'center', paddingTop: 10, paddingBottom: 8 },
  handle: { width: 44, height: 4, borderRadius: 2, backgroundColor: colors.border },
});
