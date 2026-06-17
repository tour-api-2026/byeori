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

export type VenueDetail = Venue & {
  description: string | null;
  operatingHours: string | null;
  phone: string | null;
  homepageUrl: string | null;
  visibility: string;
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
