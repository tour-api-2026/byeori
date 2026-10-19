import { NativeModules, Platform } from 'react-native';
import { LOCALES, type Locale } from './locales';   // index 가 아니라 locales — 순환을 피한다

/**
 * 기기가 알려 준 언어 문자열을 우리 네 가지 중 하나로 줄인다.
 *
 * 들어오는 꼴이 제각각이다 — `ko-KR`, `en_US`, `zh-Hans-CN`, `EN`. 앞의 언어 조각만
 * 보고 가른다. 중국어는 간체 하나만 두므로 `zh-TW`(번체)도 `zh` 로 본다. 번체 사용자에게
 * 간체가 보이는 것은 아쉽지만, 한국어가 보이는 것보다는 낫다.
 *
 * 모르는 언어는 null 이다 — 부르는 쪽이 한국어로 떨어뜨린다.
 *
 * 이 함수 본문에는 타입 캐스트(`as …`)를 쓰지 않는다. i18n-check.mjs 가 본문을 떼어내
 * node 로 그대로 돌리는데, 캐스트가 있으면 구문 오류가 난다.
 */
export function normalizeLocale(raw: string | null | undefined): Locale | null {
  if (!raw) return null;
  const head = String(raw).toLowerCase().split(/[-_]/)[0];
  return LOCALES.find((l) => l === head) ?? null;
}

/**
 * 기기 언어를 읽는다. 못 읽으면 null.
 *
 * 패키지를 넣지 않으려고 두 가지 길을 차례로 본다 — `package.json` 이 바뀌면 Expo
 * 지문이 달라져 이미 설치된 앱에 OTA 가 닿지 못한다.
 *
 *   1. Intl — 웹에서는 늘 되고, Hermes 도 대개 지원한다
 *   2. RN 내장 모듈 — Intl 이 없는 빌드를 위한 대비
 *
 * 어느 쪽도 던지지 않게 감싼다. 언어를 못 읽는 것은 앱이 멈출 이유가 아니다.
 */
export function detectDeviceLocale(): Locale | null {
  try {
    const fromIntl = normalizeLocale(Intl?.DateTimeFormat?.().resolvedOptions?.().locale);
    if (fromIntl) return fromIntl;
  } catch {
    // Intl 이 없는 빌드 — 아래로 내려간다
  }
  try {
    const raw =
      Platform.OS === 'ios'
        ? (NativeModules.SettingsManager?.settings?.AppleLocale as string | undefined)
          ?? (NativeModules.SettingsManager?.settings?.AppleLanguages as string[] | undefined)?.[0]
        : (NativeModules.I18nManager?.localeIdentifier as string | undefined);
    return normalizeLocale(raw);
  } catch {
    return null;
  }
}
