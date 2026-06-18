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

const GOOGLE_DISCOVERY: AuthSession.DiscoveryDocument = {
  authorizationEndpoint: 'https://accounts.google.com/o/oauth2/v2/auth',
  tokenEndpoint: 'https://oauth2.googleapis.com/token',
};

// 백엔드 /auth/social 호출 → 세션 저장
async function exchangeWithBackend(body: {
  provider: 'kakao' | 'google';
  code?: string;
  idToken?: string;
  accessToken?: string;
  redirectUri?: string;
}) {
  const res = await api.post<ApiEnvelope<Session>>('/auth/social', body);
  if (!res.data.success || !res.data.data?.accessToken) {
    throw new Error(res.data.error?.message ?? '로그인에 실패했습니다.');
  }
  await useAuthStore.getState().setSession(res.data.data);
  return res.data.data.user;
}

/**
 * 카카오 로그인: 네이티브 SDK로 accessToken 획득 → 백엔드 검증.
 * 네이티브 모듈이라 Expo Go/웹에선 동작하지 않음(APK/dev build 전용).
 * lazy require로 모듈 로드 — Expo Go 브라우징은 영향받지 않게.
 */
export async function loginKakao() {
  let KakaoLogin: typeof import('@react-native-seoul/kakao-login');
  try {
    KakaoLogin = require('@react-native-seoul/kakao-login');
  } catch {
    throw new Error('카카오 로그인은 설치형 앱(APK)에서만 가능해요.');
  }
  try {
    const token = await KakaoLogin.login();
    if (!token?.accessToken) throw new Error('카카오 토큰을 받지 못했습니다.');
    return exchangeWithBackend({ provider: 'kakao', accessToken: token.accessToken });
  } catch (e: any) {
    // 사용자가 카카오 화면에서 취소
    const msg = String(e?.message ?? e?.code ?? '');
    if (/cancel/i.test(msg) || e?.code === 'E_CANCELLED_OPERATION') throw new AuthCancelledError();
    if (e instanceof AuthCancelledError) throw e;
    throw new Error('카카오 로그인에 실패했습니다. (앱에서만 동작)');
  }
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
