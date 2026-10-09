import { api, unwrap, ApiEnvelope } from './client';
import type { LinkedAccount } from '@/lib/auth/oauth';

export type { LinkedAccount };

/** 내 계정에 붙어 있는 로그인 수단. */
export function fetchLinkedAccounts(): Promise<LinkedAccount[]> {
  return unwrap<LinkedAccount[]>(api.get<ApiEnvelope<LinkedAccount[]>>('/users/me/social'));
}

/** 연결 해제. 마지막 하나는 서버가 거부한다. */
export function unlinkAccount(provider: string): Promise<LinkedAccount[]> {
  return unwrap<LinkedAccount[]>(api.delete<ApiEnvelope<LinkedAccount[]>>(`/users/me/social/${provider}`));
}
