export type Locale = 'ko' | 'en' | 'ja' | 'zh';

export const LOCALES: readonly Locale[] = ['ko', 'en', 'ja', 'zh'];

/** 각 언어가 스스로를 부르는 이름. 언어 고르는 화면은 번역할 수 없으므로 이 값을 쓴다. */
export const LOCALE_NAMES: Record<Locale, string> = {
  ko: '한국어',
  en: 'English',
  ja: '日本語',
  zh: '中文',
};

/**
 * 언어 설정 화면의 제목. 일부러 한국어·영어를 함께 적고 번역하지 않는다 — 지금 언어를
 * 읽지 못하는 사람도 이 화면을 찾아올 수 있어야 한다. 화면 파일에 한국어 리터럴을 두지
 * 않으려고(i18n-check) 값을 여기 둔다.
 */
export const LANGUAGE_SCREEN_TITLE = '언어 / Language';
