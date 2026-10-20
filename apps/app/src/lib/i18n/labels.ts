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
/**
 * 추천이 뜬 이유를 문장으로.
 *
 * 서버는 문장이 아니라 코드와 값을 준다(FeedReason). CATEGORY·GENRE 는 값 자체가 문구다 —
 * 이미 가진 분류값 사전을 태우고, 따로 키를 두지 않는다(같은 것의 번역이 둘이 되므로).
 *
 * 구버전 서버가 올 수 있다: 앱은 OTA 로 먼저 나가므로, 서버가 아직 안 올라간 동안
 * reason 에 "관심 주제 · 문화" 같은 **완성된 한국어 문장**이 오고 reasonParam 은 없다.
 * 그걸 코드로 보고 사전을 찾으면 못 찾고, 값도 없어 빈 배지가 된다. 그래서 코드 꼴
 * (대문자·밑줄)이 아닌 reason 은 이미 문장이라고 보고 그대로 보여 준다.
 * 새 서버의 모르는 코드(앱보다 서버가 먼저 새 코드를 낸 경우)는 값이 있으면 값을, 없으면
 * 빈 문자열을 준다 — 코드 이름이 화면에 뜨는 것만은 막는다.
 */
export function feedReasonText(t: TFn, code: string, param?: string | null): string {
  if (code === 'CATEGORY') return param ? categoryLabel(t, param) : '';
  if (code === 'GENRE') return param ? genreLabel(t, param) : '';
  // 코드 꼴이 아니면 구버전 서버의 완성된 문장이거나 분류·장르 한 단어다. 분류값 사전을 한 번
  // 태운다 — 문장("관심 주제 · 문화")은 어느 키와도 안 맞아 그대로 나온다.
  if (!/^[A-Z][A-Z0-9_]*$/.test(code)) {
    const asCategory = categoryLabel(t, code);
    return asCategory !== code ? asCategory : genreLabel(t, code);
  }
  // FAVORITE_TOPIC 의 값은 분류 또는 장르 — 분류 사전에 없으면 장르 사전을 본다.
  let p = param ?? '';
  if (code === 'FAVORITE_TOPIC' && p) {
    const asCategory = categoryLabel(t, p);
    p = asCategory !== p ? asCategory : genreLabel(t, p);
  }
  const key = `feed.reason.${code}`;
  const out = t(key, { p });
  return out === key ? p : out;
}
