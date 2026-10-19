import { create } from 'zustand';
import { detectDeviceLocale } from '@/lib/i18n/device';
import { LOCALES, type Locale } from '@/lib/i18n/locales';   // index 가 아니라 locales — 순환을 피한다
import { getItem, setItem } from './storage';

const KEY = 'byeori.locale';

type LocaleState = {
  locale: Locale;
  /** 사용자가 직접 고른 적이 있는지. 없으면 첫 실행 언어 화면을 띄운다. */
  chosen: boolean;
  hydrated: boolean;
  setLocale: (l: Locale) => Promise<void>;
  hydrate: () => Promise<void>;
};

export const useLocaleStore = create<LocaleState>((set) => ({
  // 복원 전 기본값. 화면은 hydrated 가 true 가 될 때까지 기다린다.
  locale: 'ko',
  chosen: false,
  hydrated: false,

  setLocale: async (l) => {
    set({ locale: l, chosen: true });
    await setItem(KEY, l);   // 저장이 막혀도 던지지 않는다(storage 가 삼킨다)
  },

  /**
   * 저장된 언어를 되살린다. 없으면 기기 언어로 짐작하되 chosen 은 false 로 둔다 —
   * 짐작은 선택이 아니므로 첫 화면에서 확인받아야 한다.
   */
  hydrate: async () => {
    const raw = await getItem(KEY);
    const saved = LOCALES.find((l) => l === raw);
    if (saved) {
      set({ locale: saved, chosen: true, hydrated: true });
      return;
    }
    set({ locale: detectDeviceLocale() ?? 'ko', chosen: false, hydrated: true });
  },
}));
