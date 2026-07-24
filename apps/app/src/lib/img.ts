// 외부 placeholder 이미지(picsum 등)를 표시 크기에 맞게 줄여 요청한다.
// 큰 원본(640x420 ~40KB)을 카드 크기로 받으면 전송량·디코드가 줄어 로딩이 빨라진다.
export function sized(url: string | null | undefined, w: number, h: number): string | undefined {
  if (!url) return undefined;
  // https://picsum.photos/seed/<seed>/<W>/<H>  또는  /id/<num>/<W>/<H> → 크기만 교체
  const m = url.match(/^(https?:\/\/picsum\.photos\/(?:seed\/[^/]+|id\/\d+))\/\d+\/\d+(.*)$/);
  if (m) return `${m[1]}/${Math.round(w)}/${Math.round(h)}${m[2]}`;
  return url;
}
