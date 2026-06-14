import { Ionicons, MaterialCommunityIcons } from '@expo/vector-icons';
import { Tabs } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { colors, fonts } from '@/lib/theme';

export default function TabsLayout() {
  const insets = useSafeAreaInsets();
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.textFaint,
        tabBarStyle: {
          backgroundColor: colors.white,
          borderTopColor: colors.border,
          height: 60 + insets.bottom,
          paddingBottom: 8 + insets.bottom,
          paddingTop: 8,
        },
        tabBarLabelStyle: { fontSize: 11, fontFamily: fonts.medium },
      }}>
      <Tabs.Screen name="index" options={{ title: '홈', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'home' : 'home-outline'} size={size} color={color} /> }} />
      <Tabs.Screen name="map" options={{ title: '내 주변', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'location' : 'location-outline'} size={size} color={color} /> }} />
      <Tabs.Screen name="routes" options={{ title: '여행 루트', tabBarIcon: ({ color, size }) => <MaterialCommunityIcons name="map-marker-path" size={size} color={color} /> }} />
      <Tabs.Screen name="profile" options={{ title: '마이', tabBarIcon: ({ color, size, focused }) => <Ionicons name={focused ? 'person' : 'person-outline'} size={size} color={color} /> }} />
      {/* 검색은 홈/발견에 통합 — 탭바에서 숨김(라우트는 유지) */}
      <Tabs.Screen name="search" options={{ href: null }} />
    </Tabs>
  );
}
