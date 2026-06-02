import { useQuery } from '@tanstack/react-query';
import { fetchCourseDetail, fetchCourses } from '../api/courses';
import { fetchPerformances, PerformanceFilter } from '../api/performances';
import { fetchCommentTags } from '../api/tags';
import { fetchVenueDetail, fetchVenuePerformances, fetchVenues, VenueFilter } from '../api/venues';

export function useVenuesQuery(filter: VenueFilter = {}) {
  return useQuery({ queryKey: ['venues', filter], queryFn: () => fetchVenues(filter) });
}

export function useVenueDetailQuery(id: number) {
  return useQuery({ queryKey: ['venue', id], queryFn: () => fetchVenueDetail(id), enabled: !!id });
}

export function useVenuePerformancesQuery(id: number) {
  return useQuery({ queryKey: ['venue', id, 'performances'], queryFn: () => fetchVenuePerformances(id), enabled: !!id });
}

export function usePerformancesQuery(filter: PerformanceFilter = {}) {
  return useQuery({ queryKey: ['performances', filter], queryFn: () => fetchPerformances(filter) });
}

export function useCoursesQuery(theme?: string) {
  return useQuery({ queryKey: ['courses', theme ?? null], queryFn: () => fetchCourses(theme) });
}

export function useCourseDetailQuery(id: number) {
  return useQuery({ queryKey: ['course', id], queryFn: () => fetchCourseDetail(id), enabled: !!id });
}

export function useCommentTagsQuery() {
  return useQuery({ queryKey: ['comment-tags'], queryFn: fetchCommentTags });
}
