import { create } from 'zustand';
import { delItem, getItem, setItem } from './storage';

// 인증 사용자
export type AuthUser = {
  id: number | string;
  name: string;
  email?: string;
  profileImageUrl?: string;
};

export type Session = {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
};

const KEY_ACCESS = 'byeori.access';
const KEY_REFRESH = 'byeori.refresh';
const KEY_USER = 'byeori.user';

type AuthState = {
  user: AuthUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  hydrated: boolean;
  /** 파생: accessToken 보유 여부 */
  isLoggedIn: boolean;
  setSession: (s: Session) => Promise<void>;
  /** refresh 인터셉터에서 토큰만 갱신할 때 사용 */
  setTokens: (t: { accessToken: string; refreshToken: string }) => Promise<void>;
  /** 프로필 수정 후 화면에 보이는 사용자 정보만 바꾼다(토큰은 그대로). */
  setUser: (user: AuthUser) => Promise<void>;
  logout: () => Promise<void>;
  hydrate: () => Promise<void>;
};

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  refreshToken: null,
  hydrated: false,
  isLoggedIn: false,

  setSession: async ({ accessToken, refreshToken, user }) => {
    set({ accessToken, refreshToken, user, isLoggedIn: !!accessToken });
    await Promise.all([
      setItem(KEY_ACCESS, accessToken),
      setItem(KEY_REFRESH, refreshToken),
      setItem(KEY_USER, JSON.stringify(user)),
    ]);
  },

  setTokens: async ({ accessToken, refreshToken }) => {
    set({ accessToken, refreshToken, isLoggedIn: !!accessToken });
    await Promise.all([setItem(KEY_ACCESS, accessToken), setItem(KEY_REFRESH, refreshToken)]);
  },

  setUser: async (user) => {
    set({ user });
    await setItem(KEY_USER, JSON.stringify(user));
  },

  logout: async () => {
    set({ accessToken: null, refreshToken: null, user: null, isLoggedIn: false });
    await Promise.all([delItem(KEY_ACCESS), delItem(KEY_REFRESH), delItem(KEY_USER)]);
  },

  hydrate: async () => {
    if (get().hydrated) return;
    const [accessToken, refreshToken, userRaw] = await Promise.all([
      getItem(KEY_ACCESS),
      getItem(KEY_REFRESH),
      getItem(KEY_USER),
    ]);
    let user: AuthUser | null = null;
    if (userRaw) {
      try {
        user = JSON.parse(userRaw) as AuthUser;
      } catch {
        user = null;
      }
    }
    set({
      accessToken: accessToken ?? null,
      refreshToken: refreshToken ?? null,
      user,
      isLoggedIn: !!accessToken,
      hydrated: true,
    });
  },
}));
