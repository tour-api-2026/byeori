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
