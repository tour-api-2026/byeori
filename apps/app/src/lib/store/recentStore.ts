import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

/**
 * 최근 본 장소. 기기 안에만 남는다.
 *
 * 홈의 '최근 본 장소'가 예전에는 저장 목록의 뒤 4개를 뒤집어 보여줘, 무엇을 봐도
 * 바뀌지 않았다. 실제로 연 장소를 상세 화면에서 기록해 쓴다.
 *
 * 서버에 보내지 않는다. 열람 이력은 개인정보라 굳이 쌓아둘 이유가 없고, 기기별로
 * 달라도 무방한 정보다. authStore 와 같은 이유로 웹에서는 localStorage 를 쓴다
 * (SecureStore 가 웹을 지원하지 않는다).
 */
const KEY = 'byeori.recentVenues';
const MAX = 12;
const isWeb = Platform.OS === 'web';

async function read(): Promise<string | null> {
  try {
    return isWeb ? window.localStorage.getItem(KEY) : await SecureStore.getItemAsync(KEY);
  } catch {
    return null; // 저장소 차단(시크릿 모드 등)
  }
}

async function write(value: string) {
  try {
    if (isWeb) window.localStorage.setItem(KEY, value);
    else await SecureStore.setItemAsync(KEY, value);
  } catch {
    // 영속 실패해도 이번 세션 메모리로는 유지된다
  }
}

/** 상세를 열 때 넘기는 최소 정보. 목록 카드를 그리는 데 필요한 만큼만 담는다. */
export type RecentVenue = {
  id: number | null;
  tourContentId: string | null;
  name: string;
  address: string | null;
  category: string | null;
  imageUrl: string | null;
};

type RecentState = {
  items: RecentVenue[];
  hydrated: boolean;
  hydrate: () => Promise<void>;
  push: (v: RecentVenue) => Promise<void>;
  clear: () => void;
};

/** 같은 장소인지. 우리 id 가 없는 장소(공사 콘텐츠만 있는 곳)도 있어 둘 다 본다. */
function sameVenue(a: RecentVenue, b: RecentVenue) {
  if (a.id != null && b.id != null) return a.id === b.id;
  if (a.tourContentId && b.tourContentId) return a.tourContentId === b.tourContentId;
  return a.name === b.name;
}

export const useRecentStore = create<RecentState>((set, get) => ({
  items: [],
  hydrated: false,

  hydrate: async () => {
    if (get().hydrated) return;
    const raw = await read();
    let items: RecentVenue[] = [];
    if (raw) {
      try {
        const parsed = JSON.parse(raw);
        if (Array.isArray(parsed)) items = parsed.filter((x) => x && typeof x.name === 'string');
      } catch {
        // 형식이 깨졌으면 버린다 — 없어도 그만인 정보다
      }
    }
    set({ items, hydrated: true });
  },

  /**
   * 저장소를 먼저 읽고 합친다.
   *
   * 메모리 상태만 보고 쓰면, 상세 화면에 직접 들어온 경우(주소 입력·새로고침)에는
   * 아직 불러오기 전이라 목록이 비어 있어 기존 기록을 덮어쓴다. 실제로 두 곳을
   * 연달아 봤는데 마지막 하나만 남았다.
   */
  push: async (v) => {
    if (!v?.name) return;
    await get().hydrate();
    const items = [v, ...get().items.filter((x) => !sameVenue(x, v))].slice(0, MAX);
    set({ items });
    await write(JSON.stringify(items));
  },

  clear: () => {
    void write('[]');
    set({ items: [] });
  },
}));
