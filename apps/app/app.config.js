// 동적 설정: app.json을 베이스로, 카카오 네이티브 키(.env / EAS env)를 플러그인에 주입한다.
// (네이티브 앱 키를 app.json에 하드코딩하지 않기 위함)
const appJson = require('./app.json');

module.exports = () => {
  const expo = { ...appJson.expo };
  const kakaoAppKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_KEY || '';

  expo.plugins = (expo.plugins || []).map((p) =>
    p === '@react-native-seoul/kakao-login'
      ? ['@react-native-seoul/kakao-login', { kakaoAppKey }]
      : p,
  );

  return expo;
};
