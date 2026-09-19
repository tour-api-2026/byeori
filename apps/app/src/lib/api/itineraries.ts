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
  lat: number | null;
  lng: number | null;
};

export type ItineraryDetail = {
  id: number; title: string; startDate: string; endDate: string; sourceType: string; items: ItineraryItem[];
};

// ── 길찾기(경로) ──
export type RouteStop = {
  order: number; targetType: 'VENUE' | 'PERFORMANCE'; targetId: number; name: string | null; lat: number; lng: number;
};
export type RouteLeg = { distance: number; duration: number };
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

/** 카카오에서 찾은 장소(벼리 DB 밖). 서버 PlaceController.PlaceResult 와 같다. */
export type KakaoPlace = {
  kakaoPlaceId: string;
  name: string;
  category: string;          // 벼리 분류(맛집·카페·문화)
  categoryName: string | null; // 카카오 원래 분류(국밥·한식 등, 표시용)
  address: string;
  phone: string;
  lat: number;
  lng: number;
};

export function searchKakaoPlaces(query: string, near?: { lat: number; lng: number } | null): Promise<KakaoPlace[]> {
  return unwrap<KakaoPlace[]>(api.get<ApiEnvelope<KakaoPlace[]>>('/places/search', {
    params: { query, lat: near?.lat, lng: near?.lng },
  }));
}

/** 카카오 장소를 루트에 넣는다. 서버가 나만 보는 장소로 저장한다. */
export function addPlaceItem(id: number, place: KakaoPlace, visitDate: string, sortOrder?: number): Promise<ItineraryItem> {
  return unwrap<ItineraryItem>(api.post<ApiEnvelope<ItineraryItem>>(`/itineraries/${id}/items/place`, {
    kakaoPlaceId: place.kakaoPlaceId,
    name: place.name,
    address: place.address,
    category: place.category === '맛집' ? 'FD6' : place.category === '카페' ? 'CE7' : '',
    phone: place.phone,
    lat: place.lat,
    lng: place.lng,
    visitDate,
    sortOrder,
  }));
}
