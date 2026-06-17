import axios from 'axios';
import { Platform } from 'react-native';

// 데모: 웹은 localhost, 네이티브 기기는 LAN IP로 교체 필요
const HOST = Platform.OS === 'web' ? 'localhost' : 'localhost';
export const API_BASE = `http://${HOST}:8080/api/v1`;

export const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Accept-Language': 'ko' },
  timeout: 10000,
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
