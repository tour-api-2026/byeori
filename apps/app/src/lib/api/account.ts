import { api, ApiEnvelope, unwrap } from './client';

/** 회원 탈퇴. 개인 데이터는 파기되고, 등록한 장소는 익명 처리되어 서비스에 남는다. */
export function deleteAccount(): Promise<void> {
  return unwrap<void>(api.delete<ApiEnvelope<void>>('/users/me'));
}
