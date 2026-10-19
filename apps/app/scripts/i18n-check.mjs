// 앱 다국어 검사. 의존성이 없어야 한다 — package.json 이 바뀌면 OTA 가 끊긴다.
//
// 세 가지를 본다.
//   1. 네 사전의 키 집합이 같은지
//   2. translate() 가 키 없음·변수 없음 같은 경우에 어떻게 떨어지는지
//   3. 이미 번역한 화면에 한국어 리터럴이 남아 있지 않은지
//
// 2번은 index.ts 에서 함수 본문을 떼어내 타입 표기만 지우고 실제로 돌린다.
// 베껴 쓰면 구현과 어긋나므로 그렇게 하지 않는다.
import { readFileSync } from 'node:fs';
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
// 화면을 옮길 때마다 아래 목록에 더한다. 목록에 없는 파일은 보지 않는다.
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
  'src/components/Rating.tsx',
  'src/components/ContentComments.tsx',
  'src/components/ReportDialog.tsx',
  'src/components/PerformanceCarousel.tsx',
  'src/components/FeedCarousel.tsx',
  'src/components/SectionHeader.tsx',
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

console.log(failed ? `\n${failed}건 실패` : '\n전부 통과');
process.exit(failed ? 1 : 0);
