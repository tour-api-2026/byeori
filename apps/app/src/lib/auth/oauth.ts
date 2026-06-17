import * as AuthSession from 'expo-auth-session';
import { api, type ApiEnvelope } from '@/lib/api/client';
import { useAuthStore, type Session } from '@/lib/store/authStore';

// scheme은 app.json의 "scheme": "app" 과 일치해야 한다.
// Expo Go에서는 exp:// 프록시 URI, 개발/프로덕션 빌드에서는 app:// 네이티브 URI가 생성된다.
const redirectUri = AuthSession.makeRedirectUri({ scheme: 'app' });

// 콘솔(카카오/구글) 등록용으로 실제 사용되는 redirectUri를 출력
console.log('[oauth] redirectUri =', redirectUri);

export function getRedirectUri() {
  return redirectUri;
}

class AuthCancelledError extends Error {
  constructor() {
    super('로그인이 취소되었습니다.');
    this.name = 'AuthCancelledError';
  }
}
export function isCancelled(e: unknown) {
  return e instanceof AuthCancelledError;
}

const KAKAO_DISCOVERY: AuthSession.DiscoveryDocument = {
  authorizationEndpoint: 'https://kauth.kakao.com/oauth/authorize',
  tokenEndpoint: 'https://kauth.kakao.com/oauth/token',
};

const GOOGLE_DISCOVERY: AuthSession.DiscoveryDocument = {
  authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
  tokenEndpoint: 'https://oauth2.googleapis.com/token',
};

// 백엔드 /auth/social 호출 → 세션 저장
async function exchangeWithBackend(body: {
  provider: 'kakao' | 'google';
  code?: string;
  idToken?: string;
  redirectUri?: string;
}) {
  const res = await api.post<ApiEnvelope<Session>>('/auth/social', body);
  if (!res.data.success || !res.data.data?.accessToken) {
    throw new Error(res.data.error?.message ?? '로그인에 실패했습니다.');
  }
  await useAuthStore.getState().setSession(res.data.data);
  return res.data.data.user;
}

/** 카카오 로그인: 인가코드(code) 획득 → 백엔드 교환 */
export async function loginKakao() {
  const clientId = process.env.EXPO_PUBLIC_KAKAO_REST_KEY;
  if (!clientId) {
    throw new Error('카카오 로그인 설정이 필요해요. (EXPO_PUBLIC_KAKAO_REST_KEY 미설정)');
  }

  const request = new AuthSession.AuthRequest({
    clientId,
    redirectUri,
    responseType: AuthSession.ResponseType.Code,
    // 카카오 토큰 교환은 백엔드(redirectUri 포함)에서 수행하므로 PKCE 비활성화
    usePKCE: false,
    scopes: ['account_email', 'profile_nickname'],
  });

  const result = await request.promptAsync(KAKAO_DISCOVERY);
  if (result.type === 'cancel' || result.type === 'dismiss') throw new AuthCancelledError();
  if (result.type !== 'success' || !result.params.code) {
    throw new Error(result.type === 'error' ? (result.error?.message ?? '카카오 인증 오류') : '카카오 인증에 실패했습니다.');
  }

  return exchangeWithBackend({ provider: 'kakao', code: result.params.code, redirectUri });
}

/** 구글 로그인: id_token 획득 → 백엔드 교환 */
export async function loginGoogle() {
  const clientId = process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID;
  if (!clientId) {
    throw new Error('구글 로그인 설정이 필요해요. (EXPO_PUBLIC_GOOGLE_CLIENT_ID 미설정)');
  }

  const request = new AuthSession.AuthRequest({
    clientId,
    redirectUri,
    responseType: AuthSession.ResponseType.IdToken,
    scopes: ['openid', 'profile', 'email'],
    // id_token implicit 플로우는 nonce 필수
    extraParams: { nonce: Math.random().toString(36).slice(2) + Date.now().toString(36) },
  });

  const result = await request.promptAsync(GOOGLE_DISCOVERY);
  if (result.type === 'cancel' || result.type === 'dismiss') throw new AuthCancelledError();
  if (result.type !== 'success') {
    throw new Error(result.type === 'error' ? (result.error?.message ?? '구글 인증 오류') : '구글 인증에 실패했습니다.');
  }

  const idToken = result.params.id_token;
  if (!idToken) throw new Error('구글 id_token을 받지 못했습니다.');

  return exchangeWithBackend({ provider: 'google', idToken });
}
