import { translate } from './index';
import type { Locale } from './locales';
import { useLocaleStore } from '@/lib/store/localeStore';

export type ApiErrorBody = { code?: string; message?: string } | null | undefined;

/**
 * 서버 에러 코드 → 사전 키.
 *
 * 서버는 봉투에 code 와 한국어 message 를 함께 담아 보낸다. 여기 없는 코드는 서버가 준
 * 한국어를 그대로 띄운다 — 새 에러가 생겨도 화면이 비지 않고, 이 표에 한 줄 더하면
 * 그때부터 번역된다. 서버 Java 의 문구는 손대지 않는다.
 */
const BY_CODE: Record<string, string> = {
  'ITINERARY_NOT_FOUND': 'error.itinerary.notFound',
  'ITINERARY_FORBIDDEN': 'error.itinerary.forbidden',
  'ROUTE_TOO_FEW_POINTS': 'error.route.tooFewPoints',
  // 로그아웃 상태의 요청이 가장 흔히 맞는 코드다.
  'UNAUTHORIZED': 'error.unauthorized',
};

/**
 * 사람에게 보여 줄 에러 문구 — 언어를 받는 순수 함수.
 *
 * 판단이 여기 있고 errorText() 는 스토어에서 언어만 꺼내 넘기는 껍데기다. 이렇게 갈라
 * 두어야 i18n-check.mjs 가 이 함수를 떼어내 node 로 돌려 볼 수 있다(스토어는 node 에
 * 없다). 본문에 타입 캐스트(`as …`)를 쓰지 않는 이유도 같다.
 */
export function errorTextIn(locale: Locale, err: ApiErrorBody): string {
  const key = err && err.code ? BY_CODE[err.code] : undefined;
  if (key) return translate(locale, key);
  return (err && err.message) || translate(locale, 'common.error.unknown');
}

/** 화면·인터셉터에서 쓰는 쪽. 현재 언어를 꺼내 위로 넘긴다. */
export function errorText(err: ApiErrorBody): string {
  return errorTextIn(useLocaleStore.getState().locale, err);
}
