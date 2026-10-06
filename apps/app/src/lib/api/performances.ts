import { api, Page, unwrap, ApiEnvelope } from './client';
import { Performance } from './types';

export type PerformanceFilter = { state?: string; genre?: string; keyword?: string; traditional?: boolean; size?: number };

export function fetchPerformances(filter: PerformanceFilter = {}): Promise<Page<Performance>> {
  return unwrap<Page<Performance>>(api.get<ApiEnvelope<Page<Performance>>>('/performances', {
    params: { ...filter, size: filter.size ?? 20 },
  }));
}

/** 좌표 주변에서 지금 열리거나 곧 열릴 행사. 장소 상세의 '주변에서 열리는 행사'. */
export function fetchNearbyPerformances(p: { lat: number; lng: number; radius?: number; size?: number }): Promise<Performance[]> {
  return unwrap<Performance[]>(api.get<ApiEnvelope<Performance[]>>('/performances/nearby', {
    params: { radius: 2000, size: 10, ...p },
  }));
}

export function fetchPerformance(id: number): Promise<Performance> {
  return unwrap<Performance>(api.get<ApiEnvelope<Performance>>(`/performances/${id}`));
}
