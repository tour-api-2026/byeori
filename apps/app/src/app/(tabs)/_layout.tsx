import { Tabs } from 'expo-router';
import { TabBar } from '@/components/TabBar';

/**
 * 뒤로가기는 홈으로 간다.
 *
 * 탭 네비게이터의 backBehavior 기본값이 'firstRoute' 라서, 안드로이드 뒤로가기를 누르면
 * **선언 순서상 첫 화면**으로 간다. 아래 순서는 탭바에 보이는 좌→우 순서라 map 이 첫
 * 줄인데, 그 탓에 어느 탭에서든 뒤로가기를 누르면 지도가 떴다.
 *
 * 탭바의 순서는 TabBar 안의 ORDER 가 정하므로 선언 순서를 바꿔도 화면은 그대로지만,
 * 그러면 "왜 이 순서인가"가 암묵적인 규칙이 된다. 시작 화면을 이름으로 못 박는다.
 */
export const unstable_settings = { initialRouteName: 'index' };

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{ headerShown: false }}
      backBehavior="initialRoute"
      tabBar={(props) => <TabBar {...props} />}
    >
      {/* 좌→우: 내 주변 · 루트 탐색 · [홈] · 내 루트 · 마이 (가운데 돌출) */}
      <Tabs.Screen name="map" options={{ title: '내 주변' }} />
      <Tabs.Screen name="explore" options={{ title: '루트 탐색' }} />
      <Tabs.Screen name="index" options={{ title: '홈' }} />
      <Tabs.Screen name="routes" options={{ title: '내 루트' }} />
      <Tabs.Screen name="profile" options={{ title: '마이' }} />
      {/* 검색은 홈에서 진입 — 탭바에서 숨김(라우트는 유지) */}
      <Tabs.Screen name="search" options={{ href: null }} />
    </Tabs>
  );
}
