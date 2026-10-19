/**
 * 한국어 사전 — 키의 기준이다.
 *
 * 다른 언어 사전은 이 파일의 키 집합과 정확히 같아야 한다
 * (`node apps/app/scripts/i18n-check.mjs` 가 본다).
 * 키는 화면을 따라 점으로 나눈다: `routes.title`, `route.summary`.
 */
export const ko: Record<string, string> = {
  'common.retry': '다시 시도',
  'common.cancel': '취소',
  'common.close': '닫기',
  'common.error.unknown': '요청에 실패했어요',
  'language.title': '언어',
  'language.hint': '앱 화면과 장소 이름에 쓰이는 언어예요.',
};
