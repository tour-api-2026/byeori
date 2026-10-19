export type TFn = (key: string, vars?: Record<string, unknown>) => string;

/**
 * 카테고리·지역의 표시 이름.
 *
 * 값 자체는 한국어다 — DB 에 그렇게 들어 있고 API 가 그 문자열로 거른다. 바꾸면
 * 마이그레이션과 API 변경이 따라오므로 **표시만** 번역한다. 사전에 없는 값이 오면
 * (공사 데이터에 새 분류가 생기는 경우) 한국어가 그대로 보인다 — 키 문자열이 보이는
 * (`category.해장국`) 것보다 낫다.
 */
export const categoryLabel = (t: TFn, value: string) => {
  const out = t(`category.${value}`);
  return out === `category.${value}` ? value : out;
};
export const regionLabel = (t: TFn, value: string) => {
  const out = t(`region.${value}`);
  return out === `region.${value}` ? value : out;
};
