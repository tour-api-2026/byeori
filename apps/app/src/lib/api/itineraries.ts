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

// ── 공유 링크 ──

/**
 * 공유 링크로 보는 루트. ItineraryDetail 과 닮았지만 서버가 **일부러 다른 모양**으로 준다.
 * memo·id 가 없다 — 개인 메모가 링크를 받은 사람에게 넘어가지 않게 서버에서 뺀다.
 */
export type SharedStop = {
  targetType: 'VENUE' | 'PERFORMANCE';
  targetId: number;
  name: string | null;
  imageUrl: string | null;
  visitDate: string;
  sortOrder: number;
  plannedTime: string | null;
  lat: number | null;
  lng: number | null;
};

export type SharedItinerary = {
  title: string; startDate: string; endDate: string; stops: SharedStop[];
};

/** 공유 토큰을 받아온다. 서버가 처음 한 번만 만들고 그 뒤로는 같은 값을 준다. */
export function shareItinerary(id: number): Promise<{ token: string }> {
  return unwrap<{ token: string }>(api.post<ApiEnvelope<{ token: string }>>(`/itineraries/${id}/share`, {}));
}

/** 로그인 없이 부른다. 토큰이 열쇠다. */
export function fetchSharedItinerary(token: string): Promise<SharedItinerary> {
  return unwrap<SharedItinerary>(api.get<ApiEnvelope<SharedItinerary>>(`/shared/itineraries/${token}`));
}

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

/** 루트의 이름·기간 수정. 서버는 처음부터 PATCH 를 받고 있었는데 화면이 부르지 않았다. */
export function updateItinerary(id: number, body: { title?: string; startDate?: string; endDate?: string }): Promise<ItineraryDetail> {
  return unwrap<ItineraryDetail>(api.patch<ApiEnvelope<ItineraryDetail>>(`/itineraries/${id}`, body));
}

export function deleteItinerary(id: number): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>(`/itineraries/${id}`));
}

export function addItineraryItem(id: number, body: { targetType: string; targetId: number; visitDate: string; sortOrder?: number; plannedTime?: string; memo?: string }): Promise<ItineraryItem> {
  return unwrap<ItineraryItem>(api.post<ApiEnvelope<ItineraryItem>>(`/itineraries/${id}/items`, body));
}

/** 항목의 방문일·시간·순서·메모 수정. 서버는 처음부터 PATCH 를 받고 있었다. */
export function updateItineraryItem(id: number, itemId: number, body: {
  visitDate?: string; sortOrder?: number; plannedTime?: string | null; memo?: string | null;
}): Promise<ItineraryItem> {
  return unwrap<ItineraryItem>(api.patch<ApiEnvelope<ItineraryItem>>(`/itineraries/${id}/items/${itemId}`, body));
}

/**
 * 하루치 순서를 통째로 다시 매긴다.
 *
 * updateItineraryItem 으로는 순서를 못 바꾼다 — 서버가 받은 항목 하나만 고치고 나머지를
 * 다시 매기지 않아, 2번을 1번으로 올리면 1번이 둘이 된다. 그날 전체를 보내야 한다.
 */
export function reorderItineraryItems(id: number, visitDate: string, itemIds: number[]): Promise<ItineraryItem[]> {
  return unwrap<ItineraryItem[]>(api.patch<ApiEnvelope<ItineraryItem[]>>(`/itineraries/${id}/items/order`, { visitDate, itemIds }));
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
