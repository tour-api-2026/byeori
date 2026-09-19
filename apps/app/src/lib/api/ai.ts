import { isAxiosError } from 'axios';
import { api, ApiEnvelope, unwrap } from './client';
import type { ItineraryDetail } from './itineraries';

/** AI 루트 생성 테마. 서버 AiRouteService.CATEGORIES 와 같아야 한다. */
export const AI_THEMES = ['문화', '체험', '전통시장', '공예', '한옥스테이', '맛집', '카페'] as const;

export type AiStop = {
  targetType: 'VENUE' | 'PERFORMANCE';
  targetId: number;
  name: string;
  category: string | null;
  imageUrl: string | null;
  lat: number | null;
  lng: number | null;
  time: string | null;
  reason: string;
};

export type AiRoutePreview = {
  title: string;
  summary: string;
  date: string;
  stops: AiStop[];
  remainingToday: number;
};

export type AiStatus = { enabled: boolean; remainingToday: number | null };

/** 서버가 거절한 이유(한도 소진·후보 부족 등)를 그대로 보여주려고 4xx 본문의 메시지를 꺼낸다. */
async function withServerMessage<T>(p: Promise<T>): Promise<T> {
  try {
    return await p;
  } catch (e) {
    const msg = isAxiosError(e) ? (e.response?.data as ApiEnvelope<unknown> | undefined)?.error?.message : null;
    throw msg ? new Error(msg) : e;
  }
}

export function fetchAiStatus(): Promise<AiStatus> {
  return unwrap<AiStatus>(api.get<ApiEnvelope<AiStatus>>('/ai/routes/status'));
}

export function generateAiRoute(body: {
  lat: number; lng: number; areaName: string; categories: string[]; date: string; regenerate: boolean;
}): Promise<AiRoutePreview> {
  // AI 응답을 기다리는 호출이라 기본 타임아웃보다 길게 둔다
  return withServerMessage(
    unwrap<AiRoutePreview>(api.post<ApiEnvelope<AiRoutePreview>>('/ai/routes', body, { timeout: 40000 })),
  );
}

/** 미리보기를 내 일정으로 저장한다. 추천 이유는 항목 메모로 남긴다. */
export function saveAiRoute(p: AiRoutePreview): Promise<ItineraryDetail> {
  return withServerMessage(
    unwrap<ItineraryDetail>(api.post<ApiEnvelope<ItineraryDetail>>('/itineraries', {
      title: p.title,
      startDate: p.date,
      endDate: p.date,
      sourceType: 'AI',
      items: p.stops.map((s, i) => ({
        targetType: s.targetType,
        targetId: s.targetId,
        visitDate: p.date,
        sortOrder: i,
        plannedTime: s.time,
        memo: s.reason,
      })),
    })),
  );
}
