import { Tabs } from 'expo-router';
import { TabBar } from '@/components/TabBar';

export default function TabsLayout() {
  return (
    <Tabs screenOptions={{ headerShown: false }} tabBar={(props) => <TabBar {...props} />}>
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
