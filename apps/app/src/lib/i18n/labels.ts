export type TFn = (key: string, vars?: Record<string, unknown>) => string;

/**
 * 서버가 한국어 문자열로 주는 분류값의 표시 이름을 찾는 공통 본문.
 *
 * 사전에 없으면 키가 아니라 **한국어 값을 그대로** 돌려준다 — 외부 데이터(공사·KOPIS·카카오)에
 * 새 값이 생겨도 `genre.새것` 같은 키 문자열이 화면에 뜨는 것보다 한국어가 낫다. 라벨 함수마다
 * 이 본문을 따로 베끼면 한 곳만 고치고 나머지를 놓치므로 전부 여기를 거친다.
 */
const passthrough = (t: TFn, prefix: string, value: string) => {
  const key = `${prefix}.${value}`;
  const out = t(key);
  return out === key ? value : out;
};


/**
 * 카테고리·지역의 표시 이름.
 *
 * 값 자체는 한국어다 — DB 에 그렇게 들어 있고 API 가 그 문자열로 거른다. 바꾸면
 * 마이그레이션과 API 변경이 따라오므로 **표시만** 번역한다. 사전에 없는 값이 오면
 * (공사 데이터에 새 분류가 생기는 경우) 한국어가 그대로 보인다 — 키 문자열이 보이는
 * (`category.해장국`) 것보다 낫다.
 */
export const categoryLabel = (t: TFn, value: string) => passthrough(t, 'category', value);
/**
 * 카카오 장소의 category_group_name 표시 이름. 카카오가 정한 닫힌 집합(18개)이라 사전을 따로 둔다
 * (벼리 분류인 category.* 와 겹치는 건 카페뿐이다). 모르는 값은 키가 아니라 **원문 그대로** 보인다 —
 * 카카오가 분류를 늘려도 `kakaoCategory.무언가` 가 화면에 뜨는 것보다 한국어가 낫다.
 */
export const kakaoCategoryLabel = (t: TFn, value: string) => passthrough(t, 'kakaoCategory', value);
/**
 * 행사 장르·코스 테마·댓글 태그의 표시 이름. 값은 서버가 주는 한국어이고 비교에만 쓴다.
 * 장르는 외부 데이터(KOPIS)라 언제든 새 값이 생기는데, 그때는 한국어 원문이 보인다.
 */
export const genreLabel = (t: TFn, value: string) => passthrough(t, 'genre', value);
export const themeLabel = (t: TFn, value: string) => passthrough(t, 'theme', value);
export const tagLabel = (t: TFn, value: string) => passthrough(t, 'tag', value);
/** AI 코스 지역칩의 표시 이름. 검색으로 고른 곳은 사전에 없어 이름 그대로 보인다. */
export const areaLabel = (t: TFn, a: { name: string; my?: boolean }) => {
  if (a.my) return t('aiRoute.myLocation');
  return passthrough(t, 'aiRoute.area', a.name);
};
/**
 * 언어 설정을 가리키는 이름. 현재 언어의 '언어' 에 영어 'Language' 를 붙여 둔다 — 지금 언어를
 * 읽지 못하는 사람도 라틴 글자로 이 행·화면을 알아본다. 영어는 이미 Language 라 중복을 피한다.
 */
export const languageLabel = (t: TFn) => {
  const own = t('language.title');
  return own === 'Language' ? own : `${own} / Language`;
};
export const regionLabel = (t: TFn, value: string) => passthrough(t, 'region', value);
