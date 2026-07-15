// 동적 설정: app.json을 베이스로, 카카오 네이티브 키(.env / EAS env)를 플러그인에 주입한다.
// (네이티브 앱 키를 app.json에 하드코딩하지 않기 위함)
const appJson = require('./app.json');

module.exports = () => {
  const expo = { ...appJson.expo };
  const kakaoAppKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_KEY || '';

  expo.plugins = (expo.plugins || []).map((p) => {
    if (p === '@react-native-seoul/kakao-login') {
      // kakaoAppKey + kotlinVersion. 이 플러그인이 android.kotlinVersion gradle 속성을 직접
      // 기록한다(옵션 미지정 시 기본 1.5.10 → expo-root-project가 지원 안 함). RN0.81/Expo54의
      // Kotlin 컴파일러는 2.1.20이므로 여기서 2.1.20으로 맞춰야 expo-updates의 KSP도
      // 호환 버전(2.1.20-2.0.1)으로 잡혀 kspReleaseKotlin 크래시가 나지 않는다.
      return ['@react-native-seoul/kakao-login', { kakaoAppKey, kotlinVersion: '2.1.20' }];
    }
    return p;
  });

  const pluginName = (p) => (Array.isArray(p) ? p[0] : p);

  // 현재 위치 기반 '내 주변' 지도 — 위치 권한 플러그인(중복 방지).
  if (!expo.plugins.some((p) => pluginName(p) === 'expo-location')) {
    expo.plugins = [
      ...expo.plugins,
      ['expo-location', { locationWhenInUsePermission: '내 주변 장소를 현재 위치 기준으로 보여주기 위해 위치를 사용합니다.' }],
    ];
  }

  return expo;
};
