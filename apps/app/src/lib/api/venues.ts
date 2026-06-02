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
