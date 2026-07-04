import { api, unwrap, ApiEnvelope } from './client';

export type ItinerarySummary = {
  id: number; title: string; startDate: string; endDate: string; sourceType: string; itemCount: number;
};

export type ItineraryItem = {
  id: number;
  targetType: 'VENUE' | 'PERFORMANCE';
  targetId: number;
  name: string | null;
  imageUrl: string | null;
  visitDate: string;
  sortOrder: number;
  plannedTime: string | null;
  memo: string | null;
};

export type ItineraryDetail = {
  id: number; title: string; startDate: string; endDate: string; sourceType: string; items: ItineraryItem[];
};

// ── 길찾기(경로) ──
export type RouteStop = {
  order: number; targetType: 'VENUE' | 'PERFORMANCE'; targetId: number; name: string | null; lat: number; lng: number;
};
// pathEnd: 구간이 끝나는 path 인덱스(exclusive). 구간 시작은 이전 leg의 pathEnd(첫 구간은 0).
export type RouteLeg = { distance: number; duration: number; pathEnd: number };
export type ItineraryRoute = {
  distance: number;            // 총 거리(m)
  duration: number;            // 총 소요시간(초)
  priority: string;
  stops: RouteStop[];          // 경로에 포함된 방문지(순서대로)
  legs: RouteLeg[];            // 구간별 거리/시간
  path: [number, number][];    // polyline 좌표열 [위도, 경도]
};

export function fetchMyItineraries(): Promise<ItinerarySummary[]> {
  return unwrap<ItinerarySummary[]>(api.get<ApiEnvelope<ItinerarySummary[]>>('/users/me/itineraries'));
}

export function fetchItinerary(id: number): Promise<ItineraryDetail> {
  return unwrap<ItineraryDetail>(api.get<ApiEnvelope<ItineraryDetail>>(`/itineraries/${id}`));
}

export function createItinerary(body: { title: string; startDate: string; endDate: string; sourceType?: string; sourceCourseId?: number }): Promise<ItineraryDetail> {
  return unwrap<ItineraryDetail>(api.post<ApiEnvelope<ItineraryDetail>>('/itineraries', body));
}

export function deleteItinerary(id: number): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>(`/itineraries/${id}`));
}

export function addItineraryItem(id: number, body: { targetType: string; targetId: number; visitDate: string; sortOrder?: number; plannedTime?: string; memo?: string }): Promise<ItineraryItem> {
  return unwrap<ItineraryItem>(api.post<ApiEnvelope<ItineraryItem>>(`/itineraries/${id}/items`, body));
}

export function deleteItineraryItem(id: number, itemId: number): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>(`/itineraries/${id}/items/${itemId}`));
}

export function fetchItineraryRoute(id: number, priority = 'RECOMMEND'): Promise<ItineraryRoute> {
  return unwrap<ItineraryRoute>(api.get<ApiEnvelope<ItineraryRoute>>(`/itineraries/${id}/route`, { params: { priority } }));
}
