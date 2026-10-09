import { api, unwrap, ApiEnvelope } from './client';

/** 온보딩 화면의 낱말 그대로. 서버가 아는 값만 받는다(InterestCatalog). */
export type Interests = { topics: string[]; regions: string[] };

export function fetchMyInterests(): Promise<Interests> {
  return unwrap<Interests>(api.get<ApiEnvelope<Interests>>('/users/me/interests'));
}

/** 통째로 교체. 더하기가 아니라 화면이 들고 있는 전체 목록으로 덮는다. */
export function saveMyInterests(body: Interests): Promise<Interests> {
  return unwrap<Interests>(api.put<ApiEnvelope<Interests>>('/users/me/interests', body));
}
