import { api, unwrap, ApiEnvelope } from './client';

export type Wishlist = {
  id: number;
  targetType: 'VENUE' | 'PERFORMANCE';
  targetId: number;
  name: string | null;
  imageUrl: string | null;
  avgRating: number;
  reviewCount: number;
};

export function fetchMyWishlists(): Promise<Wishlist[]> {
  return unwrap<Wishlist[]>(api.get<ApiEnvelope<Wishlist[]>>('/users/me/wishlists'));
}

export function addWishlist(targetType: string, targetId: number): Promise<Wishlist> {
  return unwrap<Wishlist>(api.post<ApiEnvelope<Wishlist>>('/wishlists', { targetType, targetId }));
}

export function removeWishlist(targetType: string, targetId: number): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>('/wishlists', { params: { targetType, targetId } }));
}
