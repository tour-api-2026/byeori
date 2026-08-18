import { api, unwrap, ApiEnvelope } from './client';

export type Review = {
  id: number;
  targetType: 'VENUE' | 'PERFORMANCE';
  targetId: number;
  userId: number;
  rating: number;
  content: string | null;
  createdAt: string;
};

export function fetchReviews(targetType: string, targetId: number): Promise<Review[]> {
  return unwrap<Review[]>(api.get<ApiEnvelope<Review[]>>('/reviews', { params: { targetType, targetId } }));
}

export function fetchMyReviews(): Promise<Review[]> {
  return unwrap<Review[]>(api.get<ApiEnvelope<Review[]>>('/users/me/reviews'));
}

export function createReview(body: { targetType: string; targetId: number; rating: number; content?: string }): Promise<Review> {
  return unwrap<Review>(api.post<ApiEnvelope<Review>>('/reviews', body));
}

export function deleteReview(id: number): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>(`/reviews/${id}`));
}

export function reportReview(id: number, body: { reason: string; detail?: string }): Promise<void> {
  return unwrap<void>(api.post<ApiEnvelope<void>>(`/reviews/${id}/reports`, body));
}
