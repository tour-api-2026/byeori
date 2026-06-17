import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import Constants from 'expo-constants';
import { Platform } from 'react-native';
import { useAuthStore } from '@/lib/store/authStore';

// 실기기(Expo Go)에서는 dev 머신의 IP로 API를 호출해야 한다.
// Expo가 알려주는 hostUri(예: "192.168.0.5:8081")에서 호스트를 추출해 :8080에 붙인다.
function resolveHost(): string {
  if (Platform.OS === 'web') return 'localhost';
  const hostUri =
    Constants.expoConfig?.hostUri ??
    (Constants as any).expoGoConfig?.debuggerHost ??
    (Constants as any).manifest2?.extra?.expoGo?.debuggerHost ??
    '';
  const host = String(hostUri).split(':')[0];
  return host || 'localhost';
}

// EXPO_PUBLIC_API_URL이 설정되면(웹·네이티브 공통) 그 주소를 사용,
// 없으면 dev에서 호스트를 추론한다.
export const API_BASE =
  process.env.EXPO_PUBLIC_API_URL ?? `http://${resolveHost()}:8080/api/v1`;

export const api = axios.create({
  baseURL: API_BASE,
  // bypass-tunnel-reminder: localtunnel(loca.lt) 안내 페이지 우회(데모 빌드용)
  headers: { 'Accept-Language': 'ko', 'bypass-tunnel-reminder': 'true' },
  timeout: 15000,
});

// 공통 응답 봉투 { success, data, error } → data 언랩
export type ApiEnvelope<T> = { success: boolean; data: T; error: { code: string; message: string } | null };
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };

export async function unwrap<T>(promise: Promise<{ data: ApiEnvelope<T> }>): Promise<T> {
  const res = await promise;
  if (!res.data.success) {
    throw new Error(res.data.error?.message ?? '요청 실패');
  }
  return res.data.data;
}

// ---------- 인증 인터셉터 ----------
// 요청: 로그인 상태면 Authorization Bearer 첨부 (기존 기본 헤더는 유지)
api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// refresh 전용 plain axios (api 인터셉터를 타지 않아 순환·무한루프 방지)
const refreshClient = axios.create({
  baseURL: API_BASE,
  headers: { 'Accept-Language': 'ko', 'bypass-tunnel-reminder': 'true' },
  timeout: 15000,
});

// 동시 401 대비: 갱신은 1회만 수행하고 나머지는 같은 Promise를 공유
let refreshing: Promise<string | null> | null = null;

async function runRefresh(): Promise<string | null> {
  const store = useAuthStore.getState();
  const refreshToken = store.refreshToken;
  if (!refreshToken) return null;
  try {
    const res = await refreshClient.post<ApiEnvelope<{ accessToken: string; refreshToken: string }>>(
      '/auth/token/refresh',
      { refreshToken },
    );
    if (!res.data.success || !res.data.data?.accessToken) return null;
    const { accessToken, refreshToken: newRefresh } = res.data.data;
    await useAuthStore.getState().setTokens({ accessToken, refreshToken: newRefresh ?? refreshToken });
    return accessToken;
  } catch {
    return null;
  }
}

// 응답: 401 → 1회 refresh → 성공 시 원요청 재시도, 실패 시 로그아웃
api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const status = error.response?.status;

    // refresh 엔드포인트 자체 실패 또는 재시도 이력은 그대로 던짐(무한루프 방지)
    if (
      status !== 401 ||
      !original ||
      original._retry ||
      original.url?.includes('/auth/token/refresh') ||
      original.url?.includes('/auth/social')
    ) {
      return Promise.reject(error);
    }

    // 토큰이 애초에 없으면(비로그인 보호 요청) refresh 시도하지 않음
    if (!useAuthStore.getState().accessToken) {
      return Promise.reject(error);
    }

    original._retry = true;
    if (!refreshing) refreshing = runRefresh();
    const newToken = await refreshing.finally(() => {
      refreshing = null;
    });

    if (!newToken) {
      await useAuthStore.getState().logout();
      return Promise.reject(error);
    }

    original.headers = original.headers ?? {};
    original.headers.Authorization = `Bearer ${newToken}`;
    return api(original);
  },
);
