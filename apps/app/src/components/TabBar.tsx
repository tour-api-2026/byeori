import { Feather, Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import type { BottomTabBarProps } from '@react-navigation/bottom-tabs';
import type { ReactNode } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts } from '@/lib/theme';

// Figma 리디자인 v2 하단 네비게이션 바.
// 5개 항목 · 가운데 '홈'이 파란 원(#3177D5)으로 돌출된 형태.
const ACTIVE = colors.accent; // #3177D5
const INACTIVE = '#80808C'; // Figma 라벨/아이콘 회색

const BAR_H = 56; // 흰 바 높이(세이프에어리어 제외)

// 탭바가 콘텐츠 위에 '떠 있으므로'(position:absolute), 스크롤 화면은 이 높이만큼
// contentContainer 하단 여백을 줘서 마지막 항목이 바에 가리지 않게 한다.
export function useTabBarHeight() {
  const insets = useSafeAreaInsets();
  return BAR_H + insets.bottom;
}

type TabConfig = { label: string; center?: boolean; icon?: (color: string, size: number) => ReactNode };

const TABS: Record<string, TabConfig> = {
  map: { label: '내 주변', icon: (c, s) => <Feather name="map-pin" size={s} color={c} /> },
  explore: { label: '루트 탐색', icon: (c, s) => <MaterialCommunityIcons name="routes" size={s} color={c} /> },
  index: { label: '홈', center: true },
  routes: { label: '내 루트', icon: (c, s) => <Feather name="flag" size={s} color={c} /> },
  profile: { label: '마이', icon: (c, s) => <Feather name="user" size={s} color={c} /> },
};
// 화면 좌→우 배치 순서(가운데 index가 돌출 홈).
const ORDER = ['map', 'explore', 'index', 'routes', 'profile'];

export function TabBar({ state, navigation }: BottomTabBarProps) {
  const insets = useSafeAreaInsets();
  const routeByName = Object.fromEntries(state.routes.map((r) => [r.name, r]));
  const activeName = state.routes[state.index]?.name;

  return (
    // 바가 차지하는 레이아웃 높이는 흰 바(BAR_H)+세이프에어리어까지만 —
    // 가운데 홈 원은 marginTop 음수로 바 위로 '떠서' 겹치므로 콘텐츠(지도) 아래에
    // 빈 여백이 생기지 않는다(네이버 지도처럼 지도가 바 상단까지 꽉 참).
    <View style={[styles.wrap, { height: BAR_H + insets.bottom }]}>
      <View style={styles.bg} />
      <View style={styles.row}>
        {ORDER.map((name) => {
          const route = routeByName[name];
          const cfg = TABS[name];
          if (!route || !cfg) return null;
          const focused = activeName === name;
          const onPress = () => {
            const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
            if (!focused && !event.defaultPrevented) navigation.navigate(route.name);
          };

          if (cfg.center) {
            return (
              <Pressable
                key={name}
                style={styles.centerItem}
                onPress={onPress}
                accessibilityRole="button"
                accessibilityState={{ selected: focused }}
                accessibilityLabel={cfg.label}>
                <View style={styles.centerCircle}>
                  <Ionicons name="home" size={24} color={colors.white} />
                </View>
              </Pressable>
            );
          }

          const color = focused ? ACTIVE : INACTIVE;
          return (
            <Pressable
              key={name}
              style={[styles.item, { paddingBottom: insets.bottom + 8 }]}
              onPress={onPress}
              accessibilityRole="button"
              accessibilityState={{ selected: focused }}
              accessibilityLabel={cfg.label}>
              {cfg.icon?.(color, 22)}
              <Text style={[styles.label, { color }]}>{cfg.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  // 콘텐츠(지도) 위에 떠 있는 바. 지도가 화면 맨 아래까지 꽉 차고 바가 그 위에 겹친다.
  wrap: { position: 'absolute', left: 0, right: 0, bottom: 0, backgroundColor: 'transparent' },
  bg: {
    position: 'absolute',
    left: 0,
    right: 0,
    top: 0,
    bottom: 0,
    backgroundColor: colors.white,
    boxShadow: '0px -4px 4px -2px rgba(13,27,53,0.1)',
  },
  // alignItems:'stretch' — 모든 아이템이 바 전체 높이를 차지해야 가운데 원을 위로 올릴 수 있음.
  row: { ...StyleSheet.absoluteFillObject, flexDirection: 'row', alignItems: 'stretch' },
  item: { flex: 1, alignItems: 'center', justifyContent: 'flex-end', gap: 3 },
  label: { fontSize: 10, fontFamily: fonts.regular, letterSpacing: 0.3, lineHeight: 14 },
  // 가운데 홈: 바 상단보다 살짝(약 6px) 위로 돌출 — Figma 설계(원이 바 위 4px, 43×43)를 반영.
  centerItem: { flex: 1, alignItems: 'center', justifyContent: 'flex-start' },
  centerCircle: {
    width: 48,
    height: 48,
    borderRadius: 24,
    marginTop: -6,
    backgroundColor: colors.accent,
    alignItems: 'center',
    justifyContent: 'center',
    boxShadow: '0px 4px 10px rgba(49,119,213,0.35)',
  },
});
