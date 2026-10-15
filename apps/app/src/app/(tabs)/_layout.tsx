import { Tabs } from 'expo-router';
import { TabBar } from '@/components/TabBar';

/**
 * 탭에서 뒤로가기를 누르면 앱이 닫힌다. 탭 사이를 되짚지 않는다.
 *
 * backBehavior 기본값은 'firstRoute' 로, **선언 순서상 첫 화면**으로 간다. 아래 순서는
 * 탭바에 보이는 좌→우 순서라 map 이 첫 줄인데, 그 탓에 어느 탭에서든 뒤로가기가 지도를
 * 띄웠다. 'initialRoute'(홈으로 모은다)도 써 봤지만, 바텀 네비게이션은 탭 사이에 앞뒤가
 * 없는 자리다 — 홈을 한 번 더 거쳐 나가는 것은 눌러 보면 군더더기다.
 *
 * 'none' 은 탭 전환을 history 에 쌓지 않는다. 그러면 뒤로가기가 이 네비게이터에서
 * 처리되지 않고(history 가 한 칸이라 GO_BACK 이 null) 위로 올라가, (tabs)가 첫 화면인
 * 루트 Stack 에서도 꺼낼 것이 없어 앱이 닫힌다.
 *
 * initialRouteName 은 그대로 둔다. 뒤로가기와는 상관이 없고, 앱을 처음 켤 때 어느 탭을
 * 보여줄지를 정한다 — 이것이 없으면 선언 순서상 첫 줄인 지도가 된다.
 */
export const unstable_settings = { initialRouteName: 'index' };

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{ headerShown: false }}
      backBehavior="none"
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
