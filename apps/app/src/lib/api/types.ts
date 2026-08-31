export type Venue = {
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
