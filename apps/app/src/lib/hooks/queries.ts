import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { blockUser, fetchBlockedUsers, unblockUser } from '../api/account';
import { fetchAiStatus } from '../api/ai';
import { fetchCourseDetail, fetchCourses } from '../api/courses';
import {
  addItineraryItem, addPlaceItem, createItinerary, deleteItinerary, deleteItineraryItem,
  fetchItinerary, fetchItineraryRoute, fetchMyItineraries, type KakaoPlace, updateItinerary, updateItineraryItem } from '../api/itineraries';
import { fetchPerformance, fetchPerformances, PerformanceFilter, fetchNearbyPerformances } from '../api/performances';
import { createReview, deleteReview, fetchMyReviews, fetchReviews, reportReview } from '../api/reviews';
import { fetchContentTags, unvoteTag, voteTag } from '../api/tags';
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
    // 검색어가 바뀌는 동안 목록이 비면 화면이 접혔다 펴진다. 새 결과가 올 때까지 이전 것을 둔다.
    placeholderData: keepPreviousData,
  });
}

export function useNearbyVenuesQuery(p: NearbyParams | null) {
  return useQuery({
    queryKey: ['venues-nearby', p],
    queryFn: () => fetchNearbyVenues(p as NearbyParams),
    enabled: !!p,
    staleTime: 5 * 60 * 1000,
    // 지역 칩을 누르면 새 조회가 끝날 때까지 목록이 비어 아래 내용이 줄었다가
    // 되돌아온다(칩이 156px 내려갔다 올라왔다). 이전 결과를 유지해 높이를 지킨다.
    // 지도에서도 이동 중 마커가 사라지지 않아 덜 깜빡인다.
    placeholderData: keepPreviousData,
  });
}
export function useVenueDetailQuery(id: number | string) {
  return useQuery({ queryKey: ['venue', id], queryFn: () => fetchVenueDetail(id), enabled: !!id });
}
/** 좌표 주변 행사. 좌표가 없으면 호출하지 않는다. */
export function useNearbyPerformancesQuery(p: { lat?: number | null; lng?: number | null } | null) {
  const on = p?.lat != null && p?.lng != null;
  return useQuery({
    queryKey: ['performances-nearby', p?.lat, p?.lng],
    queryFn: () => fetchNearbyPerformances({ lat: Number(p!.lat), lng: Number(p!.lng) }),
    enabled: on,
  });
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
export function usePerformanceQuery(id: number) {
  return useQuery({ queryKey: ['performance', id], queryFn: () => fetchPerformance(id), enabled: !!id });
}
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
/** AI 루트 사용 가능 여부. 서버에 키가 없으면 enabled=false 라 버튼을 숨긴다. */
export function useAiStatusQuery() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  return useQuery({ queryKey: ['ai-status', isLoggedIn], queryFn: fetchAiStatus, staleTime: 60_000 });
}

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
/** 루트 이름·기간 수정. 성공하면 상세와 목록을 모두 새로 받는다(목록 카드에도 제목·기간이 보인다). */
export function useUpdateItineraryMutation(id: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { title?: string; startDate?: string; endDate?: string }) => updateItinerary(id, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['itinerary', id] });
      qc.invalidateQueries({ queryKey: ['itineraries'] });
    },
  });
}

export function useItineraryItemMutation(itineraryId: number) {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: ['itinerary', itineraryId] });
  return {
    add: useMutation({ mutationFn: (body: Parameters<typeof addItineraryItem>[1]) => addItineraryItem(itineraryId, body), onSuccess: invalidate }),
    addPlace: useMutation({
      mutationFn: (v: { place: KakaoPlace; visitDate: string; sortOrder?: number }) =>
        addPlaceItem(itineraryId, v.place, v.visitDate, v.sortOrder),
      onSuccess: invalidate,
    }),
    /** 방문일·시간·순서·메모 수정. 보내지 않은 필드는 서버가 건드리지 않는다. */
    update: useMutation({
      mutationFn: (p: { itemId: number; visitDate?: string; sortOrder?: number; plannedTime?: string | null; memo?: string | null }) =>
        updateItineraryItem(itineraryId, p.itemId, p),
      onSuccess: invalidate,
    }),
    remove: useMutation({ mutationFn: (itemId: number) => deleteItineraryItem(itineraryId, itemId), onSuccess: invalidate }),
  };
}
