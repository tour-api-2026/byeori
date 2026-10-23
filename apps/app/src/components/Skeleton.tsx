import { useEffect, useRef } from 'react';
import { Animated, Easing, StyleSheet, View, type ViewStyle } from 'react-native';

import { colors } from '@/lib/theme';

/**
 * 데이터가 오기 전 자리를 지키는 회색 막대.
 *
 * 화면 전체를 스피너로 덮는 대신 들어올 내용의 모양을 미리 그린다. 스피너는 "뭔가
 * 돌고 있다"만 알려주고 레이아웃을 전혀 안 보여줘서, 데이터가 도착하는 순간 화면이
 * 통째로 바뀐다.
 *
 * **이미 아는 값에는 쓰지 않는다.** 목록에서 들어온 장소는 이름·이미지·주소를 이미
 * 갖고 있으므로 그냥 그린다(queries.ts 의 placeholderData). 회색 막대가 번쩍였다
 * 글자로 바뀌는 것은 처음부터 글자를 보여주는 것보다 나쁘다. 스켈레톤은 정말 모르는
 * 값에만 쓴다 — 상세에서는 한국관광공사 실시간 조회로 오는 것들이다.
 */
export function Skeleton({ width, height = 12, radius = 6, style }: {
  width?: number | `${number}%`;
  height?: number;
  radius?: number;
  style?: ViewStyle;
}) {
  // 깜빡임은 한 번만 만들어 재사용한다. 막대마다 새 Animated.Value 를 돌리면
  // 소개글 5줄짜리에서 애니메이션이 다섯 개 돈다.
  const pulse = usePulse();

  return (
    <Animated.View
      style={[
        styles.bar,
        { height, borderRadius: radius, opacity: pulse },
        width !== undefined && { width },
        style,
      ]}
    />
  );
}

/**
 * 글 묶음. 마지막 줄은 짧게 둔다 — 문단은 마지막 줄이 꽉 차지 않으므로,
 * 전부 같은 길이면 글이 아니라 표처럼 보인다.
 */
export function SkeletonText({ lines = 4, lastWidth = '60%', gap = 8 }: {
  lines?: number;
  lastWidth?: `${number}%`;
  gap?: number;
}) {
  return (
    <View style={{ gap }}>
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} height={13} width={i === lines - 1 ? lastWidth : '100%'} />
      ))}
    </View>
  );
}

function usePulse() {
  const v = useRef(new Animated.Value(0.45)).current;

  useEffect(() => {
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(v, { toValue: 1, duration: 650, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
        Animated.timing(v, { toValue: 0.45, duration: 650, easing: Easing.inOut(Easing.quad), useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [v]);

  return v;
}

const styles = StyleSheet.create({
  bar: { backgroundColor: colors.bgSoft, width: '100%' },
});
