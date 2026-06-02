import { api, Page, unwrap, ApiEnvelope } from './client';
import { Performance, Venue, VenueDetail } from './types';

export type VenueFilter = { category?: string; hanbokDiscount?: boolean; keyword?: string; size?: number };

export function fetchVenues(filter: VenueFilter = {}): Promise<Page<Venue>> {
  return unwrap<Page<Venue>>(api.get<ApiEnvelope<Page<Venue>>>('/venues', {
    params: { ...filter, size: filter.size ?? 20 },
  }));
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

export function reportVenue(id: number, body: { reason: string; detail?: string }): Promise<void> {
  return unwrap<void>(api.post<ApiEnvelope<void>>(`/venues/${id}/reports`, body));
}
