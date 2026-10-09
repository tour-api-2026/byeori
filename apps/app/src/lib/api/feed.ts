import { api, unwrap, ApiEnvelope } from './client';

/** 추천 한 칸. reason 은 "왜 이게 떴는지" 한 줄 — 없으면 추천이 무작위로 보인다. */
export type FeedItem = {
  targetType: 'VENUE' | 'PERFORMANCE';
  targetId: number;
  name: string | null;
  imageUrl: string | null;
  category: string | null;
  lat: number | null;
  lng: number | null;
  reason: string;
  score: number;
};

/** 로그인해야 부를 수 있다. 좌표는 있으면 보내고 없으면 생략한다. */
export function fetchFeed(params: { lat?: number; lng?: number; size?: number }): Promise<FeedItem[]> {
  return unwrap<FeedItem[]>(api.get<ApiEnvelope<FeedItem[]>>('/feed', { params }));
}
