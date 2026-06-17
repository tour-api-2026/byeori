import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useState } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { colors } from '@/lib/theme';

export default function RootLayout() {
  const [client] = useState(() => new QueryClient());

  return (
    <QueryClientProvider client={client}>
      <SafeAreaProvider>
        <StatusBar style="dark" />
        <Stack screenOptions={{ headerTintColor: colors.text, headerStyle: { backgroundColor: colors.bg }, headerShadowVisible: false, contentStyle: { backgroundColor: colors.bg } }}>
          <Stack.Screen name="(tabs)" options={{ headerShown: false }} />
          <Stack.Screen name="venue/[id]" options={{ headerShown: false }} />
          <Stack.Screen name="login" options={{ title: '로그인', presentation: 'modal' }} />
          <Stack.Screen name="onboarding" options={{ title: '취향 조사' }} />
          <Stack.Screen name="bookmarks" options={{ title: '즐겨찾기' }} />
        </Stack>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
