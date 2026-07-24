import { Platform } from 'react-native';
import { api, API_BASE, type ApiEnvelope } from './client';

export type PickedImage = { uri: string; mimeType?: string | null; fileName?: string | null };

// 백엔드는 호스트 없는 경로(/api/v1/uploads/images/{id})를 돌려준다 → API origin을 붙여 절대 URL로.
const ORIGIN = API_BASE.replace(/\/api\/v1\/?$/, '');
function toAbsolute(url: string) {
  return /^https?:\/\//i.test(url) ? url : ORIGIN + url;
}

/** 이미지 1장을 업로드하고 절대 URL을 반환한다. (웹·네이티브 공통) */
export async function uploadImage(img: PickedImage): Promise<string> {
  const type = img.mimeType ?? 'image/jpeg';
  const ext = (type.split('/')[1] ?? 'jpg').replace('jpeg', 'jpg');
  const name = img.fileName ?? `photo.${ext}`;

  const form = new FormData();
  if (Platform.OS === 'web') {
    // 웹: blob/data URL → Blob 으로 변환해 첨부
    const blob = await (await fetch(img.uri)).blob();
    form.append('file', blob, name);
  } else {
    // 네이티브: RN FormData 파일 파트
    form.append('file', { uri: img.uri, name, type } as unknown as Blob);
  }

  const res = await api.post<ApiEnvelope<{ url: string }>>('/uploads/images', form);
  if (!res.data.success || !res.data.data?.url) {
    throw new Error(res.data.error?.message ?? '이미지 업로드에 실패했습니다.');
  }
  return toAbsolute(res.data.data.url);
}
