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
/** 오류 화면에 붙일 원인 단서. 코드와 메시지만 쓰고 토큰 같은 값은 담지 않는다. */
function detail(e: any): string {
  const code = e?.code ? String(e.code) : '';
  const msg = e?.message ? String(e.message) : '';
  const parts = [code, msg].filter(Boolean);
  return parts.length ? ` (${parts.join(': ')})` : '';
}

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
    // SDK가 주는 코드를 남긴다. 이걸 삼키면 키 해시 불일치(KOE006)인지 네트워크인지
    // 사용자도 우리도 구분할 수 없다 — 실제로 원인을 찾는 데 한참 걸렸다.
    throw new Error(`카카오 로그인에 실패했습니다.${detail(e)}`);
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

/**
 * 구글 로그인.
 *
 * 네이티브는 SDK, 웹은 인가 플로우 — 카카오와 같은 갈래다.
 * 네이티브에서 AuthSession 을 쓸 수 없는 이유는 구글이 안드로이드 앱의 커스텀 URI 스킴
 * (app:// 같은 것)을 더 이상 받지 않기 때문이다. 네이티브 SDK 가 유일한 경로다.
 *
 * SDK 에 넘기는 건 '웹' 클라이언트 ID 다. 앱 자체는 패키지명 + 서명 지문으로 식별되므로
 * Android 클라이언트 ID 를 코드에서 쓸 일은 없다(콘솔에 등록만 돼 있으면 된다).
 * 다만 id_token 의 aud 가 어느 쪽으로 오는지 문서에 명시가 없어, 백엔드는 웹·Android
 * 둘 다 허용한다(GOOGLE_CLIENT_IDS).
 *
 * Expo config plugin 은 넣지 않는다 — 그 플러그인이 하는 일은 iOS Info.plist 에 URL 스킴을
 * 더하는 것뿐이고(firebase 미사용 경로), 안드로이드는 autolinking 으로 충분하다.
 * iOS 를 시작할 때 실제 iOS 클라이언트 ID 로 플러그인을 추가하면 된다.
 */
export async function loginGoogle() {
  if (Platform.OS === 'web') return loginGoogleWeb();

  const clientId = process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID;
  if (!clientId) {
    throw new Error('구글 로그인 설정이 필요해요. (EXPO_PUBLIC_GOOGLE_CLIENT_ID 미설정)');
  }

  let mod: typeof import('@react-native-google-signin/google-signin');
  try {
    mod = require('@react-native-google-signin/google-signin');
  } catch {
    throw new Error('구글 로그인은 설치형 앱에서만 가능해요.');
  }
  const { GoogleSignin } = mod;

  try {
    GoogleSignin.configure({ webClientId: clientId });
    await GoogleSignin.hasPlayServices();
    const res = await GoogleSignin.signIn();
    if (res.type === 'cancelled') throw new AuthCancelledError();

    const idToken = res.data.idToken;
    if (!idToken) throw new Error('구글 id_token을 받지 못했습니다.');
    return exchangeWithBackend({ provider: 'google', idToken });
  } catch (e: any) {
    if (e instanceof AuthCancelledError) throw e;
    // 구버전 SDK 는 취소를 예외로 던진다
    if (/cancel/i.test(String(e?.code ?? e?.message ?? ''))) throw new AuthCancelledError();
    throw new Error(`구글 로그인에 실패했습니다.${detail(e)}`);
  }
}

/** 웹 구글 로그인: 브라우저 인가 플로우로 id_token 을 받는다. */
async function loginGoogleWeb() {
  const clientId = process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID;
  if (!clientId) {
    throw new Error('구글 로그인 설정이 필요해요. (EXPO_PUBLIC_GOOGLE_CLIENT_ID 미설정)');
  }

  const request = new AuthSession.AuthRequest({
    clientId,
    redirectUri: activeRedirectUri(),
    responseType: AuthSession.ResponseType.IdToken,
    scopes: ['openid', 'profile', 'email'],
    // PKCE는 인가 코드 플로우 전용이다. 기본값(true)대로 code_challenge 를 붙이면
    // 구글이 id_token 요청을 400 invalid_request 로 거부한다.
    usePKCE: false,
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
