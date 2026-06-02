import { api, unwrap, ApiEnvelope } from './client';
import { CuratedCourse, CuratedCourseDetail } from './types';

export function fetchCourses(theme?: string): Promise<CuratedCourse[]> {
  return unwrap<CuratedCourse[]>(api.get<ApiEnvelope<CuratedCourse[]>>('/curated-courses', { params: { theme } }));
}

export function fetchCourseDetail(id: number): Promise<CuratedCourseDetail> {
  return unwrap<CuratedCourseDetail>(api.get<ApiEnvelope<CuratedCourseDetail>>(`/curated-courses/${id}`));
}
