/**
 * 지역 칩의 중심 좌표.
 *
 * 지역을 고르면 이 좌표로 공사 OpenAPI를 실시간 조회한다(/venues/nearby).
 * 전에는 저장 목록 상위 50~60건을 주소 문자열로 걸렀는데, 그 목록이 평점순
 * 정렬 탓에 전부 서울이라 서울 외 지역을 누르면 아무것도 나오지 않았다.
 *
 * 반경은 공사 API 상한인 20km까지 쓴다. 제주는 제주시와 서귀포를 함께 담도록
 * 섬 중앙을 기준으로 잡았다.
 */
export const REGIONS = ['전체', '서울', '부산', '대구', '전주', '제주'] as const;

export type RegionSpot = { lat: number; lng: number; radius: number };

export const REGION_CENTER: Record<string, RegionSpot> = {
  // 홈 화면은 서울 구 단위 칩을 쓴다(반경은 구 하나를 덮을 정도).
  종로구: { lat: 37.573, lng: 126.9794, radius: 3000 },
  중구: { lat: 37.5636, lng: 126.9976, radius: 3000 },
  용산구: { lat: 37.5326, lng: 126.9906, radius: 3000 },

  // 검색 화면은 시·도 단위 칩을 쓴다.
  서울: { lat: 37.5665, lng: 126.978, radius: 20000 },
  부산: { lat: 35.1796, lng: 129.0756, radius: 20000 },
  대구: { lat: 35.8714, lng: 128.6014, radius: 20000 },
  전주: { lat: 35.8242, lng: 127.148, radius: 15000 },
  제주: { lat: 33.4183, lng: 126.5622, radius: 20000 },
};

/** '전체'이거나 좌표가 없는 지역이면 null — 호출부는 저장 목록을 그대로 쓴다. */
export function regionSpot(region: string): RegionSpot | null {
  return REGION_CENTER[region] ?? null;
}
