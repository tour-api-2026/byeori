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
/**
 * 카카오 장소의 category_group_name 표시 이름. 카카오가 정한 닫힌 집합(18개)이라 사전을 따로 둔다
 * (벼리 분류인 category.* 와 겹치는 건 카페뿐이다). 모르는 값은 키가 아니라 **원문 그대로** 보인다 —
 * 카카오가 분류를 늘려도 `kakaoCategory.무언가` 가 화면에 뜨는 것보다 한국어가 낫다.
 */
export const kakaoCategoryLabel = (t: TFn, value: string) => {
  const out = t(`kakaoCategory.${value}`);
  return out === `kakaoCategory.${value}` ? value : out;
};
export const regionLabel = (t: TFn, value: string) => {
  const out = t(`region.${value}`);
  return out === `region.${value}` ? value : out;
};
