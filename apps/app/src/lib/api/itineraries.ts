import { api, unwrap, ApiEnvelope } from './client';

/** 목록 카드에 한 줄로 찍히는 방문지. 번호와 이름만 쓴다. */
export type SummaryStop = { id: number; name: string | null };

/**
 * 목록용 요약.
 *
 * stops 는 카드가 보여줄 앞 몇 곳이다. 전에는 카드마다 상세를 따로 받아 와서, 제목은
 * 먼저 뜨는데 방문지 줄만 뒤늦게 나타났다. 전체 개수는 itemCount 를 본다.
 */
export type ItinerarySummary = {
  id: number; title: string; startDate: string; endDate: string; sourceType: string;
  itemCount: number; stops: SummaryStop[];
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
  visitDate: string;           // 'YYYY-MM-DD' — 지도가 하루씩 끊어 그리는 기준(항목에 없으면 루트 시작일)
};
export type RouteLeg = {
  distance: number;            // 구간 거리(m)
  duration: number;            // 구간 시간(초)
  mode: 'WALK' | 'DRIVE' | 'STRAIGHT';
  points: number;              // 전체 path 에서 이 구간이 차지하는 점 수(이음점은 앞 구간에만 센다)
};
export type ItineraryRoute = {
  distance: number;            // 총 거리(m)
  duration: number;            // 총 소요시간(초)
  mode: string;                // 요청한 모드(AUTO|WALK|DRIVE)
  stops: RouteStop[];          // 경로에 포함된 방문지(순서대로)
  legs: RouteLeg[];            // 구간별 거리/시간/이동수단
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
  /** 장소 교체. 둘 다 보내야 바뀐다. 서버가 메모를 지운다(AI 추천 이유가 남으면 엉뚱해진다). */
  targetType?: 'VENUE' | 'PERFORMANCE'; targetId?: number;
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

export function fetchItineraryRoute(id: number, mode = 'AUTO'): Promise<ItineraryRoute> {
  return unwrap<ItineraryRoute>(api.get<ApiEnvelope<ItineraryRoute>>(`/itineraries/${id}/route`, { params: { mode } }));
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

/** 한 쪽의 검색 결과. end 가 true 면 더 받을 것이 없다. */
export type KakaoPlacePage = { items: KakaoPlace[]; end: boolean };

/**
 * 쪽을 넘겨 가며 검색한다. page 는 1부터.
 *
 * 카카오는 한 쪽에 15건, 전체 45건까지만 내준다. 그 상한을 여기 적지 않는 이유는 서버가
 * 카카오의 is_end 를 그대로 전해 주기 때문이다 — 카카오가 상한을 바꿔도 따라간다.
 *
 * page 를 주면 서버가 {items, end} 를 준다. 안 주면 예전처럼 배열이라(이미 깔린 앱이 그
 * 모양을 읽는다) 위 searchKakaoPlaces 는 그대로 둔다.
 */
export function searchKakaoPlacePage(
  query: string, page: number, near?: { lat: number; lng: number } | null,
): Promise<KakaoPlacePage> {
  return unwrap<KakaoPlacePage>(api.get<ApiEnvelope<KakaoPlacePage>>('/places/search', {
    params: { query, page, lat: near?.lat, lng: near?.lng },
  }));
}

/** 이 자리의 장소를 카카오에서 고른 곳으로 바꾼다. */
export function replaceItemPlace(id: number, itemId: number, place: KakaoPlace): Promise<ItineraryItem> {
  return unwrap<ItineraryItem>(api.patch<ApiEnvelope<ItineraryItem>>(`/itineraries/${id}/items/${itemId}/place`, {
    kakaoPlaceId: place.kakaoPlaceId,
    name: place.name,
    address: place.address,
    category: place.category === '맛집' ? 'FD6' : place.category === '카페' ? 'CE7' : '',
    phone: place.phone,
    lat: place.lat,
    lng: place.lng,
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
