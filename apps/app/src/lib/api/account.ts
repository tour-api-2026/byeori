import { isAxiosError } from 'axios';
import type { AuthUser } from '@/lib/store/authStore';
import { api, ApiEnvelope, unwrap } from './client';

/**
 * 닉네임·프로필 사진 수정. profileImageUrl 이 null 이면 사진을 지운다.
 * 서버가 거절한 이유(닉네임 길이 등)를 그대로 보여주려고 4xx 본문의 메시지를 꺼낸다.
 */
export async function updateProfile(body: { name: string; profileImageUrl: string | null }): Promise<AuthUser> {
  try {
    return await unwrap<AuthUser>(api.patch<ApiEnvelope<AuthUser>>('/users/me', body));
  } catch (e) {
    const msg = isAxiosError(e) ? (e.response?.data as ApiEnvelope<unknown> | undefined)?.error?.message : null;
    throw msg ? new Error(msg) : e;
  }
}

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
