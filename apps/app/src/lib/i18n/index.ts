import { ko } from './ko';
import { en } from './en';
import { ja } from './ja';
import { zh } from './zh';
import type { Locale } from './locales';
import { useLocaleStore } from '@/lib/store/localeStore';

export { LOCALES, LOCALE_NAMES, type Locale } from './locales';

const DICT: Record<Locale, Record<string, string>> = { ko, en, ja, zh };

/**
 * 문구 하나를 꺼낸다.
 *
 * 떨어지는 차례는 선택한 언어 → 한국어 → 키 자체다. 한국어까지 없을 때 빈 문자열을
 * 주면 버튼에 글자가 사라져 누를 곳이 보이지 않는다. 키가 보이면 흉하지만 고칠 곳을
 * 알려 준다.
 *
 * `{name}` 꼴을 vars 로 치환한다. 넘기지 않은 변수는 빈 문자열이 된다 — 화면에
 * `{name}` 이 그대로 뜨는 것보다 낫다.
 */
export function translate(locale: Locale, key: string, vars?: Record<string, unknown>): string {
  const raw = DICT[locale]?.[key] ?? DICT.ko[key] ?? key;
  return raw.replace(/\{(\w+)\}/g, (_, name) => String(vars?.[name] ?? ''));
}

/**
 * 화면에서 쓰는 번역 함수.
 *
 * 스토어를 구독하므로 언어가 바뀌면 이 훅을 쓰는 화면이 다시 그려진다. 모듈 수준에서
 * `translate(locale, …)` 을 직접 부르면 그 갱신을 놓친다 — 화면에서는 반드시 이쪽을 쓴다.
 */
export function useT() {
  const locale = useLocaleStore((s) => s.locale);
  return (key: string, vars?: Record<string, unknown>) => translate(locale, key, vars);
}
