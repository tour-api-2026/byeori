import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { Stack } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { StatusBar } from 'expo-status-bar';
import { useEffect, useState } from 'react';
import { Platform, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useAutoUpdate } from '@/hooks/use-auto-update';
import { applyGlobalFont } from '@/lib/font';
import { useAuthStore } from '@/lib/store/authStore';
import { useT } from '@/lib/i18n';
import { languageLabel } from '@/lib/i18n/labels';
import { useLocaleStore } from '@/lib/store/localeStore';
import { colors, fonts } from '@/lib/theme';
import { WebLanguageSwitcher } from '@/components/WebLanguageSwitcher';
import LanguageScreen from './language';

applyGlobalFont();

// 소셜 로그인 팝업이 받은 인가 코드를 원래 창으로 넘기고 닫는다.
// 웹은 redirect_uri 가 오리진(루트)이라 팝업이 로그인 화면이 아니라 홈으로 돌아온다.
// 이 호출이 로그인 화면에만 있을 때는 팝업이 코드를 쥔 채 홈을 띄우고 멈춰,
// 카카오 로그인이 서버(/auth/social)까지 한 번도 닿지 못했다. 어느 화면으로
// 돌아오든 처리되도록 루트에서 부른다(해당 없으면 아무 일도 하지 않는다).
// localStorage 가 통째로 막힌 환경(사생활 보호 모드, 사이트 데이터 차단, 일부 임베디드
// 웹뷰)에서는 이 호출이 모듈 평가 중에 던진다. 그러면 레이아웃 모듈이 로드되지 못해
// 앱이 열화되는 게 아니라 빈 페이지가 된다. 저장소가 막히면 팝업 인계 자체가 불가능하니
// 삼켜도 잃는 것이 없다 — 이 try/catch 를 "불필요한 방어"로 걷어내지 말 것.
try {
  WebBrowser.maybeCompleteAuthSession();
} catch {
  // 복구할 것이 없다.
}

export default function RootLayout() {
  useAutoUpdate();
  const [client] = useState(() => new QueryClient());
  const [loaded] = useFonts({
    'Pretendard-Regular': require('../../assets/fonts/Pretendard-Regular.ttf'),
    'Pretendard-Medium': require('../../assets/fonts/Pretendard-Medium.ttf'),
    'Pretendard-SemiBold': require('../../assets/fonts/Pretendard-SemiBold.ttf'),
    'Pretendard-Bold': require('../../assets/fonts/Pretendard-Bold.ttf'),
  });

  // 얼리 리턴보다 위 — 훅 순서가 렌더마다 달라지면 안 된다.
  const t = useT();

  // 앱 시작 시 저장된 세션 복원
  const hydrated = useAuthStore((s) => s.hydrated);
  const hydrate = useAuthStore((s) => s.hydrate);
  useEffect(() => {
    hydrate();
  }, [hydrate]);

  const localeHydrated = useLocaleStore((s) => s.hydrated);
  const hydrateLocale = useLocaleStore((s) => s.hydrate);
  useEffect(() => {
    hydrateLocale();
  }, [hydrateLocale]);
  // 얼리 리턴보다 위에서 읽는다 — 훅 순서가 렌더마다 달라지면 안 된다.
  const chosen = useLocaleStore((s) => s.chosen);

  useEffect(() => {
    if (loaded) applyGlobalFont();
  }, [loaded]);

  // 폰트 + 세션 + 언어 복원이 모두 끝날 때까지 렌더 게이트
  if (!loaded || !hydrated || !localeHydrated) return <View style={{ flex: 1, backgroundColor: colors.bg }} />;

  // 언어를 아직 안 고른 사람에게는 이 화면만 보여 준다. 탭을 먼저 띄우고 그 위에
  // 쌓으면 뒤에 한국어 화면이 비친다. 고르면 chosen 이 true 가 되어 아래 Stack 으로 넘어간다.
  //
  // **웹은 막지 않는다.** 주소를 치고 들어온 사람에게 아무것도 안 보여 준 채 선택을 먼저
  // 요구하면, 그게 무슨 사이트인지 모르는 상태로 막힌다. 웹은 브라우저 언어로 바로 열고
  // (hydrate 의 detectDeviceLocale), 바꾸고 싶으면 오른쪽 위 버튼에서 바꾼다.
  // 앱은 그대로 둔다 — 설치하고 처음 여는 자리라 한 번 묻는 편이 낫다.
  if (!chosen && Platform.OS !== 'web') {
    return (
      <QueryClientProvider client={client}>
        <SafeAreaProvider>
          <StatusBar style="dark" />
          <LanguageScreen />
        </SafeAreaProvider>
      </QueryClientProvider>
    );
  }

  return (
    <QueryClientProvider client={client}>
      <SafeAreaProvider>
        <StatusBar style="dark" />
        {/* 웹에서만, 탭 화면에서만 보인다. Stack 위에 떠 있어 모든 탭에서 같은 자리다. */}
        <WebLanguageSwitcher />
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
          <Stack.Screen name="language" options={{ headerShown: false }} />
          <Stack.Screen name="bookmarks" options={{ title: t('nav.bookmarks') }} />
          <Stack.Screen name="performances/traditional" options={{ title: t('home.traditional') }} />
          <Stack.Screen name="my/blocked" options={{ title: t('profile.menu.blocked') }} />
          {/* 현재 언어 + ' / Language' — 지금 언어를 읽지 못하는 사람도 라틴 글자로 이 화면을 알아본다. */}
          <Stack.Screen name="my/language" options={{ title: languageLabel(t) }} />
          <Stack.Screen name="my/service-info" options={{ title: t('profile.menu.serviceInfo') }} />
          <Stack.Screen name="my/profile" options={{ title: t('profile.edit.title') }} />
          <Stack.Screen name="ai-route" options={{ title: t('aiRoute.title') }} />
          {/* 경로 보기·검색은 탭이 아니라 탭 위에 쌓는 화면이다. 탭으로 두면
              backBehavior="none" 탓에 뒤로가기가 앱을 닫는다((tabs)/_layout 주석 참고). */}
          <Stack.Screen name="route/[id]" options={{ title: t('route.title') }} />
          <Stack.Screen name="search" options={{ title: t('nav.search') }} />
        </Stack>
      </SafeAreaProvider>
    </QueryClientProvider>
  );
}
