import * as AuthSession from 'expo-auth-session';
import { Platform } from 'react-native';
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

/**
 * 실제로 인증 요청에 쓸 redirect_uri.
 * 웹에서는 makeRedirectUri가 현재 경로까지 붙여(예: /login) 콘솔 등록값과 어긋날 수 있으므로
 * 오리진만 쓴다 — 카카오·구글 콘솔에는 https://byeori.ernebi.org 를 등록한다.
 */
function activeRedirectUri(): string {
  return Platform.OS === 'web' ? window.location.origin : redirectUri;
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

const KAKAO_DISCOVERY: AuthSession.DiscoveryDocument = {
  authorizationEndpoint: 'https://kauth.kakao.com/oauth/authorize',
  tokenEndpoint: 'https://kauth.kakao.com/oauth/token',
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
 * 웹 카카오 로그인: 인가 코드 플로우.
 * 네이티브 SDK를 쓸 수 없으므로 브라우저에서 카카오 인증을 거쳐 code를 받고,
 * 백엔드가 그 code를 토큰으로 교환한다(AuthService.socialLogin의 "웹 OAuth 경로").
 *
 * redirect_uri는 카카오 콘솔에 등록된 값과 **정확히** 같아야 한다.
 * 배포 오리진을 그대로 쓰므로 콘솔에는 https://byeori.ernebi.org 형태로 등록한다.
 */
async function loginKakaoWeb() {
  const clientId = process.env.EXPO_PUBLIC_KAKAO_REST_KEY;
  if (!clientId) {
    throw new Error('카카오 웹 로그인 설정이 필요해요. (EXPO_PUBLIC_KAKAO_REST_KEY 미설정)');
  }
  const webRedirectUri = activeRedirectUri();

  const request = new AuthSession.AuthRequest({
    clientId,
    redirectUri: webRedirectUri,
    responseType: AuthSession.ResponseType.Code,
    scopes: [],
    // 백엔드가 code를 그대로 교환하므로 PKCE verifier를 넘길 수단이 없다.
    usePKCE: false,
  });

  const result = await request.promptAsync(KAKAO_DISCOVERY);
  if (result.type === 'cancel' || result.type === 'dismiss') throw new AuthCancelledError();
  if (result.type !== 'success') {
    throw new Error(
      result.type === 'error'
        ? (result.error?.message ?? '카카오 인증 오류')
        : '카카오 인증에 실패했습니다.',
    );
  }
  const code = result.params.code;
  if (!code) throw new Error('카카오 인가 코드를 받지 못했습니다.');

  return exchangeWithBackend({ provider: 'kakao', code, redirectUri: webRedirectUri });
}

/**
 * 카카오 로그인.
 * 네이티브는 SDK로 accessToken을 받아 백엔드에 검증시키고(lazy require — Expo Go 브라우징 영향 없음),
 * 웹은 네이티브 모듈을 쓸 수 없으므로 인가 코드 플로우로 우회한다.
 */
export async function loginKakao() {
  if (Platform.OS === 'web') return loginKakaoWeb();

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

/**
 * 아이디/비밀번호 로그인(현재 관리자 계정 전용).
 * 카카오/구글과 달리 네이티브 모듈·외부 인증이 필요 없어 Expo Go·웹에서도 동작.
 */
export async function loginAdmin(id: string, password: string) {
  const res = await api.post<ApiEnvelope<Session>>('/auth/login', { id: id?.trim(), password });
  if (!res.data.success || !res.data.data?.accessToken) {
    throw new Error(res.data.error?.message ?? '로그인에 실패했습니다.');
  }
  await useAuthStore.getState().setSession(res.data.data);
  return res.data.data.user;
}

/** 구글 로그인: id_token 획득 → 백엔드 교환 */
export async function loginGoogle() {
  const clientId = process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID;
  if (!clientId) {
    throw new Error('구글 로그인 설정이 필요해요. (EXPO_PUBLIC_GOOGLE_CLIENT_ID 미설정)');
  }

  const request = new AuthSession.AuthRequest({
    clientId,
    redirectUri: activeRedirectUri(),
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
