export type Locale = 'ko' | 'en' | 'ja' | 'zh';

export const LOCALES: readonly Locale[] = ['ko', 'en', 'ja', 'zh'];

/** 각 언어가 스스로를 부르는 이름. 언어 고르는 화면은 번역할 수 없으므로 이 값을 쓴다. */
export const LOCALE_NAMES: Record<Locale, string> = {
  ko: '한국어',
  en: 'English',
  ja: '日本語',
  zh: '中文',
};

