import { api, Page, unwrap, ApiEnvelope } from './client';
import { Performance, Venue, VenueDetail } from './types';

export type VenueFilter = { category?: string; hanbokDiscount?: boolean; keyword?: string; size?: number };

export function fetchVenues(filter: VenueFilter = {}): Promise<Page<Venue>> {
  return unwrap<Page<Venue>>(api.get<ApiEnvelope<Page<Venue>>>('/venues', {
    params: { ...filter, size: filter.size ?? 20 },
  }));
}

/** 지도 주변 조회 — 보고 있는 좌표·반경만 한국관광공사 OpenAPI로 실시간 조회한다. */
export type NearbyParams = { lat: number; lng: number; radius?: number; category?: string };

export type LiveSearchParams = { keyword: string; category?: string; hanbokDiscount?: boolean };

/** 공사 OpenAPI 실시간 키워드 검색. 실패·무결과면 빈 배열이라 호출부가 저장 검색으로 대체한다. */
export function searchVenuesLive(p: LiveSearchParams): Promise<Venue[]> {
  return unwrap<Venue[]>(
    api.get<ApiEnvelope<Venue[]>>('/venues/search', {
      params: { keyword: p.keyword, category: p.category, hanbokDiscount: p.hanbokDiscount, size: 60 },
    }),
  );
}

export function fetchNearbyVenues(p: NearbyParams): Promise<Venue[]> {
  return unwrap<Venue[]>(
    api.get<ApiEnvelope<Venue[]>>('/venues/nearby', {
      params: { lat: p.lat, lng: p.lng, radius: p.radius ?? 3000, category: p.category },
    }),
  );
}

export function fetchVenueDetail(id: number): Promise<VenueDetail> {
  return unwrap<VenueDetail>(api.get<ApiEnvelope<VenueDetail>>(`/venues/${id}`));
}

export function fetchVenuePerformances(id: number): Promise<Performance[]> {
  return unwrap<Performance[]>(api.get<ApiEnvelope<Performance[]>>(`/venues/${id}/performances`));
}

export function fetchMyVenues(): Promise<Venue[]> {
  return unwrap<Venue[]>(api.get<ApiEnvelope<Venue[]>>('/venues/mine'));
}

export type VenueCreateBody = {
  name: string; address: string; lat: number; lng: number;
  category?: string; phone?: string; homepageUrl?: string; operatingHours?: string; imageUrl?: string; description?: string;
};

export function createVenue(body: VenueCreateBody): Promise<VenueDetail> {
  return unwrap<VenueDetail>(api.post<ApiEnvelope<VenueDetail>>('/venues', body));
}

// 수정: 서버가 create와 동일한 DTO를 받고 null 아닌 필드만 갱신(PATCH). 소유권은 서버가 검증.
export function updateVenue(id: number, body: Partial<VenueCreateBody>): Promise<VenueDetail> {
  return unwrap<VenueDetail>(api.patch<ApiEnvelope<VenueDetail>>(`/venues/${id}`, body));
}

export function deleteVenue(id: number): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>(`/venues/${id}`));
}

export function reportVenue(id: number, body: { reason: string; detail?: string }): Promise<void> {
  return unwrap<void>(api.post<ApiEnvelope<void>>(`/venues/${id}/reports`, body));
}
