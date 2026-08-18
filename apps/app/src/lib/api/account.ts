import { api, ApiEnvelope, unwrap } from './client';

/** 회원 탈퇴. 개인 데이터는 파기되고, 등록한 장소는 익명 처리되어 서비스에 남는다. */
export function deleteAccount(): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>('/users/me'));
}

/** 내가 차단한 사용자 id 목록. */
export function fetchBlockedUsers(): Promise<number[]> {
  return unwrap<number[]>(api.get<ApiEnvelope<number[]>>('/users/me/blocks'));
}

/** 사용자 차단 — 차단하면 그 사용자의 리뷰가 목록에서 보이지 않는다. */
export function blockUser(userId: number): Promise<void> {
  return unwrap<void>(api.post<ApiEnvelope<void>>('/users/me/blocks', { userId }));
}

export function unblockUser(userId: number): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>(`/users/me/blocks/${userId}`));
}
