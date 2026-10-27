// 앱 다국어 검사. 의존성이 없어야 한다 — package.json 이 바뀌면 OTA 가 끊긴다.
//
// 세 가지를 본다.
//   1. 네 사전의 키 집합이 같은지
//   2. translate() 가 키 없음·변수 없음 같은 경우에 어떻게 떨어지는지
//   3. 이미 번역한 화면에 한국어 리터럴이 남아 있지 않은지 (+ 어느 쪽 목록에도 없는 새 파일이 없는지)
//   4. 서버가 저장하는 한국어 값마다 사전 키가 있는지
//   5. 라벨 함수가 사전에 없는 값에 한국어 원문을 돌려주는지
//   (4b. 서버 FeedReason 코드마다 feed.reason.* 키가 있는지)
//
// 2번은 index.ts 에서 함수 본문을 떼어내 타입 표기만 지우고 실제로 돌린다.
// 베껴 쓰면 구현과 어긋나므로 그렇게 하지 않는다.
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const I18N = join(ROOT, 'src/lib/i18n');

let failed = 0;
const ok = (label, cond, detail = '') => {
  if (!cond) failed++;
  console.log(`${cond ? 'OK  ' : 'FAIL'} ${label}${cond || !detail ? '' : '  ' + detail}`);
};

// ── 1. 키 집합 ──────────────────────────────────────────────
// 사전은 "'키': '값'," 한 줄 꼴로만 쓴다. 그래야 여기서 읽을 수 있다.
function keysOf(locale) {
  const src = readFileSync(join(I18N, `${locale}.ts`), 'utf8');
  return new Set([...src.matchAll(/^\s*'([^']+)':/gm)].map((m) => m[1]));
}
const LOCALES = ['ko', 'en', 'ja', 'zh'];
const keys = Object.fromEntries(LOCALES.map((l) => [l, keysOf(l)]));
ok('ko 사전이 비어 있지 않다', keys.ko.size > 0, `${keys.ko.size}개`);
for (const l of LOCALES.filter((l) => l !== 'ko')) {
  const missing = [...keys.ko].filter((k) => !keys[l].has(k));
  const extra = [...keys[l]].filter((k) => !keys.ko.has(k));
  ok(`${l} 사전에 빠진 키가 없다`, missing.length === 0, missing.slice(0, 5).join(', '));
  ok(`${l} 사전에 군더더기 키가 없다`, extra.length === 0, extra.slice(0, 5).join(', '));
}

// ── 2. translate() ──────────────────────────────────────────
// index.ts 의 본문을 떼어내 타입 표기만 지운다. 못 찾으면 바로 멈춘다
// (조용히 안 돌면 검사한 척만 하게 된다).
const idx = readFileSync(join(I18N, 'index.ts'), 'utf8');
const SIG = 'export function translate(locale: Locale, key: string, vars?: Record<string, unknown>): string {';
if (!idx.includes(SIG)) {
  console.log('FAIL translate() 서명을 찾지 못했다 — 스크립트의 SIG 를 함께 고칠 것');
  process.exit(1);
}
const body = idx.slice(idx.indexOf(SIG)).replace(SIG, 'function translate(locale, key, vars) {');
const end = body.indexOf('\n}\n');
const fn = new Function('DICT', body.slice(0, end + 3) + '\nreturn translate;');

const DICT = {
  ko: { greet: '안녕 {name}', only: '한국어에만', count: '{n}곳' },
  en: { greet: 'Hi {name}' },
  ja: {}, zh: {},
};
const t = fn(DICT);
ok('치환이 된다', t('en', 'greet', { name: 'Ann' }) === 'Hi Ann');
ok('키가 없으면 한국어로 떨어진다', t('en', 'only') === '한국어에만');
ok('한국어에도 없으면 키를 그대로 준다', t('en', 'nope.key') === 'nope.key');
ok('변수를 안 넘기면 빈 문자열로 치운다', t('en', 'greet') === 'Hi ');
ok('모르는 변수도 빈 문자열', t('ko', 'greet', { other: 1 }) === '안녕 ');
ok('0 같은 falsy 값도 그대로 치환한다', t('ko', 'count', { n: 0 }) === '0곳');
ok('모르는 언어는 한국어로 떨어진다', t('xx', 'greet', { name: 'Ann' }) === '안녕 Ann');

// ── 2b. normalizeLocale() ───────────────────────────────────
const dev = readFileSync(join(I18N, 'device.ts'), 'utf8');
const NSIG = 'export function normalizeLocale(raw: string | null | undefined): Locale | null {';
if (!dev.includes(NSIG)) {
  console.log('FAIL normalizeLocale() 서명을 찾지 못했다 — 스크립트의 NSIG 를 함께 고칠 것');
  process.exit(1);
}
const nBody = dev.slice(dev.indexOf(NSIG)).replace(NSIG, 'function normalizeLocale(raw) {');
const nEnd = nBody.indexOf('\n}\n');
// LOCALES 는 import 라 본문에 없다 — 넣어 준다. 본문에 타입 캐스트(`as …`)를 쓰면
// node 가 읽지 못하므로 normalizeLocale 은 캐스트 없이 쓴다.
const normalize = new Function('LOCALES', nBody.slice(0, nEnd + 3) + '\nreturn normalizeLocale;')(
  ['ko', 'en', 'ja', 'zh'],
);

ok('ko-KR → ko', normalize('ko-KR') === 'ko');
ok('en-US → en', normalize('en-US') === 'en');
ok('ja_JP → ja (밑줄도 받는다)', normalize('ja_JP') === 'ja');
ok('zh-Hans-CN → zh', normalize('zh-Hans-CN') === 'zh');
ok('zh-TW 도 zh (번체는 따로 두지 않는다)', normalize('zh-TW') === 'zh');
ok('대문자도 받는다', normalize('EN') === 'en');
ok('모르는 언어는 null', normalize('de-DE') === null);
ok('빈 문자열은 null', normalize('') === null);
ok('null 은 null', normalize(null) === null);
ok('undefined 는 null', normalize(undefined) === null);

// ── 2c. errorTextIn() ───────────────────────────────────────
// errorText() 는 스토어에서 언어를 꺼내므로 node 에서 돌릴 수 없다. 판단은 순수 함수인
// errorTextIn() 에 있고 errorText() 는 얇은 껍데기다 — 돌려 보는 쪽은 알맹이다.
const errSrc = readFileSync(join(I18N, 'errors.ts'), 'utf8');
const ESIG = 'export function errorTextIn(locale: Locale, err: ApiErrorBody): string {';
if (!errSrc.includes(ESIG)) {
  console.log('FAIL errorTextIn() 서명을 찾지 못했다 — 스크립트의 ESIG 를 함께 고칠 것');
  process.exit(1);
}
const eBody = errSrc.slice(errSrc.indexOf(ESIG)).replace(ESIG, 'function errorTextIn(locale, err) {');
const eEnd = eBody.indexOf('\n}\n');
// BY_CODE 와 translate 는 이 함수 밖에 있다 — 넣어 준다.
const errorTextIn = new Function(
  'BY_CODE', 'translate',
  eBody.slice(0, eEnd + 3) + '\nreturn errorTextIn;',
)(
  { ITINERARY_NOT_FOUND: 'error.itinerary.notFound' },
  (loc, key) => (key === 'error.itinerary.notFound' ? '없는 루트' : key === 'common.error.unknown' ? '알 수 없음' : key),
);

ok('아는 코드는 사전으로 번역한다',
   errorTextIn('ko', { code: 'ITINERARY_NOT_FOUND', message: '여행 일지를 찾을 수 없습니다.' }) === '없는 루트');
ok('모르는 코드는 서버 message 를 그대로 띄운다',
   errorTextIn('ko', { code: 'SOMETHING_NEW', message: '서버가 준 말' }) === '서버가 준 말');
ok('코드도 message 도 없으면 common.error.unknown',
   errorTextIn('ko', null) === '알 수 없음');
ok('message 만 있고 code 가 없으면 message',
   errorTextIn('ko', { message: '그냥 메시지' }) === '그냥 메시지');
ok('아는 코드는 message 보다 사전이 이긴다',
   errorTextIn('ko', { code: 'ITINERARY_NOT_FOUND', message: '한국어' }) === '없는 루트');

// BY_CODE 의 값이 ko 사전에 실제로 있는 키인지. 오타가 나면 사용자에게 키 문자열이
// 그대로(`error.itinerary.notFoundd`) 보이는데, 아무도 눈치채지 못한다.
const byCodeSrc = errSrc.slice(errSrc.indexOf('const BY_CODE'));
const byCodeBody = byCodeSrc.slice(0, byCodeSrc.indexOf('};'));
const byCodeVals = [...byCodeBody.matchAll(/^\s*'[^']+':\s*'([^']+)'/gm)].map((m) => m[1]);
ok('BY_CODE 를 읽었다', byCodeVals.length > 0, `${byCodeVals.length}개`);
const dangling = byCodeVals.filter((k) => !keys.ko.has(k));
ok('BY_CODE 의 값이 모두 ko 사전에 있다', dangling.length === 0, dangling.join(', '));

// ── 3. 번역을 마친 화면에 한국어가 남아 있지 않은지 ───────────
// 화면을 옮길 때마다 DONE 에 더한다. DONE 에도 EXCLUDED 에도 없는 파일은 아래에서 실패한다
// (새 한국어 화면이 조용히 통과하던 구멍을 막는다). 제외는 이유를 한 줄 적어야 한다.
// 일부러 DONE 에 넣지 않는 파일. 빠뜨린 게 아니니 "고치려" 들지 말 것.
const EXCLUDED = {
  'src/app/language.tsx': '언어 고르기 화면. 지금 언어를 못 읽는 사람이 쓰는 곳이라 일부러 번역하지 않는다(각 언어의 자기 이름과 영어만 쓴다).',
  'src/app/+html.tsx': '빌드 시점에 한 번 만드는 정적 <head> — 훅이 돌지 않아 번역할 수 없다. 메타데이터는 일부러 영어로 고정했다.',
  'src/lib/api/ai.ts': 'AI_THEMES 는 서버가 저장·필터하는 한국어 **값**이다. 아래 4번이 사전 키를 검사한다.',
  'src/lib/api/itineraries.ts': "'맛집'/'카페' 를 카카오 분류 코드로 바꾸려고 서버 값과 비교한다. 표시 문구가 아니다.",
};
// 4번이 값 목록을 검사하는 모듈(categories·regions·interestOptions·venueForm·aiRouteAreas·reportReasons)은
// src/lib 아래라 열거 대상이 아니다. 열거 대상은 src/app, src/components, src/lib/api 다.
const DONE = [
  'src/app/(tabs)/index.tsx',
  'src/app/(tabs)/map.tsx',
  'src/app/(tabs)/explore.tsx',
  'src/app/(tabs)/routes.tsx',
  'src/app/(tabs)/profile.tsx',
  'src/components/TabBar.tsx',
  'src/components/LoginRequired.tsx',
  'src/app/(tabs)/_layout.tsx',
  'src/app/ai-route.tsx',
  'src/app/itinerary/[id].tsx',
  'src/app/itinerary/index.tsx',
  'src/app/route/[id].tsx',
  'src/app/s/[token].tsx',
  'src/components/DraggableStops.tsx',
  'src/components/StopEditSheet.tsx',
  'src/components/Calendar.tsx',
  'src/components/PlacePicker.tsx',
  'src/app/login.tsx',
  'src/app/email-login.tsx',
  'src/app/onboarding.tsx',
  'src/lib/auth/oauth.ts',
  'src/app/venue/[id].tsx',
  'src/app/venue/register.tsx',
  'src/app/performances/[id].tsx',
  'src/app/performances/traditional.tsx',
  'src/app/course/[id].tsx',
  'src/app/review/write.tsx',
  'src/components/VenueCard.tsx',
  'src/components/VenueRow.tsx',
  'src/components/WebLanguageSwitcher.tsx',
  'src/components/Rating.tsx',
  'src/components/ContentComments.tsx',
  'src/components/ReportDialog.tsx',
  'src/components/PerformanceCarousel.tsx',
  'src/components/FeedCarousel.tsx',
  'src/components/SectionHeader.tsx',
  'src/components/Skeleton.tsx',
  'src/app/_layout.tsx',
  'src/app/bookmarks.tsx',
  'src/app/my/accounts.tsx',
  'src/app/my/blocked.tsx',
  'src/app/my/language.tsx',
  'src/app/my/profile.tsx',
  'src/app/my/reviews.tsx',
  'src/app/my/service-info.tsx',
  'src/app/my/venues.tsx',
  'src/app/search.tsx',
  'src/components/BottomSheet.tsx',
  'src/components/Chip.tsx',
  'src/components/HeaderRight.tsx',
  'src/components/Image.tsx',
  'src/components/KakaoMapView.tsx',
  'src/components/KakaoMapView.web.tsx',
  'src/components/Refresh.tsx',
  'src/components/TimeWheel.tsx',
  'src/components/CountLine.tsx',
  'src/lib/alert.ts',
  'src/lib/api/uploads.ts',
  'src/lib/api/account.ts',
  'src/lib/api/client.ts',
  'src/lib/api/courses.ts',
  'src/lib/api/feed.ts',
  'src/lib/api/interests.ts',
  'src/lib/api/performances.ts',
  'src/lib/api/reviews.ts',
  'src/lib/api/social.ts',
  'src/lib/api/tags.ts',
  'src/lib/api/types.ts',
  'src/lib/api/venues.ts',
  'src/lib/api/wishlists.ts',
  'src/lib/map/kakaoMapSource.ts',
  'src/lib/map/useMapLabels.ts',
];
const HANGUL_LITERAL = /(['"`])[^'"`\n]*[가-힣][^'"`\n]*\1/g;
for (const rel of DONE) {
  const src = readFileSync(join(ROOT, rel), 'utf8');
  // 주석 줄은 한국어로 쓴다 — 번역 대상이 아니다.
  // 여러 줄 /* … */ 블록(JSX 의 {/* … */} 포함)은 줄머리가 `*` 가 아닌 안쪽 줄까지 통째로 지운다.
  const code = src.replace(/(^|[\s{(=,:])\/\*[\s\S]*?\*\//g, '$1').split('\n').filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l)).join('\n');
  const hits = [...code.matchAll(HANGUL_LITERAL)].map((m) => m[0]);
  ok(`${rel} 에 한국어 리터럴이 없다`, hits.length === 0, hits.slice(0, 3).join(' | '));
}

// ── 3b. 목록에 없는 파일 ────────────────────────────────────
// 반대 방향 검사. 열거한 파일이 DONE 에도 EXCLUDED 에도 없으면 실패한다.
function walk(dir) {
  return readdirSync(join(ROOT, dir), { withFileTypes: true }).flatMap((e) => {
    const rel = `${dir}/${e.name}`;
    if (e.isDirectory()) return walk(rel);
    return /\.tsx?$/.test(e.name) ? [rel] : [];
  });
}
const listed = new Set([...DONE, ...Object.keys(EXCLUDED)]);
const onDisk = ['src/app', 'src/components', 'src/lib/api'].flatMap(walk);
const unlisted = onDisk.filter((f) => !listed.has(f));
ok('src/app·components·lib/api 의 모든 파일이 DONE 이나 EXCLUDED 에 있다', unlisted.length === 0, unlisted.join(', '));
const gone = [...listed].filter((f) => !onDisk.includes(f) && !existsSync(join(ROOT, f)));
ok('DONE·EXCLUDED 의 파일이 모두 실제로 있다', gone.length === 0, gone.join(', '));
const both = Object.keys(EXCLUDED).filter((f) => DONE.includes(f));
ok('DONE 과 EXCLUDED 에 동시에 있는 파일이 없다', both.length === 0, both.join(', '));
ok('EXCLUDED 마다 이유가 적혀 있다', Object.values(EXCLUDED).every((r) => r.trim().length > 0));

// ── 4. 서버가 저장하는 한국어 값에 사전 키가 있는지 ───────────
// labels.ts 는 키가 없으면 한국어 원문으로 떨어지도록 만들었다(의도). 그래서 키를 빠뜨려도
// 아무도 모른다 — 값마다 키가 ko 사전에 있는지 여기서 본다.
// 일곱 모듈을 한데 합치지 않는다. 각자 있는 자리에서 읽는다.
const quoted = (txt) => [...txt.matchAll(/'([^']*[가-힣][^']*)'/g)].map((m) => m[1]);
function listOf(rel, name) {
  const src = readFileSync(join(ROOT, rel), 'utf8');
  const at = src.indexOf(`export const ${name} =`);
  if (at < 0) { console.log(`FAIL ${rel} 에서 ${name} 을 찾지 못했다 — 스크립트도 함께 고칠 것`); process.exit(1); }
  const open = src.indexOf('[', at);
  const close = src.indexOf(']', open);
  return src.slice(open, close);
}
const VALUE_LISTS = [
  // [파일, 이름, 키 접두사, 뽑는 방식]
  ['src/lib/categories.ts', 'CATEGORIES', 'category.'],
  ['src/lib/categories.ts', 'PICKER_CATEGORIES', 'category.'],
  ['src/lib/categories.ts', 'HOME_KEYWORDS', 'category.'],
  ['src/lib/regions.ts', 'REGIONS', 'region.'],
  ['src/lib/interestOptions.ts', 'TOPICS', 'onboarding.topic.'],
  ['src/lib/interestOptions.ts', 'REGIONS', 'onboarding.region.'],
  ['src/lib/venueForm.ts', 'VENUE_CATEGORIES', 'category.'],
  ['src/lib/venueForm.ts', 'VENUE_REGIONS', 'region.'],
  ['src/lib/venueForm.ts', 'VENUE_DAYS', 'venueRegister.day.'],
  ['src/lib/aiRouteAreas.ts', 'AREAS', 'aiRoute.area.', /name:\s*'([^']+)'/g],
  ['src/lib/aiRouteAreas.ts', 'DEFAULT_THEMES', 'category.'],
  ['src/lib/api/ai.ts', 'AI_THEMES', 'category.'],
];
for (const [rel, name, prefix, pick] of VALUE_LISTS) {
  const body = listOf(rel, name);
  const vals = pick ? [...body.matchAll(pick)].map((m) => m[1]) : quoted(body);
  const missing = vals.filter((v) => !keys.ko.has(prefix + v));
  ok(`${rel} ${name} 의 값 ${vals.length}개가 ko 사전에 ${prefix}* 키로 있다`, vals.length > 0 && missing.length === 0,
     vals.length === 0 ? '값을 하나도 못 읽었다' : missing.map((v) => prefix + v).join(', '));
}
// REPORT_REASONS 는 키를 데이터에 같이 들고 있다 — 그 키가 사전에 있는지.
{
  const body = listOf('src/lib/reportReasons.ts', 'REPORT_REASONS');
  const ks = [...body.matchAll(/key:\s*'([^']+)'/g)].map((m) => m[1]);
  const missing = ks.filter((k) => !keys.ko.has(k));
  ok(`src/lib/reportReasons.ts REPORT_REASONS 의 키 ${ks.length}개가 ko 사전에 있다`, ks.length > 0 && missing.length === 0, missing.join(', '));
}
ok('aiRoute.myLocation 키가 ko 사전에 있다', keys.ko.has('aiRoute.myLocation'));

// 코스 테마·댓글 태그는 우리 데이터라 상수로 둔다. 서버(시드 데이터)가 저장하는 값과 같아야 한다.
// 행사 장르는 외부(KOPIS) 데이터라 목록을 박아 두지 않는다 — ko 의 genre.* 키가 다른 언어에도
// 있는지는 위 1번의 키 집합 검사가 이미 본다.
const THEMES = ['공예·한옥', '한복나들이', '한옥나들이', '한옥마을', '궁궐산책', '근대골목', '세계유산', '전통시장', '역사탐방', '미식투어'];
const TAGS = ['분위기 좋음', '야경 최고', '재방문 의사', '인생샷 명소', '가성비 좋음'];
{
  const missT = THEMES.filter((v) => !keys.ko.has('theme.' + v));
  ok(`코스 테마 ${THEMES.length}개가 ko 사전에 theme.* 키로 있다`, missT.length === 0, missT.join(', '));
  const missG = TAGS.filter((v) => !keys.ko.has('tag.' + v));
  ok(`댓글 태그 ${TAGS.length}개가 ko 사전에 tag.* 키로 있다`, missG.length === 0, missG.join(', '));
  const genres = [...keys.ko].filter((k) => k.startsWith('genre.'));
  ok('ko 사전에 genre.* 키가 있다', genres.length > 0, `${genres.length}개`);
}

// ── 4b. 피드 이유 코드 ──────────────────────────────────────
// 우리 서버가 만드는 값이라 목록을 서버 소스에서 직접 읽는다. 서버가 코드를 더했는데 사전을
// 안 더하면 사용자에게 'feed.reason.XXX' 키 문자열이 그대로 보인다. CATEGORY·GENRE 는 일부러
// 사전 키가 없다 — 분류·장르 사전(category.*, genre.*)을 거쳐 번역한다.
{
  const FEED_REASON = join(ROOT, '../api/src/main/java/com/byeori/domain/feed/FeedReason.java');
  if (!existsSync(FEED_REASON)) {
    console.log('FAIL FeedReason.java 를 찾지 못했다 — 스크립트의 경로를 함께 고칠 것');
    process.exit(1);
  }
  const codes = [...new Set([...readFileSync(FEED_REASON, 'utf8').matchAll(/new FeedReason\("([A-Z_]+)"/g)].map((m) => m[1]))];
  const ROUTED = ['CATEGORY', 'GENRE'];
  ok(`FeedReason 코드를 읽었다`, codes.length >= 8, `${codes.length}개`);
  const need = codes.filter((c) => !ROUTED.includes(c));
  for (const l of LOCALES) {
    const miss = need.filter((c) => !keys[l].has('feed.reason.' + c));
    ok(`${l} 사전에 FeedReason 코드 ${need.length}개의 feed.reason.* 키가 있다`, miss.length === 0, miss.join(', '));
  }
  const stale = [...keys.ko].filter((k) => k.startsWith('feed.reason.') && !codes.includes(k.slice('feed.reason.'.length)));
  ok('서버가 더는 내지 않는 feed.reason.* 키가 없다', stale.length === 0, stale.join(', '));
  const routedMissing = ROUTED.filter((c) => !codes.includes(c));
  ok('제외한 CATEGORY·GENRE 가 실제로 서버에 있는 코드다', routedMissing.length === 0, routedMissing.join(', '));
}

// ── 5. passthrough ──────────────────────────────────────────
// 라벨 함수는 사전에 없는 값에 키 문자열이 아니라 한국어 원문을 돌려줘야 한다. 본문을 떼어내 돌린다.
const lab = readFileSync(join(I18N, 'labels.ts'), 'utf8');
const PSIG = 'const passthrough = (t: TFn, prefix: string, value: string) => {';
if (!lab.includes(PSIG)) {
  console.log('FAIL passthrough 서명을 찾지 못했다 — 스크립트의 PSIG 를 함께 고칠 것');
  process.exit(1);
}
const pBody = lab.slice(lab.indexOf(PSIG)).replace(PSIG, 'const passthrough = (t, prefix, value) => {');
const passthrough = new Function(pBody.slice(0, pBody.indexOf('\n};\n') + 4) + '\nreturn passthrough;')();
const tf = (k) => ({ 'genre.연극': 'Theater' })[k] ?? k;
ok('passthrough: 있는 값은 번역을 준다', passthrough(tf, 'genre', '연극') === 'Theater');
ok('passthrough: 없는 값은 한국어 원문을 준다(키가 아니다)', passthrough(tf, 'genre', '새로운것') === '새로운것');
for (const fnName of ['categoryLabel', 'regionLabel', 'kakaoCategoryLabel', 'areaLabel', 'genreLabel', 'themeLabel', 'tagLabel']) {
  const m = lab.match(new RegExp(`export const ${fnName} =[\\s\\S]*?;\\n(?=export|/\\*\\*|$)`));
  ok(`${fnName} 이 passthrough 를 쓴다`, !!m && m[0].includes('passthrough('));
}

console.log(failed ? `\n${failed}건 실패` : '\n전부 통과');
process.exit(failed ? 1 : 0);
