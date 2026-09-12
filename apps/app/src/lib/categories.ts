import type { Venue } from './api/types';

/**
 * 지도·검색 화면의 카테고리 칩.
 *
 * 두 화면이 같은 목록을 각자 들고 있었다. 한쪽만 고치면 화면마다 칩이 달라지므로
 * 여기 한 곳에서만 정의한다. 값은 서버가 돌려주는 category 문자열과 같아야 한다
 * (CategoryMapper.fromTour 참고).
 */
export const CATEGORIES = ['전체', '문화', '카페', '체험', '맛집', '한복'] as const;

/** 한국 본토·제주를 덮는 좌표 범위. */
const KOREA = { minLat: 33, maxLat: 38.7, minLng: 124.5, maxLng: 132 };

/**
 * 지도에 찍어도 되는 좌표인지.
 *
 * 공사 데이터에는 지오코딩에 실패한 자리표시자 좌표가 섞여 있다(예: 19.69,117.99).
 * 걸러내지 않으면 지도가 외국까지 넓어진다.
 */
export function inKorea(v: Pick<Venue, 'lat' | 'lng'>): boolean {
  if (v.lat == null || v.lng == null) return false;
  const lat = Number(v.lat);
  const lng = Number(v.lng);
  return lat >= KOREA.minLat && lat <= KOREA.maxLat && lng >= KOREA.minLng && lng <= KOREA.maxLng;
}
