import type { Venue } from './api/types';

/**
 * 지도·검색 화면의 카테고리 칩.
 *
 * 두 화면이 같은 목록을 각자 들고 있었다. 한쪽만 고치면 화면마다 칩이 달라지므로
 * 여기 한 곳에서만 정의한다. 값은 서버가 돌려주는 category 문자열과 같아야 한다
 * (CategoryMapper.fromTour 참고).
 *
 * 전통시장·공예는 공사 쇼핑(38)에서, 한옥스테이는 숙박(32)에서 전통문화와 닿는
 * 소분류만 골라낸 것이다. '한복'은 공사 분류가 아니라 한복 착용 혜택 표시다.
 * '필수명소'는 외국인이 꼭 들르는 곳(올리브영·다이소·백화점·한강공원)으로, 공사 분류가
 * 쓸모없어 상호명으로 고른 것이다(CategoryMapper.isMustVisit 참고).
 */
export const CATEGORIES = [
  '전체', '필수명소', '문화', '체험', '맛집', '카페', '전통시장', '공예', '한옥스테이', '한복',
] as const;

/**
 * '전체' 칩의 값. 필터를 걸지 않는다는 뜻이라 API 에는 보내지 않는다.
 * 화면 파일에 한국어 리터럴을 두지 않으려고 이름을 붙여 뒀다(i18n-check 가 본다).
 */
export const ALL = '전체';

/** 장소 고르기 창의 칩. 순서가 위 목록과 달라(맛집·카페를 앞에 둔다) 따로 둔다. 값은 같은 한국어다. */
export const PICKER_CATEGORIES = [
  '전체', '필수명소', '문화', '맛집', '카페', '전통시장', '체험', '공예', '한옥스테이',
] as const;

/** 카카오 장소의 category 값 중 아이콘을 가르는 둘. 화면 파일에 한국어 리터럴을 두지 않으려고 이름을 붙였다. */
export const FOOD = '맛집';
export const CAFE = '카페';

/** 한복 태그·카테고리의 값. 한복 혜택이 있으면 카테고리와 별개로 이 태그를 앞에 붙인다. */
export const HANBOK = '한복';

/** 홈의 키워드 칩. 값은 카테고리와 같은 한국어다. */
export const HOME_KEYWORDS = ['전체', '문화', '카페', '체험', '맛집'] as const;

/**
 * 지도 핀의 카테고리별 이모지. 키는 서버가 주는 category **값**이라 번역하지 않는다.
 * 지도 스크립트(WebView 안의 문자열)가 쓰는데, 거기서는 한국어 리터럴을 직접 둘 수 없어
 * (i18n-check 가 본다) 이 표를 JSON 으로 심어 보낸다.
 */
export const CATEGORY_EMOJI: Record<string, string> = {
  필수명소: '🛍️',
  맛집: '🍽️',
  카페: '☕',
  문화: '🎭',
  체험: '🎨',
  전통시장: '🏮',
  공예: '🪡',
  한옥스테이: '🏯',
  한복: '👘',
};

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
