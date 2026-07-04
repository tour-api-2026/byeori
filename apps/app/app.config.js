// 동적 설정: app.json을 베이스로, 카카오 네이티브 키(.env / EAS env)를 플러그인에 주입한다.
// (네이티브 앱 키를 app.json에 하드코딩하지 않기 위함)
const appJson = require('./app.json');

module.exports = () => {
  const expo = { ...appJson.expo };
  const kakaoAppKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_KEY || '';

  expo.plugins = (expo.plugins || []).map((p) => {
    if (p === '@react-native-seoul/kakao-login') {
      // kakaoAppKey + kotlinVersion. 이 플러그인이 android.kotlinVersion을 기본 1.5.10으로
      // 설정해 expo-root-project(KSP, Kotlin 2.x만 지원)와 충돌 → 지원 버전으로 넘긴다.
      return ['@react-native-seoul/kakao-login', { kakaoAppKey, kotlinVersion: '2.0.21' }];
    }
    // RN0.81/Expo54는 저장소를 settings.gradle로 중앙화(FAIL_ON_PROJECT_REPOS) →
    // 카카오 SDK maven 저장소를 프로젝트에 직접 추가해야 com.kakao.sdk 해석됨
    if (p === 'expo-build-properties') {
      return ['expo-build-properties', {
        android: { extraMavenRepos: ['https://devrepo.kakao.com/nexus/content/groups/public/'] },
      }];
    }
    return p;
  });

  // 현재 위치 기반 '내 주변' 지도 — 위치 권한 플러그인(중복 방지).
  const hasLocation = expo.plugins.some((p) => (Array.isArray(p) ? p[0] : p) === 'expo-location');
  if (!hasLocation) {
    expo.plugins = [
      ...expo.plugins,
      ['expo-location', { locationWhenInUsePermission: '내 주변 장소를 현재 위치 기준으로 보여주기 위해 위치를 사용합니다.' }],
    ];
  }

  return expo;
};
