import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useAutoUpdate } from '@/hooks/use-auto-update';
import { applyGlobalFont } from '@/lib/font';
import { useAuthStore } from '@/lib/store/authStore';
import { colors, fonts } from '@/lib/theme';

applyGlobalFont();

// 소셜 로그인 팝업이 받은 인가 코드를 원래 창으로 넘기고 닫는다.
// 웹은 redirect_uri 가 오리진(루트)이라 팝업이 로그인 화면이 아니라 홈으로 돌아온다.
// 이 호출이 로그인 화면에만 있을 때는 팝업이 코드를 쥔 채 홈을 띄우고 멈춰,
// 카카오 로그인이 서버(/auth/social)까지 한 번도 닿지 못했다. 어느 화면으로
// 돌아오든 처리되도록 루트에서 부른다(해당 없으면 아무 일도 하지 않는다).
WebBrowser.maybeCompleteAuthSession();

export default function RootLayout() {
  useAutoUpdate();
  const [client] = useState(() => new QueryClient());
  const [loaded] = useFonts({
    'Pretendard-Regular': require('../../assets/fonts/Pretendard-Regular.ttf'),
    'Pretendard-Medium': require('../../assets/fonts/Pretendard-Medium.ttf'),
    'Pretendard-SemiBold': require('../../assets/fonts/Pretendard-SemiBold.ttf'),
    'Pretendard-Bold': require('../../assets/fonts/Pretendard-Bold.ttf'),
  });

  // 앱 시작 시 저장된 세션 복원
  const hydrated = useAuthStore((s) => s.hydrated);
  const hydrate = useAuthStore((s) => s.hydrate);
  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (loaded) applyGlobalFont();
  }, [loaded]);

  // 폰트 + 세션 복원이 모두 끝날 때까지 렌더 게이트
  if (!loaded || !hydrated) return <View style={{ flex: 1, backgroundColor: colors.bg }} />;

  return (
    <QueryClientProvider client={client}>
      <SafeAreaProvider>
        <StatusBar style="dark" />
        <Stack
          screenOptions={{
            headerTintColor: colors.text,
            headerStyle: { backgroundColor: colors.bg },
            headerTitleStyle: { fontFamily: fonts.bold, color: colors.text },
            headerShadowVisible: false,
            contentStyle: { backgroundColor: colors.bg },
          }}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="venue/[id]" options={{ headerShown: false }} />
          <Stack.Screen name="performances/[id]" options={{ headerShown: false }} />
          <Stack.Screen name="login" options={{ headerShown: false, presentation: 'modal' }} />
          <Stack.Screen name="email-login" options={{ headerShown: false }} />
          <Stack.Screen name="onboarding" options={{ headerShown: false }} />
          <Stack.Screen name="bookmarks" options={{ title: '즐겨찾기' }} />
          <Stack.Screen name="performances/traditional" options={{ title: '전통 테마 행사' }} />
          <Stack.Screen name="my/blocked" options={{ title: '차단한 사용자' }} />
          <Stack.Screen name="my/service-info" options={{ title: '서비스 정보' }} />
          <Stack.Screen name="my/profile" options={{ title: '프로필 수정' }} />
        </Stack>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
