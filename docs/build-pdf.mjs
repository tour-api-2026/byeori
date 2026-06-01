// 벼리 기술 설계 문서 — Markdown → HTML → (headless Chromium) PDF 빌드
// 사용법: node docs/build-pdf.mjs
import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { marked } from 'marked';

const here = dirname(fileURLToPath(import.meta.url));
const MD = join(here, 'byeori-tech-spec.md');
const CSS = join(here, 'assets', 'style.css');
const HTML = join(here, '.build.html');
const PDF = join(here, 'byeori-tech-spec.pdf');

// NotoSansKR TTF (로컬 expo-google-fonts) — base64 임베드로 한글 렌더 보장
const FONT_DIR = '/home/hidi/dev/git-grass/git-grass-widget/node_modules/@expo-google-fonts/noto-sans-kr';
const FONTS = [
  { file: `${FONT_DIR}/400Regular/NotoSansKR_400Regular.ttf`, weight: 400 },
  { file: `${FONT_DIR}/700Bold/NotoSansKR_700Bold.ttf`, weight: 700 },
];

// Chromium 바이너리 후보 (Playwright 캐시)
const CHROME_CANDIDATES = [
  '/home/hidi/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome',
  '/home/hidi/.cache/ms-playwright/chromium-1208/chrome-linux64/chrome',
];
const chrome = CHROME_CANDIDATES.find(existsSync);
if (!chrome) { console.error('Chromium 바이너리를 찾지 못했습니다.'); process.exit(1); }

// 1) @font-face (base64)
const fontFaces = FONTS.map(({ file, weight }) => {
  const b64 = readFileSync(file).toString('base64');
  return `@font-face{font-family:'Noto Sans KR';font-style:normal;font-weight:${weight};`
       + `src:url(data:font/ttf;base64,${b64}) format('truetype');}`;
}).join('\n');

// 2) Markdown → HTML
marked.setOptions({ gfm: true, breaks: false });
const body = marked.parse(readFileSync(MD, 'utf8'));
const css = readFileSync(CSS, 'utf8');

const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<style>${fontFaces}</style>
<style>${css}</style>
</head><body>${body}</body></html>`;
writeFileSync(HTML, html);

// 3) headless Chromium 인쇄 (@page CSS가 A4/여백 제어)
const r = spawnSync(chrome, [
  '--headless=new', '--disable-gpu', '--no-sandbox',
  '--no-pdf-header-footer',
  `--print-to-pdf=${PDF}`,
  `file://${HTML}`,
], { stdio: 'inherit', timeout: 120000 });

if (r.status !== 0) { console.error('Chromium 인쇄 실패:', r.status, r.error); process.exit(1); }
console.log('생성 완료 →', PDF);
