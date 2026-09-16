import { api, Page, unwrap, ApiEnvelope } from './client';
import { Performance } from './types';

export type PerformanceFilter = { state?: string; genre?: string; keyword?: string; traditional?: boolean; size?: number };

export function fetchPerformances(filter: PerformanceFilter = {}): Promise<Page<Performance>> {
  return unwrap<Page<Performance>>(api.get<ApiEnvelope<Page<Performance>>>('/performances', {
    params: { ...filter, size: filter.size ?? 20 },
  }));
}

export function fetchPerformance(id: number): Promise<Performance> {
  return unwrap<Performance>(api.get<ApiEnvelope<Performance>>(`/performances/${id}`));
}
