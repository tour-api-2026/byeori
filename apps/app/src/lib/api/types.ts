export type Venue = {
  /** 공사 콘텐츠 ID. 우리 DB에 없는 장소는 id가 null이라 이 값으로 상세를 연다. */
  tourContentId: string | null;
  id: number;
  name: string;
  address: string;
  category: string | null;
  imageUrl: string | null;
  hanbokDiscount: boolean;
  hanbokDiscountDesc: string | null;
  avgRating: number;
  reviewCount: number;
  source: string;
  lat: number;
  lng: number;
};

/**
 * 목록 카드가 그리는 데 필요한 만큼. 좌표는 쓰지 않는다.
 * 최근 본 장소처럼 Venue 전체를 갖고 있지 않은 자료도 같은 카드로 그리기 위한 것이다.
 */
export type VenueCardItem = Pick<
  Venue,
  'id' | 'tourContentId' | 'name' | 'category' | 'imageUrl' | 'hanbokDiscount' | 'avgRating' | 'reviewCount'
>;

/** 상세 화면을 열 때 한국관광공사 OpenAPI에서 실시간으로 받아온 값. 없으면 null. */
export type VenueLiveInfo = {
  overview: string | null;
  useTime: string | null;
  restDate: string | null;
  infoCenter: string | null;
  parking: string | null;
  homepage: string | null;
};

export type VenueDetail = Venue & {
  description: string | null;
  operatingHours: string | null;
  phone: string | null;
  homepageUrl: string | null;
  visibility: string;
  liveInfo: VenueLiveInfo | null;
};

export type Performance = {
  id: number;
  venueId: number;
  title: string;
  genre: string | null;
  posterImageUrl: string | null;
  startDate: string;
  endDate: string;
  state: string;
  externalBookingUrl: string | null;
  avgRating: number;
  reviewCount: number;
  source: string;
  traditional: boolean;
  lat: number | null;
  lng: number | null;
  /** 공사 축제만. 상세를 열 때 실시간으로 받아온 소개글. */
  overview: string | null;
};

export type CommentTag = { id: number; name: string };

export type CourseItem = {
  id: number;
  targetType: 'VENUE' | 'PERFORMANCE';
  targetId: number;
  name: string | null;
  imageUrl: string | null;
  sortOrder: number;
  recommendedTime: string | null;
  note: string | null;
};

export type CuratedCourse = {
  id: number;
  title: string;
  description: string | null;
  theme: string | null;
  coverImageUrl: string | null;
  durationHours: number | null;
};

export type CuratedCourseDetail = CuratedCourse & { items: CourseItem[] };
