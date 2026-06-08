import axios from 'axios';
import Constants from 'expo-constants';
import { Platform } from 'react-native';

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

// 빌드 시 EXPO_PUBLIC_API_URL이 주입되면(설치형 APK·터널) 그 주소를 우선 사용,
// 아니면 dev(Expo Go/LAN)에서 호스트를 추론한다.
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
