import { create } from 'zustand';

// 데모용 로컬 즐겨찾기 (실서비스는 /wishlists API 연동)
type BookmarkState = {
  venueIds: number[];
  toggle: (id: number) => void;
  has: (id: number) => boolean;
};

export const useBookmarkStore = create<BookmarkState>((set, get) => ({
  venueIds: [],
  toggle: (id) =>
    set((s) => ({
      venueIds: s.venueIds.includes(id) ? s.venueIds.filter((v) => v !== id) : [...s.venueIds, id],
    })),
  has: (id) => get().venueIds.includes(id),
}));
