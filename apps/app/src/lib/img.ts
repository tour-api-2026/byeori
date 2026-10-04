// 안드로이드 릴리스 빌드는 평문 HTTP를 차단한다(usesCleartextTraffic 기본값 false).
// 공사 이미지 주소는 http와 https가 섞여 내려오므로, 그대로 넘기면 http 건만 앱에서 안 보인다.
// 브라우저는 혼합 콘텐츠 이미지를 알아서 https로 올려 주기 때문에 웹에서는 드러나지 않는다.
//
// 로컬 개발 서버(localhost·루프백·사설망)는 https를 제공하지 않으므로 건드리지 않는다.
// 이쪽은 평문이어도 안드로이드가 막지 않는다(디버그 빌드 한정).
const LOCAL_HOST = /^(localhost|127\.\d+\.\d+\.\d+|10\.\d+\.\d+\.\d+|192\.168\.\d+\.\d+|172\.(1[6-9]|2\d|3[01])\.\d+\.\d+)$/;

/** 원격 이미지 주소를 https로 올린다. 로컬 주소와 http가 아닌 주소는 그대로 둔다. */
export function secure(url: string | null | undefined): string | undefined {
  if (!url) return undefined;
  if (!url.startsWith('http://')) return url;
  const host = url.slice('http://'.length).split(/[/:?#]/, 1)[0];
  if (LOCAL_HOST.test(host)) return url;
  return 'https://' + url.slice('http://'.length);
}

// 외부 placeholder 이미지(picsum 등)를 표시 크기에 맞게 줄여 요청한다.
// 큰 원본(640x420 ~40KB)을 카드 크기로 받으면 전송량·디코드가 줄어 로딩이 빨라진다.
export function sized(url: string | null | undefined, w: number, h: number): string | undefined {
  if (!url) return undefined;
  // https://picsum.photos/seed/<seed>/<W>/<H>  또는  /id/<num>/<W>/<H> → 크기만 교체
  const m = url.match(/^(https?:\/\/picsum\.photos\/(?:seed\/[^/]+|id\/\d+))\/\d+\/\d+(.*)$/);
  if (m) return `${m[1]}/${Math.round(w)}/${Math.round(h)}${m[2]}`;
  return url;
}
