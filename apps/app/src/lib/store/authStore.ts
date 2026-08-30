import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import { create } from 'zustand';

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
  logout: () => Promise<void>;
  hydrate: () => Promise<void>;
};

// SecureStore는 웹을 지원하지 않는다. 웹에서 그냥 무시하면 새로고침할 때마다 로그아웃되므로
// localStorage로 대체한다. 브라우저 저장소라 네이티브의 키체인만큼 안전하진 않지만,
// 웹앱에서 세션을 유지할 다른 수단이 없다(시크릿 모드·저장소 차단 시엔 조용히 실패).
const isWeb = Platform.OS === 'web';

async function setItem(key: string, value: string) {
  if (isWeb) {
    try {
      window.localStorage.setItem(key, value);
    } catch {
      // 저장소 차단(시크릿 모드 등): 이번 세션 메모리로만 유지
    }
    return;
  }
  try {
    await SecureStore.setItemAsync(key, value);
  } catch {
    // 미지원 환경: 영속 생략 (세션 메모리만 유지)
  }
}
async function getItem(key: string): Promise<string | null> {
  if (isWeb) {
    try {
      return window.localStorage.getItem(key);
    } catch {
      return null;
    }
  }
  try {
    return await SecureStore.getItemAsync(key);
  } catch {
    return null;
  }
}
async function delItem(key: string) {
  if (isWeb) {
    try {
      window.localStorage.removeItem(key);
    } catch {
      // 무시
    }
    return;
  }
  try {
    await SecureStore.deleteItemAsync(key);
  } catch {
    // 무시
  }
}

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
