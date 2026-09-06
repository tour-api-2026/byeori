import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { blockUser, fetchBlockedUsers, unblockUser } from '../api/account';
import { fetchCourseDetail, fetchCourses } from '../api/courses';
import {
  addItineraryItem, createItinerary, deleteItinerary, deleteItineraryItem,
  fetchItinerary, fetchItineraryRoute, fetchMyItineraries,
} from '../api/itineraries';
import { fetchPerformances, PerformanceFilter } from '../api/performances';
import { createReview, deleteReview, fetchMyReviews, fetchReviews, reportReview } from '../api/reviews';
import { fetchCommentTags, fetchContentTags, unvoteTag, voteTag } from '../api/tags';
import {
  createVenue, deleteVenue, fetchMyVenues, fetchNearbyVenues, fetchVenueDetail,
  fetchVenuePerformances, fetchVenues, reportVenue, searchVenuesLive, updateVenue,
  VenueCreateBody, VenueFilter, type LiveSearchParams, type NearbyParams,
} from '../api/venues';
import { addWishlist, fetchMyWishlists, removeWishlist } from '../api/wishlists';
import { useAuthStore } from '../store/authStore';

// ---------- 장소 ----------
export function useVenuesQuery(filter: VenueFilter = {}) {
  return useQuery({ queryKey: ['venues', filter], queryFn: () => fetchVenues(filter) });
}
/**
 * 지도 주변 조회. 지도를 움직일 때마다 부르지 않도록 호출부에서 좌표를 반올림해 넘긴다
 * (같은 키면 캐시가 재사용된다).
 */
export function useLiveSearchQuery(p: LiveSearchParams | null) {
  return useQuery({
    queryKey: ['venues-search-live', p],
    queryFn: () => searchVenuesLive(p as LiveSearchParams),
    enabled: !!p,
    staleTime: 5 * 60 * 1000,
  });
}

export function useNearbyVenuesQuery(p: NearbyParams | null) {
  return useQuery({
    queryKey: ['venues-nearby', p],
    queryFn: () => fetchNearbyVenues(p as NearbyParams),
    enabled: !!p,
    staleTime: 5 * 60 * 1000,
  });
}
export function useVenueDetailQuery(id: number) {
  return useQuery({ queryKey: ['venue', id], queryFn: () => fetchVenueDetail(id), enabled: !!id });
}
export function useVenuePerformancesQuery(id: number) {
  return useQuery({ queryKey: ['venue', id, 'performances'], queryFn: () => fetchVenuePerformances(id), enabled: !!id });
}
export function useMyVenuesQuery() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  return useQuery({ queryKey: ['venues', 'mine'], queryFn: fetchMyVenues, enabled: isLoggedIn });
}
export function useCreateVenueMutation() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: createVenue, onSuccess: () => qc.invalidateQueries({ queryKey: ['venues'] }) });
}
export function useUpdateVenueMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (v: { id: number; body: Partial<VenueCreateBody> }) => updateVenue(v.id, v.body),
    onSuccess: (_d, v) => {
      qc.invalidateQueries({ queryKey: ['venues'] });
      qc.invalidateQueries({ queryKey: ['venue', v.id] });
    },
  });
}
export function useDeleteVenueMutation() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: deleteVenue, onSuccess: () => qc.invalidateQueries({ queryKey: ['venues'] }) });
}
export function useReportVenueMutation() {
  return useMutation({ mutationFn: (v: { id: number; reason: string; detail?: string }) => reportVenue(v.id, { reason: v.reason, detail: v.detail }) });
}

// ---------- 공연 ----------
export function usePerformancesQuery(filter: PerformanceFilter = {}) {
  return useQuery({ queryKey: ['performances', filter], queryFn: () => fetchPerformances(filter) });
}

// ---------- 코스 ----------
export function useCoursesQuery(theme?: string) {
  return useQuery({ queryKey: ['courses', theme ?? null], queryFn: () => fetchCourses(theme) });
}
export function useCourseDetailQuery(id: number) {
  return useQuery({ queryKey: ['course', id], queryFn: () => fetchCourseDetail(id), enabled: !!id });
}

// ---------- 태그 ----------
export function useCommentTagsQuery() {
  return useQuery({ queryKey: ['comment-tags'], queryFn: fetchCommentTags });
}
export function useContentTagsQuery(targetType: string, targetId: number) {
  return useQuery({ queryKey: ['content-tags', targetType, targetId], queryFn: () => fetchContentTags(targetType, targetId), enabled: !!targetId });
}
export function useVoteTagMutation(targetType: string, targetId: number) {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ['content-tags', targetType, targetId] });
  return {
    vote: useMutation({ mutationFn: (commentTagId: number) => voteTag({ commentTagId, targetType, targetId }), onSuccess: invalidate }),
    unvote: useMutation({ mutationFn: (commentTagId: number) => unvoteTag({ commentTagId, targetType, targetId }), onSuccess: invalidate }),
  };
}

// ---------- 리뷰 ----------
export function useReviewsQuery(targetType: string, targetId: number) {
  return useQuery({ queryKey: ['reviews', targetType, targetId], queryFn: () => fetchReviews(targetType, targetId), enabled: !!targetId });
}
export function useMyReviewsQuery() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  return useQuery({ queryKey: ['reviews', 'mine'], queryFn: fetchMyReviews, enabled: isLoggedIn });
}
export function useCreateReviewMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: createReview,
    onSuccess: (_d, vars) => {
      qc.invalidateQueries({ queryKey: ['reviews', vars.targetType, vars.targetId] });
      qc.invalidateQueries({ queryKey: ['venue', vars.targetId] });
    },
  });
}
export function useReportReviewMutation() {
  return useMutation({ mutationFn: (v: { id: number; reason: string; detail?: string }) => reportReview(v.id, { reason: v.reason, detail: v.detail }) });
}
export function useDeleteReviewMutation() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: deleteReview, onSuccess: () => qc.invalidateQueries({ queryKey: ['reviews'] }) });
}

// ---------- 차단 ----------
export function useBlockedUsersQuery() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  return useQuery({ queryKey: ['blocks'], queryFn: fetchBlockedUsers, enabled: isLoggedIn });
}
export function useBlockUserMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: blockUser,
    // 차단 즉시 상대 리뷰가 사라지도록 리뷰 목록도 무효화
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['blocks'] });
      qc.invalidateQueries({ queryKey: ['reviews'] });
    },
  });
}
export function useUnblockUserMutation() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: unblockUser,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['blocks'] });
      qc.invalidateQueries({ queryKey: ['reviews'] });
    },
  });
}

// ---------- 찜 ----------
export function useMyWishlistsQuery() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  return useQuery({ queryKey: ['wishlists'], queryFn: fetchMyWishlists, enabled: isLoggedIn });
}
export function useToggleWishlistMutation() {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ['wishlists'] });
  return {
    add: useMutation({ mutationFn: (v: { targetType: string; targetId: number }) => addWishlist(v.targetType, v.targetId), onSuccess: invalidate }),
    remove: useMutation({ mutationFn: (v: { targetType: string; targetId: number }) => removeWishlist(v.targetType, v.targetId), onSuccess: invalidate }),
  };
}

// ---------- 여행일지 ----------
export function useMyItinerariesQuery() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  return useQuery({ queryKey: ['itineraries'], queryFn: fetchMyItineraries, enabled: isLoggedIn });
}
export function useItineraryQuery(id: number) {
  return useQuery({ queryKey: ['itinerary', id], queryFn: () => fetchItinerary(id), enabled: !!id });
}
export function useItineraryRouteQuery(id: number, priority = 'RECOMMEND') {
  return useQuery({ queryKey: ['itinerary', id, 'route', priority], queryFn: () => fetchItineraryRoute(id, priority), enabled: !!id });
}
export function useCreateItineraryMutation() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: createItinerary, onSuccess: () => qc.invalidateQueries({ queryKey: ['itineraries'] }) });
}
export function useDeleteItineraryMutation() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: deleteItinerary, onSuccess: () => qc.invalidateQueries({ queryKey: ['itineraries'] }) });
}
export function useItineraryItemMutation(itineraryId: number) {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ['itinerary', itineraryId] });
  return {
    add: useMutation({ mutationFn: (body: Parameters<typeof addItineraryItem>[1]) => addItineraryItem(itineraryId, body), onSuccess: invalidate }),
    remove: useMutation({ mutationFn: (itemId: number) => deleteItineraryItem(itineraryId, itemId), onSuccess: invalidate }),
  };
}
