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
  /** 코드(NEARBY·ONGOING…). 구버전 서버는 완성된 한국어 문장을 준다. */
  reason: string;
  /** reason 의 값(거리·날수·분류). 없으면 null — 구버전 서버에선 필드 자체가 없다. */
  reasonParam?: string | null;
  score: number;
};

/** 로그인해야 부를 수 있다. 좌표는 있으면 보내고 없으면 생략한다. */
export function fetchFeed(params: { lat?: number; lng?: number; size?: number }): Promise<FeedItem[]> {
  return unwrap<FeedItem[]>(api.get<ApiEnvelope<FeedItem[]>>('/feed', { params }));
}
